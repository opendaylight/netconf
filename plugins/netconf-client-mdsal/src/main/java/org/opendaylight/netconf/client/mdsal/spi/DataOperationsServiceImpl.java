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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.mdsal.spi.data.AsyncGetDataProcessor;
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

/**
 * This class performs data manipulation and retrieval operations on a NETCONF YANG datastore.
 */
@NonNullByDefault
public final class DataOperationsServiceImpl implements DataOperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(DataOperationsServiceImpl.class);
    private static final ListenableFuture<? extends DOMRpcResult> RPC_SUCCESS = Futures.immediateFuture(
        new DefaultDOMRpcResult());

    private final Map<Data, Collection<? extends NormalizedNode>> readListCache = new ConcurrentHashMap<>();
    private final NetconfDataTreeService dataTreeService;
    private final AtomicBoolean lock = new AtomicBoolean(false);

    public DataOperationsServiceImpl(final NetconfDataTreeService dataStoreService) {
        this.dataTreeService = requireNonNull(dataStoreService);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> createData(final Data path, final NormalizedNode data) {
        var futureChain = lock();
        final var instance = path.instance();
        if (isNonEmptyList(data)) {
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = instance.node(child.name());
                futureChain = addIntoFutureChain(futureChain, () -> dataTreeService.create(CONFIGURATION, childPath,
                    child, Optional.empty()));
            }
        } else {
            futureChain = addIntoFutureChain(futureChain, () -> dataTreeService.create(CONFIGURATION, instance, data,
                Optional.empty()));
        }
        return addCancelIfFails(futureChain);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(final Data path) {
        var futureChain = lock();
        futureChain = addIntoFutureChain(futureChain, () -> eraseData(path,
            (b) -> dataTreeService.delete(CONFIGURATION, b)));
        return addCancelIfFails(futureChain);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(final Data path) {
        var futureChain = lock();
        futureChain = addIntoFutureChain(futureChain, () -> eraseData(path,
            (b) -> dataTreeService.remove(CONFIGURATION, b)));
        return addCancelIfFails(futureChain);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(final Data path, final NormalizedNode data) {
        var futureChain = lock();
        futureChain = addIntoFutureChain(futureChain, () -> dataTreeService.merge(CONFIGURATION, path.instance(), data,
            Optional.empty()));
        return addCancelIfFails(futureChain);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(final Data path, final NormalizedNode data) {
        var futureChain = lock();
        final var instance = path.instance();
        if (isNonEmptyList(data)) {
            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = instance.node(child.name());
                futureChain = addIntoFutureChain(futureChain, () -> dataTreeService.replace(CONFIGURATION, childPath,
                    child, Optional.empty()));
            }
        } else {
            futureChain = addIntoFutureChain(futureChain, () -> dataTreeService.replace(CONFIGURATION, instance, data,
                Optional.empty()));
        }
        return addCancelIfFails(futureChain);
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
        var futureChain = addCancelIfFails(dataTreeService.commit());
        return addIntoFutureChain(futureChain, this::unlock);
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link NetconfDataTreeService} with
     * specified subtrees that should only be read.
     *
     * @param store  datastore type
     * @param path   parent path to selected fields
     * @param fields FieldsParser prepared to read from starting node
     * @return {@link ListenableFuture}
     */
    private ListenableFuture<Optional<NormalizedNode>> readDataViaTransaction(final LogicalDatastoreType store,
            final Data path, final @Nullable FieldsParam fields) {
        // Paths to selected subtrees which should be read, relative to the parent path
        final List<YangInstanceIdentifier> fieldPaths;
        try {
            fieldPaths = FieldsParamParser.fieldsParamsToPaths(path.inference().modelContext(), path.schema(), fields);
        } catch (RequestException e) {
            return Futures.immediateFailedFuture(e);
        }

        final var instance = path.instance();
        if (store == CONFIGURATION) {
            return fieldPaths.isEmpty() ? dataTreeService.getConfig(instance)
                : dataTreeService.getConfig(instance, fieldPaths);
        } else {
            return fieldPaths.isEmpty() ? dataTreeService.get(instance) : dataTreeService.get(instance, fieldPaths);
        }
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
                if (resultData.isPresent()
                        && resultData.orElseThrow() instanceof NormalizedNodeContainer<?> container) {
                    LOG.trace("Populate cache list on path {} with data {}", path, container.body());
                    readListCache.put(path, container.body());
                }
                return resultData;
            }, MoreExecutors.directExecutor());
        }
        return dataRead;
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
                    final var childPath = path.instance().node(childElement.name());
                    futureChain = addIntoFutureChain(futureChain, () -> operation.apply(childPath));
                }
                return futureChain;
            }
        }
        return operation.apply(path.instance());
    }

    private Collection<? extends NormalizedNode> getListItemsForRemove(final Data path)
            throws RequestException {
        final var cached = readListCache.remove(path);
        if (cached != null) {
            LOG.debug("Found cached list data {} on path {}", cached, path);
            return cached;
        }
        final var instance = path.instance();
        final ListenableFuture<Optional<NormalizedNode>> future;
        // check if keys only can be filtered out to minimize amount of data retrieved
        if (path.schema().dataSchemaNode() instanceof ListSchemaNode listSchemaNode) {
            final var keyFields = listSchemaNode.getKeyDefinition().stream().map(YangInstanceIdentifier::of).toList();
            final var child = NodeIdentifierWithPredicates.of(instance.getLastPathArgument().getNodeType());
            final var childPath = instance.node(child);
            if (keyFields.isEmpty()) {
                future = dataTreeService.getConfig(childPath);
            } else {
                future = dataTreeService.getConfig(childPath, keyFields);
            }
        } else {
            future = dataTreeService.getConfig(instance);
        }

        final var retrieved = RestconfStrategy.syncAccess(future, instance);
        return retrieved.map(data -> ((NormalizedNodeContainer<?>) data).body()).orElse(List.of());
    }

    private ListenableFuture<? extends DOMRpcResult> lock() {
        if (!lock.getAndSet(true)) {
            return dataTreeService.lock();
        }
        return RPC_SUCCESS;
    }

    private ListenableFuture<? extends DOMRpcResult> unlock() {
        if (lock.getAndSet(false)) {
            return dataTreeService.unlock();
        }
        return RPC_SUCCESS;
    }

    /**
     * If the provided {@code future} fails, this method chains a call to {@link DataOperationsServiceImpl#cancel()} into the
     * returned future. The result of the returned future is the result of the provided future. The result from
     * {@link DataOperationsServiceImpl#cancel()} is ignored and only logged.
     *
     * @param future The {@link ListenableFuture} to be checked for failure.
     * @return A {@link ListenableFuture} that includes a chained {@link DataOperationsServiceImpl#cancel()} call in case of
     *         any failure. The result of this future is the result of the {@code future} provided as a parameter.
     */
    private ListenableFuture<? extends DOMRpcResult> addCancelIfFails(
            final ListenableFuture<? extends DOMRpcResult> future) {
        final var handleRpcResult = Futures.transformAsync(future, result -> {
            if (!result.errors().isEmpty() && noWarnings(result.errors())) {
                return Futures.transform(cancel(), ignored -> result, MoreExecutors.directExecutor());
            }
            return Futures.immediateFuture(result);
        }, MoreExecutors.directExecutor());

        return Futures.catchingAsync(handleRpcResult, Throwable.class, t ->
            Futures.transformAsync(cancel(), ignored ->
                Futures.immediateFailedFuture(t), MoreExecutors.directExecutor()), MoreExecutors.directExecutor());
    }

    private ListenableFuture<? extends DOMRpcResult> cancel() {
        final var discardChanges = dataTreeService.discardChanges();
        final var unlock = unlock();
        return Futures.whenAllComplete(discardChanges, unlock).call(() -> {
            final var unlockResult = Futures.getDone(unlock);

            final var discard = Futures.getDone(discardChanges);
            if (discard != null && !discard.errors().isEmpty() && noWarnings(discard.errors())) {
                LOG.warn("Discard operation invoked by cancel result with errors {}, ignoring result.",
                    discard.errors());
            }
            if (unlockResult != null && !unlockResult.errors().isEmpty() && noWarnings(unlockResult.errors())) {
                LOG.warn("Unlock operation invoked by cancel result with errors {}, ignoring result.",
                    unlockResult.errors());
            }
            return new DefaultDOMRpcResult();
        }, MoreExecutors.directExecutor());
    }

    private static boolean noWarnings(final Collection<? extends RpcError> errors) {
        return !errors.stream().allMatch(error -> error.getSeverity() == ErrorSeverity.WARNING);
    }

    private static ListenableFuture<? extends DOMRpcResult> addIntoFutureChain(
            final ListenableFuture<? extends DOMRpcResult> futureChain,
            final Supplier<ListenableFuture<? extends DOMRpcResult>> nextFuture) {
        return Futures.transformAsync(futureChain,
            result -> {
                if (!result.errors().isEmpty() && noWarnings(result.errors())) {
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
