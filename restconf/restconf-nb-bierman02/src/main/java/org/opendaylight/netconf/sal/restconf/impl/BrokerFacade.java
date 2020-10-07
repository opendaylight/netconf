/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEditOperation;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeBuilder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("checkstyle:FinalClass")
public class BrokerFacade implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerFacade.class);

    private volatile DOMRpcService rpcService;

    private final DOMDataBroker domDataBroker;
    private final DOMNotificationService domNotification;
    private final ControllerContext controllerContext;

    private final ThreadLocal<Boolean> isMounted = new ThreadLocal<>();

    @Inject
    public BrokerFacade(final @Reference DOMRpcService rpcService, final DOMDataBroker domDataBroker,
            final @Reference DOMNotificationService domNotification, final ControllerContext controllerContext) {
        this.rpcService = Objects.requireNonNull(rpcService);
        this.domDataBroker = Objects.requireNonNull(domDataBroker);
        this.domNotification = Objects.requireNonNull(domNotification);
        this.controllerContext = Objects.requireNonNull(controllerContext);
    }

    /**
     * Factory method.
     *
     * @deprecated Just use
     *             {@link #BrokerFacade(DOMRpcService, DOMDataBroker, DOMNotificationService, ControllerContext)}
     *             constructor instead.
     */
    @Deprecated
    public static BrokerFacade newInstance(final DOMRpcService rpcService, final DOMDataBroker domDataBroker,
            final DOMNotificationService domNotification, final ControllerContext controllerContext) {
        return new BrokerFacade(rpcService, domDataBroker, domNotification, controllerContext);
    }

    @Override
    @PreDestroy
    public void close() {
    }

    /**
     * Read config data by path.
     *
     * @param path
     *            path of data
     * @return read date
     */
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path) {
        return readConfigurationData(path, null);
    }

    /**
     * Read config data by path.
     *
     * @param path
     *            path of data
     * @param withDefa
     *            value of with-defaults parameter
     * @return read date
     */
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path, final String withDefa) {
        try (DOMDataTreeReadTransaction tx = this.domDataBroker.newReadOnlyTransaction()) {
            return readDataViaTransaction(tx, CONFIGURATION, path, withDefa);
        }
    }

    /**
     * Read config data from mount point by path.
     *
     * @param mountPoint
     *            mount point for reading data
     * @param path
     *            path of data
     * @return read data
     */
    public NormalizedNode<?, ?> readConfigurationData(final DOMMountPoint mountPoint,
            final YangInstanceIdentifier path) {
        return readConfigurationData(mountPoint, path, null);
    }

    /**
     * Read config data from mount point by path.
     *
     * @param mountPoint
     *            mount point for reading data
     * @param path
     *            path of data
     * @param withDefa
     *            value of with-defaults parameter
     * @return read data
     */
    public NormalizedNode<?, ?> readConfigurationData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path,
            final String withDefa) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            try (DOMDataTreeReadTransaction tx = domDataBrokerService.get().newReadOnlyTransaction()) {
                return readDataViaTransaction(tx, CONFIGURATION, path, withDefa);
            }
        }
        throw dataBrokerUnavailable(path);
    }

    /**
     * Read operational data by path.
     *
     * @param path
     *            path of data
     * @return read data
     */
    public NormalizedNode<?, ?> readOperationalData(final YangInstanceIdentifier path) {
        try (DOMDataTreeReadTransaction tx = this.domDataBroker.newReadOnlyTransaction()) {
            return readDataViaTransaction(tx, OPERATIONAL, path);
        }
    }

    /**
     * Read operational data from mount point by path.
     *
     * @param mountPoint
     *            mount point for reading data
     * @param path
     *            path of data
     * @return read data
     */
    public NormalizedNode<?, ?> readOperationalData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            try (DOMDataTreeReadTransaction tx = domDataBrokerService.get().newReadOnlyTransaction()) {
                return readDataViaTransaction(tx, OPERATIONAL, path);
            }
        }
        throw dataBrokerUnavailable(path);
    }

    /**
     * <b>PUT configuration data</b>
     *
     * <p>
     * Prepare result(status) for PUT operation and PUT data via transaction.
     * Return wrapped status and future from PUT.
     *
     * @param globalSchema
     *            used by merge parents (if contains list)
     * @param path
     *            path of node
     * @param payload
     *            input data
     * @param point
     *            point
     * @param insert
     *            insert
     * @return wrapper of status and future of PUT
     */
    public PutResult commitConfigurationDataPut(
            final EffectiveModelContext globalSchema, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> payload, final String insert, final String point) {
        Preconditions.checkNotNull(globalSchema);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        isMounted.set(false);
        final DOMDataTreeReadWriteTransaction newReadWriteTransaction = this.domDataBroker.newReadWriteTransaction();
        final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null ? Status.OK
                : Status.CREATED;
        final FluentFuture<? extends CommitInfo> future = putDataViaTransaction(
                newReadWriteTransaction, CONFIGURATION, path, payload, globalSchema, insert, point);
        isMounted.remove();
        return new PutResult(status, future);
    }

    /**
     * <b>PUT configuration data (Mount point)</b>
     *
     * <p>
     * Prepare result(status) for PUT operation and PUT data via transaction.
     * Return wrapped status and future from PUT.
     *
     * @param mountPoint
     *            mount point for getting transaction for operation and schema
     *            context for merging parents(if contains list)
     * @param path
     *            path of node
     * @param payload
     *            input data
     * @param point
     *            point
     * @param insert
     *            insert
     * @return wrapper of status and future of PUT
     */
    public PutResult commitMountPointDataPut(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        Preconditions.checkNotNull(mountPoint);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        isMounted.set(true);
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                    domDataBrokerService.get().newReadWriteTransaction();
            final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null
                    ? Status.OK : Status.CREATED;
            final FluentFuture<? extends CommitInfo> future = putDataViaTransaction(
                    newReadWriteTransaction, CONFIGURATION, path, payload, modelContext(mountPoint), insert, point);
            isMounted.remove();
            return new PutResult(status, future);
        }
        isMounted.remove();
        throw dataBrokerUnavailable(path);
    }

    public PatchStatusContext patchConfigurationDataWithinTransaction(final PatchContext patchContext)
            throws Exception {
        final DOMMountPoint mountPoint = patchContext.getInstanceIdentifierContext().getMountPoint();

        // get new transaction and schema context on server or on mounted device
        final EffectiveModelContext schemaContext;
        final DOMDataTreeReadWriteTransaction patchTransaction;
        if (mountPoint == null) {
            schemaContext = patchContext.getInstanceIdentifierContext().getSchemaContext();
            patchTransaction = this.domDataBroker.newReadWriteTransaction();
        } else {
            schemaContext = modelContext(mountPoint);

            final Optional<DOMDataBroker> optional = mountPoint.getService(DOMDataBroker.class);

            if (optional.isPresent()) {
                patchTransaction = optional.get().newReadWriteTransaction();
            } else {
                // if mount point does not have broker it is not possible to continue and global error is reported
                LOG.error("Http Patch {} has failed - device {} does not support broker service",
                        patchContext.getPatchId(), mountPoint.getIdentifier());
                return new PatchStatusContext(
                        patchContext.getPatchId(),
                        null,
                        false,
                        ImmutableList.of(new RestconfError(
                                ErrorType.APPLICATION,
                                ErrorTag.OPERATION_FAILED,
                                "DOM data broker service isn't available for mount point "
                                        + mountPoint.getIdentifier()))
                );
            }
        }

        final List<PatchStatusEntity> editCollection = new ArrayList<>();
        List<RestconfError> editErrors;
        boolean withoutError = true;

        for (final PatchEntity patchEntity : patchContext.getData()) {
            final PatchEditOperation operation = patchEntity.getOperation();
            switch (operation) {
                case CREATE:
                    if (withoutError) {
                        try {
                            postDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http Patch operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case REPLACE:
                    if (withoutError) {
                        try {
                            putDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode(), patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http Patch operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case DELETE:
                case REMOVE:
                    if (withoutError) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http Patch operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case MERGE:
                    if (withoutError) {
                        try {
                            mergeDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http Patch operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                default:
                    LOG.error("Unsupported http Patch operation {} on target {}",
                            operation,
                            patchEntity.getTargetNode().toString());
                    break;
            }
        }

        // if errors then cancel transaction and return error status
        if (!withoutError) {
            patchTransaction.cancel();
            return new PatchStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection), false, null);
        }

        // if no errors commit transaction
        final CountDownLatch waiter = new CountDownLatch(1);
        final FluentFuture<? extends CommitInfo> future = patchTransaction.commit();
        final PatchStatusContextHelper status = new PatchStatusContextHelper();

        future.addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                status.setStatus(new PatchStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection),
                        true, null));
                waiter.countDown();
            }

            @Override
            public void onFailure(final Throwable throwable) {
                // if commit failed it is global error
                LOG.error("Http Patch {} transaction commit has failed", patchContext.getPatchId());
                status.setStatus(new PatchStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection),
                        false, ImmutableList.of(
                        new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, throwable.getMessage()))));
                waiter.countDown();
            }
        }, MoreExecutors.directExecutor());

        waiter.await();
        return status.getStatus();
    }

    // POST configuration
    public FluentFuture<? extends CommitInfo> commitConfigurationDataPost(
            final EffectiveModelContext globalSchema, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> payload, final String insert, final String point) {
        isMounted.set(false);
        FluentFuture<? extends CommitInfo> future =
                postDataViaTransaction(this.domDataBroker.newReadWriteTransaction(), CONFIGURATION, path, payload,
                                       globalSchema, insert, point);
        isMounted.remove();
        return future;
    }

    public FluentFuture<? extends CommitInfo> commitConfigurationDataPost(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        isMounted.set(true);
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            FluentFuture<? extends CommitInfo> future =
                    postDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path,
                                           payload, modelContext(mountPoint), insert, point);
            isMounted.remove();
            return future;
        }
        isMounted.remove();
        throw dataBrokerUnavailable(path);
    }

    // DELETE configuration
    public FluentFuture<? extends CommitInfo> commitConfigurationDataDelete(final YangInstanceIdentifier path) {
        return deleteDataViaTransaction(this.domDataBroker.newReadWriteTransaction(), CONFIGURATION, path);
    }

    public FluentFuture<? extends CommitInfo> commitConfigurationDataDelete(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return deleteDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path);
        }
        throw dataBrokerUnavailable(path);
    }

    // RPC
    public ListenableFuture<? extends DOMRpcResult> invokeRpc(final @NonNull QName type,
            final @NonNull NormalizedNode<?, ?> input) {
        if (this.rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
        LOG.trace("Invoke RPC {} with input: {}", type, input);
        return this.rpcService.invokeRpc(type, input);
    }

    public void registerToListenDataChanges(final LogicalDatastoreType datastore, final DataChangeScope scope,
            final ListenerAdapter listener) {
        if (listener.isListening()) {
            return;
        }

        final YangInstanceIdentifier path = listener.getPath();
        DOMDataTreeChangeService changeService = this.domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        if (changeService == null) {
            throw new UnsupportedOperationException("DOMDataBroker does not support the DOMDataTreeChangeService"
                                                        + this.domDataBroker);
        }
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(datastore, path);
        ListenerRegistration<ListenerAdapter> registration =
                                    changeService.registerDataTreeChangeListener(root, listener);
        listener.setRegistration(registration);
    }

    private NormalizedNode<?, ?> readDataViaTransaction(final DOMDataTreeReadOperations transaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        return readDataViaTransaction(transaction, datastore, path, null);
    }

    private NormalizedNode<?, ?> readDataViaTransaction(final DOMDataTreeReadOperations transaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final String withDefa) {
        LOG.trace("Read {} via Restconf: {}", datastore.name(), path);

        try {
            final Optional<NormalizedNode<?, ?>> optional = transaction.read(datastore, path).get();
            return !optional.isPresent() ? null : withDefa == null ? optional.get() :
                prepareDataByParamWithDef(optional.get(), path, withDefa);
        } catch (InterruptedException e) {
            LOG.warn("Error reading {} from datastore {}", path, datastore.name(), e);
            throw new RestconfDocumentedException("Error reading data.", e);
        } catch (ExecutionException e) {
            LOG.warn("Error reading {} from datastore {}", path, datastore.name(), e);
            throw RestconfDocumentedException.decodeAndThrow("Error reading data.", Throwables.getCauseAs(e,
                ReadFailedException.class));
        }
    }

    private NormalizedNode<?, ?> prepareDataByParamWithDef(final NormalizedNode<?, ?> result,
            final YangInstanceIdentifier path, final String withDefa) {
        boolean trim;
        switch (withDefa) {
            case "trim":
                trim = true;
                break;
            case "explicit":
                trim = false;
                break;
            default:
                throw new RestconfDocumentedException("Bad value used with with-defaults parameter : " + withDefa);
        }

        final EffectiveModelContext ctx = controllerContext.getGlobalSchema();
        final DataSchemaContextTree baseSchemaCtxTree = DataSchemaContextTree.from(ctx);
        final DataSchemaNode baseSchemaNode = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
        if (result instanceof ContainerNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder =
                    Builders.containerBuilder((ContainerSchemaNode) baseSchemaNode);
            buildCont(builder, (ContainerNode) result, baseSchemaCtxTree, path, trim);
            return builder.build();
        }

        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder =
                Builders.mapEntryBuilder((ListSchemaNode) baseSchemaNode);
        buildMapEntryBuilder(builder, (MapEntryNode) result, baseSchemaCtxTree, path, trim,
            ((ListSchemaNode) baseSchemaNode).getKeyDefinition());
        return builder.build();
    }

    private void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim, final List<QName> keys) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> childBuilder =
                        Builders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(childBuilder, (ContainerNode) child, baseSchemaCtxTree, path, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, MapNode> childBuilder =
                        Builders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.getValue();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (keys.contains(child.getNodeType())) {
                    leafBuilder.withValue(child.getValue());
                    builder.withChild(leafBuilder.build());
                } else {
                    if (trim) {
                        if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(child.getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    } else {
                        if (defaultVal != null && defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(child.getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    }
                }
            }
        }
    }

    private void buildList(final CollectionNodeBuilder<MapEntryNode, MapNode> builder, final MapNode result,
            final DataSchemaContextTree baseSchemaCtxTree, final YangInstanceIdentifier path, final boolean trim,
            final List<QName> keys) {
        for (final MapEntryNode mapEntryNode : result.getValue()) {
            final YangInstanceIdentifier actualNode = path.node(mapEntryNode.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(actualNode).orElseThrow()
                    .getDataSchemaNode();
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                    Builders.mapEntryBuilder((ListSchemaNode) childSchema);
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, baseSchemaCtxTree, actualNode, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builderChild =
                        Builders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(builderChild, result, baseSchemaCtxTree, actualPath, trim);
                builder.withChild(builderChild.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, MapNode> childBuilder =
                        Builders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.getValue();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (trim) {
                    if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(child.getValue());
                        builder.withChild(leafBuilder.build());
                    }
                } else {
                    if (defaultVal != null && defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(child.getValue());
                        builder.withChild(leafBuilder.build());
                    }
                }
            }
        }
    }

    /**
     * POST data and submit transaction {@link DOMDataReadWriteTransaction}.
     */
    private FluentFuture<? extends CommitInfo> postDataViaTransaction(
            final DOMDataTreeReadWriteTransaction rwTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext, final String insert, final String point) {
        LOG.trace("POST {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        postData(rwTransaction, datastore, path, payload, schemaContext, insert, point);
        return rwTransaction.commit();
    }

    /**
     * POST data and do NOT submit transaction {@link DOMDataReadWriteTransaction}.
     */
    private void postDataWithinTransaction(
            final DOMDataTreeReadWriteTransaction rwTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", datastore.name(), path, payload);
        postData(rwTransaction, datastore, path, payload, schemaContext, null, null);
    }

    private void postData(final DOMDataTreeReadWriteTransaction rwTransaction, final LogicalDatastoreType datastore,
                          final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
                          final EffectiveModelContext schemaContext, final String insert, final String point) {
        if (insert == null) {
            makeNormalPost(rwTransaction, datastore, path, payload, schemaContext);
            return;
        }

        final DataSchemaNode schemaNode = checkListAndOrderedType(schemaContext, path);
        checkItemDoesNotExists(rwTransaction, datastore, path);
        switch (insert) {
            case "first":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        rwTransaction.delete(datastore, path.getParent().getParent());
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                        makeNormalPost(rwTransaction, datastore, path.getParent().getParent(), readList,
                            schemaContext);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        rwTransaction.delete(datastore, path.getParent());
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                        makeNormalPost(rwTransaction, datastore, path.getParent().getParent(), readLeafList,
                            schemaContext);
                    }
                }
                break;
            case "last":
                simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                break;
            case "before":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointListPost(rwTransaction, datastore, path, payload, schemaContext, point,
                            readList,
                            true);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointLeafListPost(rwTransaction, datastore, path, payload, schemaContext, point,
                            readLeafList, true);
                    }
                }
                break;
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointListPost(rwTransaction, datastore, path, payload, schemaContext, point,
                            readList,
                            false);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointLeafListPost(rwTransaction, datastore, path, payload, schemaContext, point,
                            readLeafList, false);
                    }
                }
                break;
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, "
                            + "but was: " + insert);
        }
    }

    private void insertWithPointLeafListPost(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext.toInstanceIdentifier(point);
        int lastItemPosition = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                checkItemDoesNotExists(rwTransaction, datastore, path);
                simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            checkItemDoesNotExists(rwTransaction, datastore, childPath);
            rwTransaction.put(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private void insertWithPointListPost(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String point, final MapNode readList, final boolean before) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext.toInstanceIdentifier(point);
        int lastItemPosition = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier()
                    .equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                checkItemDoesNotExists(rwTransaction, datastore, path);
                simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            checkItemDoesNotExists(rwTransaction, datastore, childPath);
            rwTransaction.put(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static DataSchemaNode checkListAndOrderedType(final EffectiveModelContext ctx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier parent = path.getParent();
        final DataSchemaContextNode<?> node = DataSchemaContextTree.from(ctx).findChild(parent).orElseThrow();
        final DataSchemaNode dataSchemaNode = node.getDataSchemaNode();

        if (dataSchemaNode instanceof ListSchemaNode) {
            if (!((ListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException("Insert parameter can be used only with ordered-by user list.");
            }
            return dataSchemaNode;
        }
        if (dataSchemaNode instanceof LeafListSchemaNode) {
            if (!((LeafListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException(
                        "Insert parameter can be used only with ordered-by user leaf-list.");
            }
            return dataSchemaNode;
        }
        throw new RestconfDocumentedException("Insert parameter can be used only with list or leaf-list");
    }

    private void makeNormalPost(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext) {
        final Collection<? extends NormalizedNode<?, ?>> children;
        if (payload instanceof MapNode) {
            children = ((MapNode) payload).getValue();
        } else if (payload instanceof LeafSetNode) {
            children = ((LeafSetNode<?>) payload).getValue();
        } else {
            simplePostPut(rwTransaction, datastore, path, payload, schemaContext);
            return;
        }

        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
        if (children.isEmpty()) {
            if (isMounted != null && !isMounted.get()) {

                rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()),
                                    emptySubtree);
                ensureParentsByMerge(datastore, path, rwTransaction, schemaContext);
            }
            return;
        }

        // Kick off batch existence check first...
        final BatchedExistenceCheck check = BatchedExistenceCheck.start(rwTransaction, datastore, path, children);

        // ... now enqueue modifications. This relies on proper ordering of requests, i.e. these will not affect the
        // result of the existence checks...
        if (isMounted != null && !isMounted.get()) {

            rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            ensureParentsByMerge(datastore, path, rwTransaction, schemaContext);
        }
        for (final NormalizedNode<?, ?> child : children) {
            // FIXME: we really want a create(YangInstanceIdentifier, NormalizedNode) method in the transaction,
            //        as that would allow us to skip the existence checks
            rwTransaction.put(datastore, path.node(child.getIdentifier()), child);
        }

        // ... finally collect existence checks and abort the transaction if any of them failed.
        final Entry<YangInstanceIdentifier, ReadFailedException> failure;
        try {
            failure = check.getFailure();
        } catch (InterruptedException e) {
            rwTransaction.cancel();
            throw new RestconfDocumentedException("Could not determine the existence of path " + path, e);
        }

        if (failure != null) {
            rwTransaction.cancel();
            final ReadFailedException e = failure.getValue();
            if (e == null) {
                throw new RestconfDocumentedException("Data already exists for path: " + failure.getKey(),
                    ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS);
            }

            throw new RestconfDocumentedException("Could not determine the existence of path " + failure.getKey(), e,
                e.getErrorList());
        }
    }

    private void simplePostPut(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext) {
        checkItemDoesNotExists(rwTransaction, datastore, path);
        if (isMounted != null && !isMounted.get()) {
            ensureParentsByMerge(datastore, path, rwTransaction, schemaContext);
        }
        rwTransaction.put(datastore, path, payload);
    }

    private static boolean doesItemExist(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        try {
            return rwTransaction.exists(store, path).get();
        } catch (InterruptedException e) {
            rwTransaction.cancel();
            throw new RestconfDocumentedException("Could not determine the existence of path " + path, e);
        } catch (ExecutionException e) {
            rwTransaction.cancel();
            throw RestconfDocumentedException.decodeAndThrow("Could not determine the existence of path " + path,
                Throwables.getCauseAs(e, ReadFailedException.class));
        }
    }

    /**
     * Check if item already exists. Throws error if it does NOT already exist.
     * @param rwTransaction Current transaction
     * @param store Used datastore
     * @param path Path to item to verify its existence
     */
    private static void checkItemExists(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (!doesItemExist(rwTransaction, store, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            rwTransaction.cancel();
            throw new RestconfDocumentedException("Data does not exist for path: " + path, ErrorType.PROTOCOL,
                    ErrorTag.DATA_MISSING);
        }
    }

    /**
     * Check if item does NOT already exist. Throws error if it already exists.
     * @param rwTransaction Current transaction
     * @param store Used datastore
     * @param path Path to item to verify its existence
     */
    private static void checkItemDoesNotExists(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (doesItemExist(rwTransaction, store, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            rwTransaction.cancel();
            throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                    ErrorTag.DATA_EXISTS);
        }
    }

    /**
     * PUT data and submit {@link DOMDataReadWriteTransaction}.
     *
     * @param point
     *            point
     * @param insert
     *            insert
     */
    private FluentFuture<? extends CommitInfo> putDataViaTransaction(
            final DOMDataTreeReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext, final String insert, final String point) {
        LOG.trace("Put {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        putData(readWriteTransaction, datastore, path, payload, schemaContext, insert, point);
        return readWriteTransaction.commit();
    }

    /**
     * PUT data and do NOT submit {@link DOMDataReadWriteTransaction}.
     */
    private void putDataWithinTransaction(
            final DOMDataTreeReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext) {
        LOG.trace("Put {} within Restconf Patch: {} with payload {}", datastore.name(), path, payload);
        putData(writeTransaction, datastore, path, payload, schemaContext, null, null);
    }

    // FIXME: This is doing correct put for container and list children, not sure if this will work for choice case
    private void putData(final DOMDataTreeReadWriteTransaction rwTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext, final String insert, final String point) {
        if (insert == null) {
            makePut(rwTransaction, datastore, path, payload, schemaContext);
            return;
        }

        final DataSchemaNode schemaNode = checkListAndOrderedType(schemaContext, path);
        checkItemDoesNotExists(rwTransaction, datastore, path);
        switch (insert) {
            case "first":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        rwTransaction.delete(datastore, path.getParent());
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                        makePut(rwTransaction, datastore, path.getParent(), readList, schemaContext);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        rwTransaction.delete(datastore, path.getParent());
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                        makePut(rwTransaction, datastore, path.getParent(), readLeafList,
                            schemaContext);
                    }
                }
                break;
            case "last":
                simplePut(datastore, path, rwTransaction, schemaContext, payload);
                break;
            case "before":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        insertWithPointListPut(rwTransaction, datastore, path, payload, schemaContext, point,
                            readList, true);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        insertWithPointLeafListPut(rwTransaction, datastore, path, payload, schemaContext, point,
                            readLeafList, true);
                    }
                }
                break;
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        insertWithPointListPut(rwTransaction, datastore, path, payload, schemaContext, point,
                            readList, false);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePut(datastore, path, rwTransaction, schemaContext, payload);
                    } else {
                        insertWithPointLeafListPut(rwTransaction, datastore, path, payload, schemaContext, point,
                            readLeafList, false);
                    }
                }
                break;
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                            + insert);
        }
    }

    private void insertWithPointLeafListPut(final DOMDataTreeWriteTransaction tx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before) {
        tx.delete(datastore, path.getParent());
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext.toInstanceIdentifier(point);
        int index1 = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            index1++;
        }
        if (!before) {
            index1++;
        }
        int index2 = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        tx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (index2 == index1) {
                simplePut(datastore, path, tx, schemaContext, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(nodeChild.getIdentifier());
            tx.put(datastore, childPath, nodeChild);
            index2++;
        }
    }

    private void insertWithPointListPut(final DOMDataTreeWriteTransaction tx, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String point, final OrderedMapNode readList, final boolean before) {
        tx.delete(datastore, path.getParent());
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext.toInstanceIdentifier(point);
        int index1 = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            index1++;
        }
        if (!before) {
            index1++;
        }
        int index2 = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        tx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (index2 == index1) {
                simplePut(datastore, path, tx, schemaContext, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(mapEntryNode.getIdentifier());
            tx.put(datastore, childPath, mapEntryNode);
            index2++;
        }
    }

    private void makePut(final DOMDataTreeWriteTransaction tx, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        if (payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            if (isMounted != null && !isMounted.get()) {
                tx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
                ensureParentsByMerge(datastore, path, tx, schemaContext);
            }
            for (final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                tx.put(datastore, childPath, child);
            }
        } else {
            simplePut(datastore, path, tx, schemaContext, payload);
        }
    }

    private void simplePut(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final DOMDataTreeWriteTransaction tx, final SchemaContext schemaContext,
            final NormalizedNode<?, ?> payload) {
        if (isMounted != null && !isMounted.get()) {
            ensureParentsByMerge(datastore, path, tx, schemaContext);
        }
        tx.put(datastore, path, payload);
    }

    private static FluentFuture<? extends CommitInfo> deleteDataViaTransaction(
            final DOMDataTreeReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        LOG.trace("Delete {} via Restconf: {}", datastore.name(), path);
        checkItemExists(readWriteTransaction, datastore, path);
        readWriteTransaction.delete(datastore, path);
        return readWriteTransaction.commit();
    }

    private static void deleteDataWithinTransaction(final DOMDataTreeWriteTransaction tx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        LOG.trace("Delete {} within Restconf Patch: {}", datastore.name(), path);
        tx.delete(datastore, path);
    }

    private static void mergeDataWithinTransaction(final DOMDataTreeWriteTransaction tx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", datastore.name(), path, payload);
        ensureParentsByMerge(datastore, path, tx, schemaContext);

        // Since YANG Patch provides the option to specify what kind of operation for each edit,
        // OpenDaylight should not change it.
        tx.merge(datastore, path, payload);
    }

    public void registerToListenNotification(final NotificationListenerAdapter listener) {
        if (listener.isListening()) {
            return;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration = this.domNotification
                .registerNotificationListener(listener, Absolute.of(ImmutableList.copyOf(path.getPathFromRoot())));

        listener.setRegistration(registration);
    }

    private static void ensureParentsByMerge(final LogicalDatastoreType store,
            final YangInstanceIdentifier normalizedPath, final DOMDataTreeWriteTransaction tx,
            final SchemaContext schemaContext) {
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = normalizedPath.getPathArguments().iterator();

        while (it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if (rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }

            if (it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        if (normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        Preconditions.checkArgument(rootNormalizedPath != null, "Empty path received");

        final NormalizedNode<?, ?> parentStructure = ImmutableNodes.fromInstanceId(schemaContext,
                YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        tx.merge(store, rootNormalizedPath, parentStructure);
    }

    private static RestconfDocumentedException dataBrokerUnavailable(final YangInstanceIdentifier path) {
        LOG.warn("DOM data broker service is not available for mount point {}", path);
        return new RestconfDocumentedException("DOM data broker service is not available for mount point " + path);
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }

    private static final class PatchStatusContextHelper {
        PatchStatusContext status;

        public PatchStatusContext getStatus() {
            return this.status;
        }

        public void setStatus(final PatchStatusContext status) {
            this.status = status;
        }
    }
}
