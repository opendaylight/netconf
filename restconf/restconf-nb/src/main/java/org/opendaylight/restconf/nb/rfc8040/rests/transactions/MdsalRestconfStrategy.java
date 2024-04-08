/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FieldsParam.NodeSelector;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.ParameterAwareNormalizedNodeWriter;
import org.opendaylight.restconf.nb.rfc8040.legacy.WriterParameters;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Implementation of RESTCONF operations using {@link DOMTransactionChain} and related concepts.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public final class MdsalRestconfStrategy extends RestconfStrategy {
    private final DOMDataBroker dataBroker;

    public MdsalRestconfStrategy(final DatabindContext databind, final DOMDataBroker dataBroker,
            final ImmutableMap<QName, RpcImplementation> localRpcs, final @Nullable DOMRpcService rpcService,
            final @Nullable DOMActionService actionService, final @Nullable YangTextSourceExtension sourceProvider,
            final @Nullable DOMMountPointService mountPointService) {
        super(databind, localRpcs, rpcService, actionService, sourceProvider, mountPointService);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(modelContext(), dataBroker);
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final YangInstanceIdentifier path) {
        final var tx = dataBroker.newReadWriteTransaction();
        tx.exists(CONFIGURATION, path).addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (!result) {
                    cancelTx(new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING, path));
                    return;
                }

                tx.delete(CONFIGURATION, path);
                tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                    @Override
                    public void onSuccess(final CommitInfo result) {
                        future.set(Empty.value());
                    }

                    @Override
                    public void onFailure(final Throwable cause) {
                        future.setFailure(new RestconfDocumentedException("Transaction to delete " + path + " failed",
                            cause));
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancelTx(new RestconfDocumentedException("Failed to access " + path, cause));
            }

            private void cancelTx(final RestconfDocumentedException ex) {
                tx.cancel();
                future.setFailure(ex);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    RestconfFuture<DataGetResult> dataGET(final Data path, final DataGetParams params) {
        final var inference = path.inference();
        final var fields = params.fields();
        return completeDataGET(inference,
            fields == null ? WriterParameters.of(params.prettyPrint(), params.depth())
                : new WriterParameters(params.prettyPrint(), params.depth(),
                    translateFieldsParam(inference.modelContext(), path.schema(), fields)),
            readData(params.content(), path.instance(), params.withDefaults()), null);
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.CONFIGURATION, path);
        }
    }

    /**
     * Translate a {@link FieldsParam} to a complete list of child nodes organized into levels, suitable for use with
     * {@link ParameterAwareNormalizedNodeWriter}.
     *
     * <p>
     * Fields parser that stores set of {@link QName}s in each level. Because of this fact, from the output it is only
     * possible to assume on what depth the selected element is placed. Identifiers of intermediary mixin nodes are also
     * flatten to the same level as identifiers of data nodes.<br>
     * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
     * <pre>
     * level 0: ['a', 'd']
     * level 1: ['b', 'x', 'e']
     * level 2: ['c']
     * </pre>
     *
     * @param modelContext EffectiveModelContext
     * @param startNode {@link DataSchemaContext} of the API request path
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains set of {@link QName}
     */
    @VisibleForTesting
    public static @NonNull List<Set<QName>> translateFieldsParam(final @NonNull EffectiveModelContext modelContext,
            final DataSchemaContext startNode, final @NonNull FieldsParam input) {
        final var parsed = new ArrayList<Set<QName>>();
        processSelectors(parsed, modelContext, startNode.dataSchemaNode().getQName().getModule(), startNode,
            input.nodeSelectors(), 0);
        return parsed;
    }

    private static void processSelectors(final List<Set<QName>> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final DataSchemaContext startNode, final List<NodeSelector> selectors,
            final int index) {
        final Set<QName> startLevel;
        if (parsed.size() <= index) {
            startLevel = new HashSet<>();
            parsed.add(startLevel);
        } else {
            startLevel = parsed.get(index);
        }
        for (var selector : selectors) {
            var node = startNode;
            var namespace = startNamespace;
            var level = startLevel;
            var levelIndex = index;

            // Note: path is guaranteed to have at least one step
            final var it = selector.path().iterator();
            while (true) {
                // FIXME: The layout of this loop is rather weird, which is due to how prepareQNameLevel() operates. We
                //        need to call it only when we know there is another identifier coming, otherwise we would end
                //        up with empty levels sneaking into the mix.
                //
                //        Dealing with that weirdness requires understanding what the expected end results are and a
                //        larger rewrite of the algorithms involved.
                final var step = it.next();
                final var module = step.module();
                if (module != null) {
                    // FIXME: this is not defensive enough, as we can fail to find the module
                    namespace = context.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed identifier to results for current level
                node = addChildToResult(node, step.identifier().bindTo(namespace), level);
                if (!it.hasNext()) {
                    break;
                }

                // go one level down
                level = prepareQNameLevel(parsed, level);
                levelIndex++;
            }

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, context, namespace, node, subs, levelIndex + 1);
            }
        }
    }

    /**
     * Preparation of the identifiers level that is used as storage for parsed identifiers. If the current level exist
     * at the index that doesn't equal to the last index of already parsed identifiers, a new level of identifiers
     * is allocated and pushed to input parsed identifiers.
     *
     * @param parsedIdentifiers Already parsed list of identifiers grouped to multiple levels.
     * @param currentLevel Current level of identifiers (set).
     * @return Existing or new level of identifiers.
     */
    private static Set<QName> prepareQNameLevel(final List<Set<QName>> parsedIdentifiers,
            final Set<QName> currentLevel) {
        final var existingLevel = parsedIdentifiers.stream()
                .filter(qNameSet -> qNameSet.equals(currentLevel))
                .findAny();
        if (existingLevel.isPresent()) {
            final int index = parsedIdentifiers.indexOf(existingLevel.orElseThrow());
            if (index == parsedIdentifiers.size() - 1) {
                final var nextLevel = new HashSet<QName>();
                parsedIdentifiers.add(nextLevel);
                return nextLevel;
            }

            return parsedIdentifiers.get(index + 1);
        }

        final var nextLevel = new HashSet<QName>();
        parsedIdentifiers.add(nextLevel);
        return nextLevel;
    }

    /**
     * Add parsed child of current node to result for current level.
     *
     * @param currentNode current node
     * @param childQName parsed identifier of child node
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    private static DataSchemaContext addChildToResult(final DataSchemaContext currentNode, final QName childQName,
            final Set<QName> level) {
        // resolve parent node
        final var parentNode = resolveMixinNode(currentNode, level, currentNode.dataSchemaNode().getQName());
        if (parentNode == null) {
            throw new RestconfDocumentedException(
                    "Not-mixin node missing in " + currentNode.getPathStep().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // resolve child node
        final DataSchemaContext childNode = resolveMixinNode(childByQName(parentNode, childQName), level, childQName);
        if (childNode == null) {
            throw new RestconfDocumentedException(
                    "Child " + childQName.getLocalName() + " node missing in "
                            + currentNode.getPathStep().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // add final childNode node to level nodes
        level.add(childNode.dataSchemaNode().getQName());
        return childNode;
    }

    private static @Nullable DataSchemaContext childByQName(final DataSchemaContext parent, final QName qname) {
        return parent instanceof DataSchemaContext.Composite composite ? composite.childByQName(qname) : null;
    }

    /**
     * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
     * All nodes expect of not mixin node are added to current level nodes.
     *
     * @param node          initial mixin or not-mixin node
     * @param level         current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    private static @Nullable DataSchemaContext resolveMixinNode(final @Nullable DataSchemaContext node,
            final @NonNull Set<QName> level, final @NonNull QName qualifiedName) {
        DataSchemaContext currentNode = node;
        while (currentNode instanceof PathMixin currentMixin) {
            level.add(qualifiedName);
            currentNode = currentMixin.childByQName(qualifiedName);
        }

        return currentNode;
    }

}
