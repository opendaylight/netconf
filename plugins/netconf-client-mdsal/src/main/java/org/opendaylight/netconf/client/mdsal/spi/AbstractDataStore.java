/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.MERGE;
import static org.opendaylight.netconf.api.EffectiveOperation.REMOVE;
import static org.opendaylight.netconf.api.EffectiveOperation.REPLACE;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.netconf.databind.DatabindContext;
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

public abstract class AbstractDataStore implements DataStoreService, DataOperationService {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataStore.class);

    static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    final Map<Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();
    final AtomicBoolean lock = new AtomicBoolean(false);
    final NetconfBaseOps netconfOps;
    final @NonNull RemoteDeviceId id;
    final boolean rollbackSupport;
    final boolean lockDatastore;

    AbstractDataStore(final NetconfBaseOps netconfOps, final RemoteDeviceId id, boolean rollbackSupport,
            boolean lockDatastore) {
        this.netconfOps = netconfOps;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.lockDatastore = lockDatastore;
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final DatabindContext databind,
            final RemoteDeviceServices.Rpcs rpcs, final NetconfSessionPreferences sessionPreferences,
            final boolean lockDatastore) {
        final var netconfOps = new NetconfBaseOps(databind, rpcs);
        return of(id, netconfOps, sessionPreferences, lockDatastore);
    }

    public static @NonNull AbstractDataStore of(final RemoteDeviceId id, final NetconfBaseOps netconfOps,
            final NetconfSessionPreferences sessionPreferences, final boolean lockDatastore) {
        final boolean rollbackSupport = sessionPreferences.isRollbackSupported();

        // Examine preferences and decide which implementation to use
        if (sessionPreferences.isCandidateSupported()) {
            return new Candidate(netconfOps, id, rollbackSupport, lockDatastore);
        } else if (sessionPreferences.isRunningWritable()) {
            return new Running(netconfOps, id, rollbackSupport, lockDatastore);
        } else {
            throw new IllegalArgumentException("Device " + id.name() + " has advertised neither :writable-running nor "
                + ":candidate capability. Failed to establish session, as at least one of these must be advertised.");
        }
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> createData(YangInstanceIdentifier path, NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.node(child.name());
                addIntoFutureChain(futureChain, () -> editConfig(CREATE, child, childPath), false);
            }
            return futureChain;
        }
        return editConfig(CREATE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(Data path) {
        return eraseData(path, (p) -> editConfig(DELETE, p));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(Data path) {
        return eraseData(path, (p) -> editConfig(REMOVE, p));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(YangInstanceIdentifier path, NormalizedNode data) {
        final var netconfOpsChild = netconfOps.createNode(data, MERGE, path);
        return editConfig(netconfOpsChild);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(YangInstanceIdentifier path, NormalizedNode data) {
        if (isNonEmptyListPath(data)) {
            final var futureChain = RPC_SUCCESS;
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.node(child.name());
                addIntoFutureChain(futureChain, () -> editConfig(REPLACE, child, childPath), false);
            }
            return futureChain;
        } else {
            return editConfig(REPLACE, data, path);
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(Data path, DataGetParams params) {
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

    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path,
        final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path, fields);
            case OPERATIONAL -> get(path, fields);
        };
    }

    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final Data path) {
        return switch (store) {
            case CONFIGURATION -> getConfig(path);
            case OPERATIONAL -> get(path);
        };
    }

    abstract ListenableFuture<? extends DOMRpcResult> unlock();

    abstract void cancel();

    ListenableFuture<Optional<NormalizedNode>> get(final Data path) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path.instance()));
    }

    ListenableFuture<Optional<NormalizedNode>> get(final Data path,
        final List<YangInstanceIdentifier> fields) {
        return netconfOps.getData(new NetconfRpcFutureCallback("Data read", id), Optional.of(path.instance()),
            fields);
    }

    ListenableFuture<Optional<NormalizedNode>> getConfig(final Data path) {
        final var dataRead = netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
            Optional.of(path.instance()));
        addCallbackToAddList(dataRead, path);
        return dataRead;
    }

    ListenableFuture<Optional<NormalizedNode>> getConfig(final Data path,
        final List<YangInstanceIdentifier> fields) {
        final var dataRead = netconfOps.getConfigRunningData(new NetconfRpcFutureCallback("Data read", id),
                Optional.of(path.instance()), fields);
        addCallbackToAddList(dataRead, path);
        return dataRead;
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

    static void executeWithLogging(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation) {
        var operationResult = operation.get();
        Futures.addCallback(operationResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult rpcResult) {
                if (rpcResult != null && !rpcResult.errors().isEmpty()) {
                    LOG.error("Errors occurred during processing of the RPC operation: {}",
                        rpcResult.errors().stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Error processing operation", throwable);
            }
        }, MoreExecutors.directExecutor());
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
        if (fieldPaths.isEmpty()) {
            return read(store, path);
        }
        return read(store, path, fieldPaths);
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
                    futureChain = addIntoFutureChain(futureChain, () -> operation.apply(childPath.instance()), false);
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
            final var childPath = childPath(path, List.of(child));
            future = read(CONFIGURATION, childPath, keyFields);
        } else {
            future = read(CONFIGURATION, path);
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
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture, final boolean unlockInSuccess) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (result != null && !result.errors().isEmpty() && !allWarnings(result.errors())) {
                    return Futures.immediateFailedFuture(getNetconfDocumentedException(result.errors()));
                }
                final var listenableFuture = nextFuture.get();
                addCallback(listenableFuture, unlockInSuccess);
                return listenableFuture;
            }, Executors.newSingleThreadExecutor());
    }

    void addCallback(final ListenableFuture<? extends DOMRpcResult> listenableFuture, final boolean unlockInSuccess) {
        Futures.addCallback(listenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (unlockInSuccess) {
                    unlock();
                }
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancel();
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
