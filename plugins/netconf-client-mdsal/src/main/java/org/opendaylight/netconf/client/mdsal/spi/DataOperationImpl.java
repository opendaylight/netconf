/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.mdsal.spi.data.AsyncWithDefaultsProcessor;
import org.opendaylight.restconf.mdsal.spi.data.RestconfStrategy;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataOperationImpl implements DataOperationService {
    private static final Logger LOG = LoggerFactory.getLogger(DataOperationImpl.class);
    private static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    private final Map<Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();
    private final DataStoreService dataStoreService;

    public DataOperationImpl(DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> createData(final Data path, final NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.instance().node(child.name());
                addIntoFutureChain(futureChain, () -> dataStoreService.create(childPath, child));
            }
            return futureChain;
        }
        return dataStoreService.create(path.instance(), data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(final Data path) {
        return eraseData(path, dataStoreService::delete);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(final Data path) {
        return eraseData(path, dataStoreService::remove);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(final Data path, final NormalizedNode data) {
        return dataStoreService.merge(path.instance(), data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(final Data path, final NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.instance().node(child.name());
                addIntoFutureChain(futureChain, () -> dataStoreService.put(childPath, child));
            }
            return futureChain;
        } else {
            return dataStoreService.put(path.instance(), data);
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(final Data path, final DataGetParams params) {
        //  type of data to read (config, state, all)
        final var content = params.content();
        // value of with-defaults parameter
        final var fields = params.fields();
        final var processor = new AsyncWithDefaultsProcessor(path, params.withDefaults());
        final var param = new FieldParams(path.inference().modelContext(), fields);
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataFuture = readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, param);
                // PREPARE CONFIG DATA NODE
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, param);

                yield processor.all(configDataFuture, stateDataFuture);
            }
            case CONFIG -> {
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, param);
                yield processor.config(configDataFuture);
            }
            case NONCONFIG -> processor.nonConfig(
                readDataViaTransaction(LogicalDatastoreType.OPERATIONAL, path, param));
        };
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        return dataStoreService.commit();
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link RestconfStrategy} with
     * specified subtrees that should only be read.
     *
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param fields                paths to selected subtrees which should be read, relative to the parent path
     * @return {@link NormalizedNode}
     * @throws RequestException when an error occurs
     */
    private ListenableFuture<Optional<NormalizedNode>> readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final Data path, final FieldParams fields) {
        // Paths to selected subtrees which should be read, relative to the parent path
        final List<YangInstanceIdentifier> fieldPaths;
        try {
            fieldPaths = fields.fieldsParamToPaths(path.schema());
        } catch (RequestException e) {
            return Futures.immediateFailedFuture(e);
        }
        final var readFuture = dataStoreService.read(store, path.instance(), fieldPaths);
        addCallbackToAddList(readFuture, path);
        return readFuture;
    }

    void addCallbackToAddList(final ListenableFuture<Optional<NormalizedNode>> dataRead, final Data path) {
        final var dataSchemaNode = path.schema().dataSchemaNode();
        if (dataSchemaNode instanceof ListSchemaNode || dataSchemaNode instanceof LeafListSchemaNode) {
            Futures.addCallback(dataRead, new FutureCallback<>() {
                @Override
                public void onSuccess(final Optional<NormalizedNode> result) {
                    readListCache.put(path, result.map(data -> ((NormalizedNodeContainer<?>) data).body())
                        .orElse(List.of()));
                }

                @Override
                public void onFailure(Throwable cause) {

                }
            }, Executors.newSingleThreadExecutor());
        }
    }

    private ListenableFuture<? extends DOMRpcResult> eraseData(final Data path,
            final Function<YangInstanceIdentifier, ListenableFuture<? extends DOMRpcResult>> operation) {
        if (isListPath(path)) {
            final Collection<? extends NormalizedNode> items;
            try {
                items = getListItemsForRemove(path);
            } catch (RequestException cause) {
                return Futures.immediateFailedFuture(cause);
            }
            if (items.isEmpty()) {
                LOG.debug("Path {} contains no items, delete operation omitted.", path);
                return RPC_SUCCESS;
            } else {
                var futureChain = RPC_SUCCESS;
                for (final var childElement : items) {
                    final var childPath = childPath(path, List.of(childElement.name()));
                    futureChain = addIntoFutureChain(futureChain, () -> operation.apply(childPath.instance()));
                }
                return futureChain;
            }
        }
        return operation.apply(path.instance());
    }

    private static boolean isNonEmptyListPath(final NormalizedNode data) {
        return (data instanceof MapNode || data instanceof LeafSetNode)
            && !((NormalizedNodeContainer<?>) data).body().isEmpty();
    }

    private static boolean isListPath(final Data path) {
        if (path.instance().getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifier) {
            // list can be referenced by NodeIdentifier only, prevent list item do be identified as list
            final var schemaNode = path.schema().dataSchemaNode();
            return schemaNode instanceof ListSchemaNode || schemaNode instanceof LeafListSchemaNode;
        }
        return false;
    }

    private @NonNull Collection<? extends NormalizedNode> getListItemsForRemove(final Data path)
        throws RequestException {
        final var cached = readListCache.remove(path);
        if (cached != null) {
            return cached;
        }
        final ListenableFuture<Optional<NormalizedNode>> future;
        // check if keys only can be filtered out to minimize amount of data retrieved
        if (path.schema().dataSchemaNode() instanceof ListSchemaNode listSchemaNode) {
            final var keyFields = listSchemaNode.getKeyDefinition().stream().map(YangInstanceIdentifier::of).toList();
            final var child = NodeIdentifierWithPredicates.of(path.instance().getLastPathArgument().getNodeType());
            final var childPath = path.instance().node(child);
            future = dataStoreService.read(CONFIGURATION, childPath, keyFields);
        } else {
            future = dataStoreService.read(CONFIGURATION, path.instance(), List.of());
        }

        final var retrieved = RestconfStrategy.syncAccess(future, path.instance());
        return retrieved.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of());
    }

    protected static Data childPath(final Data path, final List<YangInstanceIdentifier.PathArgument> prefixAndBody) {
        var ret = path.instance();
        for (var arg : prefixAndBody) {
            ret = ret.node(arg);
        }
        final var databind = path.databind();
        final var childAndStack = databind.schemaTree().enterPath(ret).orElseThrow();
        return new Data(databind, childAndStack.stack().toInference(), ret, childAndStack.node());
    }

    static boolean allWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    ListenableFuture<? extends DOMRpcResult> addIntoFutureChain(
        final ListenableFuture<? extends DOMRpcResult> futureChain,
        final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                final var listenableFuture = nextFuture.get();
                addCallback(listenableFuture);
                return listenableFuture;
            }, Executors.newSingleThreadExecutor());
    }

    void addCallback(final ListenableFuture<? extends DOMRpcResult> listenableFuture) {
        Futures.addCallback(listenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
            }

            @Override
            public void onFailure(final Throwable cause) {
                dataStoreService.cancel();
            }
        }, Executors.newSingleThreadExecutor());
    }

    static NetconfDocumentedException getNetconfDocumentedException(final Collection<? extends RpcError> errors) {
        var errType = ErrorType.APPLICATION;
        var errSeverity = ErrorSeverity.ERROR;
        final var msgBuilder = new StringJoiner(" ");
        var errorTag = ErrorTag.OPERATION_FAILED;
        for (final RpcError error : errors) {
            errType = error.getErrorType();
            errSeverity = error.getSeverity();
            msgBuilder.add(error.getMessage());
            msgBuilder.add(error.getInfo());
            errorTag = error.getTag();
        }
        return new NetconfDocumentedException("RPC during tx failed. " + msgBuilder, errType, errorTag, errSeverity);
    }
}
