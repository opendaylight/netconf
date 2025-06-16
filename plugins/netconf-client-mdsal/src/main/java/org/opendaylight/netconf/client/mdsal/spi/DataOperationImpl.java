/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.mdsal.spi.data.AsyncGetDataProcessor;
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

/**
 * This class performs data manipulation and retrieval operations on a NETCONF YANG datastore.
 */
public final class DataOperationImpl implements DataOperationService {
    private static final Logger LOG = LoggerFactory.getLogger(DataOperationImpl.class);
    private static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    private final Map<Data, NormalizedNodeContainer<?>> readListCache = new ConcurrentHashMap<>();
    private final DataStoreService dataStoreService;

    public DataOperationImpl(final @NonNull DataStoreService dataStoreService) {
        this.dataStoreService = requireNonNull(dataStoreService);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> createData(final Data path, final NormalizedNode data) {
        final var instance = path.instance();
        if (isNonEmptyList(data)) {
            var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = instance.node(child.name());
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.create(childPath, child));
            }
            return futureChain;
        }
        return dataStoreService.create(instance, data);
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
        final var instance = path.instance();
        if (isNonEmptyList(data)) {
            var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = instance.node(child.name());
                futureChain = addIntoFutureChain(futureChain, () -> dataStoreService.replace(childPath, child));
            }
            return futureChain;
        } else {
            return dataStoreService.replace(instance, data);
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(final Data path, final DataGetParams params) {
        //  type of data to read (config, state, all)
        final var content = params.content();
        // value of with-defaults parameter
        final var fields = params.fields();
        final var processor = new AsyncGetDataProcessor(path, params.withDefaults());
        return switch (content) {
            case ALL -> {
                // PREPARE STATE DATA NODE
                final var stateDataFuture = readDataViaTransaction(OPERATIONAL, path, fields);
                // PREPARE CONFIG DATA NODE
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, fields);
                final var cachedConfigDataFuture = cacheListData(configDataFuture, path);
                yield processor.all(cachedConfigDataFuture, stateDataFuture);
            }
            case CONFIG -> {
                final var configDataFuture = readDataViaTransaction(CONFIGURATION, path, fields);
                final var cachedConfigDataFuture = cacheListData(configDataFuture, path);
                yield processor.config(cachedConfigDataFuture);
            }
            case NONCONFIG -> processor.nonConfig(
                readDataViaTransaction(OPERATIONAL, path, fields));
        };
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        return dataStoreService.commit();
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link DataStoreService} with
     * specified subtrees that should only be read.
     *
     * @param store  datastore type
     * @param path   parent path to selected fields
     * @param fields FieldsParser prepared to read from starting node
     * @return {@link ListenableFuture}
     */
    private ListenableFuture<Optional<NormalizedNode>> readDataViaTransaction(final @NonNull LogicalDatastoreType store,
            final Data path, final FieldsParam fields) {
        // Paths to selected subtrees which should be read, relative to the parent path
        final List<YangInstanceIdentifier> fieldPaths;
        try {
            fieldPaths = FieldsParamParser.fieldsParamsToPaths(path.inference().modelContext(), path.schema(), fields);
        } catch (RequestException e) {
            return Futures.immediateFailedFuture(e);
        }
        return dataStoreService.get(store, path.instance(), fieldPaths);
    }

    /**
     * Asynchronously populates the {@code readListCache} attribute if retrieved data from {@code dataRead} parameter
     * represents List or LeafList.
     *
     * @param dataRead Get request future.
     * @param path The path on which the GET request is performed.
     * @return {@link ListenableFuture} The returned future will complete with the same result as {@code dataRead},
     *         after the caching operation has been performed.
     */
    private ListenableFuture<Optional<NormalizedNode>> cacheListData(
            final ListenableFuture<Optional<NormalizedNode>> dataRead, final Data path) {
        final var dataSchemaNode = path.schema().dataSchemaNode();
        if (dataSchemaNode instanceof ListSchemaNode || dataSchemaNode instanceof LeafListSchemaNode) {
            return Futures.whenAllComplete(dataRead).call(() -> {
                final var resultData = Futures.getDone(dataRead);
                // If list data are present store them into readListCache.
                if (resultData != null && resultData.isPresent()
                        && resultData.orElseThrow() instanceof NormalizedNodeContainer<?> container) {
                    LOG.trace("Populate cache list on path {} with data {}", path, container);
                    readListCache.put(path, container);
                }
                return resultData;
            }, MoreExecutors.directExecutor());
        }
        return dataRead;
    }

    private ListenableFuture<? extends DOMRpcResult> eraseData(final Data path,
            final Function<YangInstanceIdentifier, ListenableFuture<? extends DOMRpcResult>> operation) {
        if (isListPath(path)) {
            final var getListFuture = getListItemsForRemove(path);
            return Futures.transformAsync(getListFuture,
                result -> {
                    final var listData = result.map(data -> ((NormalizedNodeContainer<?>) data).body())
                        .orElse(List.of());
                    var futureChain = RPC_SUCCESS;
                    if (listData.isEmpty()) {
                        // No items to delete, result in returning RPC_SUCCESS.
                        LOG.debug("Path {} contains no items, delete operation omitted.", path);
                    } else {
                        for (final var childElement : listData) {
                            final var childPath = path.instance().node(childElement.name());
                            futureChain = addIntoFutureChain(futureChain, () -> operation.apply(childPath));
                        }
                    }
                    return futureChain;
                }, MoreExecutors.directExecutor());
        }
        return operation.apply(path.instance());
    }

    private ListenableFuture<Optional<NormalizedNode>> getListItemsForRemove(final Data path) {
        final var cached = readListCache.remove(path);
        if (cached != null) {
            LOG.debug("Found cached list data {} on path {}", cached, path);
            return Futures.immediateFuture(Optional.of(cached));
        }
        final var instance = path.instance();
        // check if keys only can be filtered out to minimize amount of data retrieved
        if (path.schema().dataSchemaNode() instanceof ListSchemaNode listSchemaNode) {
            final var keyFields = listSchemaNode.getKeyDefinition().stream().map(YangInstanceIdentifier::of).toList();
            final var child = NodeIdentifierWithPredicates.of(instance.getLastPathArgument().getNodeType());
            final var childPath = instance.node(child);
            return dataStoreService.get(CONFIGURATION, childPath, keyFields);
        } else {
            return dataStoreService.get(CONFIGURATION, instance, List.of());
        }
    }

    private static boolean noWarnings(final Collection<? extends @NonNull RpcError> errors) {
        return !errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    private static ListenableFuture<? extends DOMRpcResult> addIntoFutureChain(
            final ListenableFuture<? extends DOMRpcResult> futureChain,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && noWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                return nextFuture.get();
            }, MoreExecutors.directExecutor());
    }

    private static NetconfDocumentedException getNetconfDocumentedException(
            final Collection<? extends RpcError> errors) {
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

    private static boolean isNonEmptyList(final NormalizedNode data) {
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
}
