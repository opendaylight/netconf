/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker.ConsumerSession;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
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
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerFacade {
    private final static Logger LOG = LoggerFactory.getLogger(BrokerFacade.class);

    private final static BrokerFacade INSTANCE = new BrokerFacade();
    private volatile DOMRpcService rpcService;
    private volatile ConsumerSession context;
    private DOMDataBroker domDataBroker;
    private DOMNotificationService domNotification;

    private BrokerFacade() {}

    public void setRpcService(final DOMRpcService router) {
        this.rpcService = router;
    }

    public void setDomNotificationService(final DOMNotificationService domNotification) {
        this.domNotification = domNotification;
    }

    public void setContext(final ConsumerSession context) {
        this.context = context;
    }

    public static BrokerFacade getInstance() {
        return BrokerFacade.INSTANCE;
    }

    private void checkPreconditions() {
        if (this.context == null || this.domDataBroker == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Read config data by path
     *
     * @param path
     *            - path of data
     * @return read date
     */
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path) {
        return readConfigurationData(path, null);
    }

    /**
     * Read config data by path
     *
     * @param path
     *            - path of data
     * @param withDefa
     *            - value of with-defaults parameter
     * @return read date
     */
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path, final String withDefa) {
        checkPreconditions();
        try (DOMDataReadOnlyTransaction tx = this.domDataBroker.newReadOnlyTransaction()) {
            return readDataViaTransaction(tx, CONFIGURATION, path, withDefa);
        }
    }

    /**
     * Read config data from mount point by path.
     *
     * @param mountPoint
     *            - mount point for reading data
     * @param path
     *            - path of data
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
     *            - mount point for reading data
     * @param path
     *            - path of data
     * @param withDefa
     *            - value of with-defaults parameter
     * @return read data
     */
    public NormalizedNode<?, ?> readConfigurationData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path,
            final String withDefa) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            try (DOMDataReadOnlyTransaction tx = domDataBrokerService.get().newReadOnlyTransaction()) {
                return readDataViaTransaction(tx, CONFIGURATION, path, withDefa);
            }
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    /**
     * Read operational data by path.
     *
     * @param path
     *            - path of data
     * @return read data
     */
    public NormalizedNode<?, ?> readOperationalData(final YangInstanceIdentifier path) {
        checkPreconditions();

        try (DOMDataReadOnlyTransaction tx = this.domDataBroker.newReadOnlyTransaction()) {
            return readDataViaTransaction(tx, OPERATIONAL, path);
        }
    }

    /**
     * Read operational data from mount point by path.
     *
     * @param mountPoint
     *            - mount point for reading data
     * @param path
     *            - path of data
     * @return read data
     */
    public NormalizedNode<?, ?> readOperationalData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            try (DOMDataReadOnlyTransaction tx = domDataBrokerService.get().newReadOnlyTransaction()) {
                return readDataViaTransaction(tx, OPERATIONAL, path);
            }
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    /**
     * <b>PUT configuration data</b>
     *
     * Prepare result(status) for PUT operation and PUT data via transaction.
     * Return wrapped status and future from PUT.
     *
     * @param globalSchema
     *            - used by merge parents (if contains list)
     * @param path
     *            - path of node
     * @param payload
     *            - input data
     * @param point
     * @param insert
     * @return wrapper of status and future of PUT
     */
    public PutResult commitConfigurationDataPut(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        Preconditions.checkNotNull(globalSchema);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        checkPreconditions();

        final DOMDataReadWriteTransaction newReadWriteTransaction = this.domDataBroker.newReadWriteTransaction();
        final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null ? Status.OK
                : Status.CREATED;
        final CheckedFuture<Void, TransactionCommitFailedException> future = putDataViaTransaction(
                newReadWriteTransaction, CONFIGURATION, path, payload, globalSchema, insert, point);
        return new PutResult(status, future);
    }

    /**
     * <b>PUT configuration data (Mount point)</b>
     *
     * Prepare result(status) for PUT operation and PUT data via transaction.
     * Return wrapped status and future from PUT.
     *
     * @param mountPoint
     *            - mount point for getting transaction for operation and schema
     *            context for merging parents(if contains list)
     * @param path
     *            - path of node
     * @param payload
     *            - input data
     * @param point
     * @param insert
     * @return wrapper of status and future of PUT
     */
    public PutResult commitMountPointDataPut(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        Preconditions.checkNotNull(mountPoint);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            final DOMDataReadWriteTransaction newReadWriteTransaction = domDataBrokerService.get().newReadWriteTransaction();
            final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null
                    ? Status.OK : Status.CREATED;
            final CheckedFuture<Void, TransactionCommitFailedException> future = putDataViaTransaction(
                    newReadWriteTransaction, CONFIGURATION, path, payload, mountPoint.getSchemaContext(), insert,
                    point);
            return new PutResult(status, future);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    public PATCHStatusContext patchConfigurationDataWithinTransaction(final PATCHContext patchContext) throws Exception {
        final DOMMountPoint mountPoint = patchContext.getInstanceIdentifierContext().getMountPoint();

        // get new transaction and schema context on server or on mounted device
        final SchemaContext schemaContext;
        final DOMDataReadWriteTransaction patchTransaction;
        if (mountPoint == null) {
            schemaContext = patchContext.getInstanceIdentifierContext().getSchemaContext();
            patchTransaction = this.domDataBroker.newReadWriteTransaction();
        } else {
            schemaContext = mountPoint.getSchemaContext();

            final Optional<DOMDataBroker> optional = mountPoint.getService(DOMDataBroker.class);

            if (optional.isPresent()) {
                patchTransaction = optional.get().newReadWriteTransaction();
            } else {
                // if mount point does not have broker it is not possible to continue and global error is reported
                LOG.error("Http PATCH {} has failed - device {} does not support broker service",
                        patchContext.getPatchId(), mountPoint.getIdentifier());
                return new PATCHStatusContext(
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

        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        List<RestconfError> editErrors;
        boolean withoutError = true;

        for (final PATCHEntity patchEntity : patchContext.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation().toUpperCase());

            switch (operation) {
                case CREATE:
                    if (withoutError) {
                        try {
                            postDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), schemaContext);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http PATCH operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case REPLACE:
                    if (withoutError) {
                        try {
                            putDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode(), patchEntity.getNode(), schemaContext);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http PATCH operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case DELETE:
                    if (withoutError) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http PATCH operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case REMOVE:
                    if (withoutError) {
                        try {
                            removeDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http PATCH operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                case MERGE:
                    if (withoutError) {
                        try {
                            mergeDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), schemaContext);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error call http PATCH operation {} on target {}",
                                    operation,
                                    patchEntity.getTargetNode().toString());

                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            withoutError = false;
                        }
                    }
                    break;
                default:
                    LOG.error("Unsupported http PATCH operation {} on target {}",
                            operation,
                            patchEntity.getTargetNode().toString());
                    break;
            }
        }

        // if errors then cancel transaction and return error status
        if (!withoutError) {
            patchTransaction.cancel();
            return new PATCHStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection), false, null);
        }

        // if no errors commit transaction
        final CountDownLatch waiter = new CountDownLatch(1);
        final CheckedFuture<Void, TransactionCommitFailedException> future = patchTransaction.submit();
        final PATCHStatusContextHelper status = new PATCHStatusContextHelper();

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                status.setStatus(new PATCHStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection),
                        true, null));
                waiter.countDown();
            }

            @Override
            public void onFailure(final Throwable t) {
                // if commit failed it is global error
                LOG.error("Http PATCH {} transaction commit has failed", patchContext.getPatchId());
                status.setStatus(new PATCHStatusContext(patchContext.getPatchId(), ImmutableList.copyOf(editCollection),
                        false, ImmutableList.of(
                        new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, t.getMessage()))));
                waiter.countDown();
            }
        });

        waiter.await();
        return status.getStatus();
    }

    // POST configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        checkPreconditions();
        return postDataViaTransaction(this.domDataBroker.newReadWriteTransaction(), CONFIGURATION, path, payload,
                globalSchema, insert, point);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final String insert, final String point) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return postDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path,
                    payload, mountPoint.getSchemaContext(), insert, point);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // DELETE configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataDelete(
            final YangInstanceIdentifier path) {
        checkPreconditions();
        return deleteDataViaTransaction(this.domDataBroker.newReadWriteTransaction(), CONFIGURATION, path);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataDelete(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return deleteDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // RPC
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(final SchemaPath type, final NormalizedNode<?, ?> input) {
        checkPreconditions();
        if (this.rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
        LOG.trace("Invoke RPC {} with input: {}", type, input);
        return this.rpcService.invokeRpc(type, input);
    }

    public void registerToListenDataChanges(final LogicalDatastoreType datastore, final DataChangeScope scope,
            final ListenerAdapter listener) {
        checkPreconditions();

        if (listener.isListening()) {
            return;
        }

        final YangInstanceIdentifier path = listener.getPath();
        final ListenerRegistration<DOMDataChangeListener> registration = this.domDataBroker.registerDataChangeListener(
                datastore, path, listener, scope);

        listener.setRegistration(registration);
    }

    private NormalizedNode<?, ?> readDataViaTransaction(final DOMDataReadTransaction transaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        return readDataViaTransaction(transaction, datastore, path, null);
    }

    private NormalizedNode<?, ?> readDataViaTransaction(final DOMDataReadTransaction transaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final String withDefa) {
        LOG.trace("Read {} via Restconf: {}", datastore.name(), path);

        try {
            final Optional<NormalizedNode<?, ?>> optional = transaction.read(datastore, path).checkedGet();
            return !optional.isPresent() ? null : withDefa == null ? optional.get() :
                prepareDataByParamWithDef(optional.get(), path, withDefa);
        } catch (final ReadFailedException e) {
            LOG.warn("Error reading {} from datastore {}", path, datastore.name(), e);
            throw new RestconfDocumentedException("Error reading data.", e, e.getErrorList());
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

        final SchemaContext ctx = ControllerContext.getInstance().getGlobalSchema();
        final DataSchemaContextTree baseSchemaCtxTree = DataSchemaContextTree.from(ctx);
        final DataSchemaNode baseSchemaNode = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
        if (result instanceof ContainerNode) {
            final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder =
                    Builders.containerBuilder((ContainerSchemaNode) baseSchemaNode);
            buildCont(builder, (ContainerNode) result, baseSchemaCtxTree, path, trim);
            return builder.build();
        }

        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder =
                Builders.mapEntryBuilder((ListSchemaNode) baseSchemaNode);
        buildMapEntryBuilder(builder, (MapEntryNode) result, baseSchemaCtxTree, path, trim,
            ((ListSchemaNode) baseSchemaNode).getKeyDefinition());
        return builder.build();
    }

    private void buildMapEntryBuilder(final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim, final List<QName> keys) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> childBuilder =
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
                final String defaultVal = ((LeafSchemaNode) childSchema).getDefault();
                final String nodeVal = ((LeafNode<String>) child).getValue();
                final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (keys.contains(child.getNodeType())) {
                    leafBuilder.withValue(((LeafNode) child).getValue());
                    builder.withChild(leafBuilder.build());
                } else {
                    if (trim) {
                        if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode) child).getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    } else {
                        if (defaultVal != null && defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode) child).getValue());
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
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(actualNode).getDataSchemaNode();
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                    Builders.mapEntryBuilder((ListSchemaNode) childSchema);
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, baseSchemaCtxTree, actualNode, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private void buildCont(final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if(child instanceof ContainerNode){
                final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builderChild =
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
                final String defaultVal = ((LeafSchemaNode) childSchema).getDefault();
                final String nodeVal = ((LeafNode<String>) child).getValue();
                final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (trim) {
                    if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                } else {
                    if (defaultVal != null && defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                }
            }
        }
    }

    /**
     * POST data and submit transaction {@link DOMDataReadWriteTransaction}
     */
    private CheckedFuture<Void, TransactionCommitFailedException> postDataViaTransaction(
            final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String insert, final String point) {
        LOG.trace("POST {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        postData(rWTransaction, datastore, path, payload, schemaContext, insert, point);
        return rWTransaction.submit();
    }

    /**
     * POST data and do NOT submit transaction {@link DOMDataReadWriteTransaction}
     */
    private void postDataWithinTransaction(
            final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("POST {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        postData(rWTransaction, datastore, path, payload, schemaContext, null, null);
    }

    private void postData(final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
                          final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String insert, final String point) {
        if (insert == null) {
            makeNormalPost(rWTransaction, datastore, path, payload, schemaContext);
            return;
        }

        final DataSchemaNode schemaNode = checkListAndOrderedType(schemaContext, path);
        checkItemDoesNotExists(rWTransaction, datastore, path);
        switch (insert) {
            case "first":
                if(schemaNode instanceof ListSchemaNode){
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        rWTransaction.delete(datastore, path.getParent().getParent());
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                        makeNormalPost(rWTransaction, datastore, path.getParent().getParent(), readList,
                            schemaContext);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        rWTransaction.delete(datastore, path.getParent());
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                        makeNormalPost(rWTransaction, datastore, path.getParent().getParent(), readLeafList,
                            schemaContext);
                    }
                }
                break;
            case "last":
                simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                break;
            case "before":
                if(schemaNode instanceof ListSchemaNode){
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointListPost(rWTransaction, datastore, path, payload, schemaContext, point,
                            readList,
                            true);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointLeafListPost(rWTransaction, datastore, path, payload, schemaContext, point,
                            readLeafList, true);
                    }
                }
                break;
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final OrderedMapNode readList =
                            (OrderedMapNode) this.readConfigurationData(path.getParent().getParent());
                    if (readList == null || readList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointListPost(rWTransaction, datastore, path, payload, schemaContext, point,
                            readList,
                            false);
                    }
                } else {
                    final OrderedLeafSetNode<?> readLeafList =
                            (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
                    } else {
                        insertWithPointLeafListPost(rWTransaction, datastore, path, payload, schemaContext, point,
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

    private static void insertWithPointLeafListPost(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before) {
        rWTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ControllerContext.getInstance().toInstanceIdentifier(point);
        int p = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            p++;
        }
        if (!before) {
            p++;
        }
        int h = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (h == p) {
                checkItemDoesNotExists(rWTransaction, datastore, path);
                simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            checkItemDoesNotExists(rWTransaction, datastore, childPath);
            rWTransaction.put(datastore, childPath, nodeChild);
            h++;
        }
    }

    private static void insertWithPointListPost(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String point, final MapNode readList, final boolean before) {
        rWTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ControllerContext.getInstance().toInstanceIdentifier(point);
        int p = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier()
                    .equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            p++;
        }
        if (!before) {
            p++;
        }
        int h = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (h == p) {
                checkItemDoesNotExists(rWTransaction, datastore, path);
                simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            checkItemDoesNotExists(rWTransaction, datastore, childPath);
            rWTransaction.put(datastore, childPath, mapEntryNode);
            h++;
        }
    }

    private DataSchemaNode checkListAndOrderedType(final SchemaContext ctx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier parent = path.getParent();
        final DataSchemaContextNode<?> node = DataSchemaContextTree.from(ctx).getChild(parent);
        final DataSchemaNode dataSchemaNode = node.getDataSchemaNode();

        if (dataSchemaNode instanceof ListSchemaNode) {
            if(!((ListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException("Insert parameter can be used only with ordered-by user list.");
            }
            return dataSchemaNode;
        }
        if (dataSchemaNode instanceof LeafListSchemaNode) {
            if(!((LeafListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException("Insert parameter can be used only with ordered-by user leaf-list.");
            }
            return dataSchemaNode;
        }
        throw new RestconfDocumentedException("Insert parameter can be used only with list or leaf-list");
    }

    private static void makeNormalPost(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext) {
        final Collection<? extends NormalizedNode<?, ?>> children;
        if (payload instanceof MapNode) {
            children = ((MapNode) payload).getValue();
        } else if (payload instanceof LeafSetNode) {
            children = ((LeafSetNode<?>) payload).getValue();
        } else {
            simplePostPut(rWTransaction, datastore, path, payload, schemaContext);
            return;
        }

        // We are putting multiple children, we really need a create() operation, but until we have that we make do
        // with a two-step process of verifying if the children exist and then putting them in.
        for (final NormalizedNode<?, ?> child : children) {
            checkItemDoesNotExists(rWTransaction, datastore, path.node(child.getIdentifier()));
        }

        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
        rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        ensureParentsByMerge(datastore, path, rWTransaction, schemaContext);
        for (final NormalizedNode<?, ?> child : children) {
            rWTransaction.put(datastore, path.node(child.getIdentifier()), child);
        }
    }

    private static void simplePostPut(final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        checkItemDoesNotExists(rWTransaction, datastore, path);
        ensureParentsByMerge(datastore, path, rWTransaction, schemaContext);
        rWTransaction.put(datastore, path, payload);
    }

    private static boolean doesItemExist(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        try {
            return rWTransaction.exists(store, path).checkedGet();
        } catch (final ReadFailedException e) {
            rWTransaction.cancel();
            throw new RestconfDocumentedException("Could not determine the existence of path " + path,
                    e, e.getErrorList());
        }
    }

    /**
     * Check if item already exists. Throws error if it does NOT already exist.
     * @param rWTransaction Current transaction
     * @param store Used datastore
     * @param path Path to item to verify its existence
     */
    private void checkItemExists(final DOMDataReadWriteTransaction rWTransaction,
                                 final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (!doesItemExist(rWTransaction, store, path)) {
            final String errMsg = "Operation via Restconf was not executed because data does not exist";
            LOG.trace("{}:{}", errMsg, path);
            rWTransaction.cancel();
            throw new RestconfDocumentedException("Data does not exist for path: " + path, ErrorType.PROTOCOL,
                    ErrorTag.DATA_MISSING);
        }
    }

    /**
     * Check if item does NOT already exist. Throws error if it already exists.
     * @param rWTransaction Current transaction
     * @param store Used datastore
     * @param path Path to item to verify its existence
     */
    private static void checkItemDoesNotExists(final DOMDataReadWriteTransaction rWTransaction,
                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        if (doesItemExist(rWTransaction, store, path)) {
            final String errMsg = "Operation via Restconf was not executed because data already exists";
            LOG.trace("{}:{}", errMsg, path);
            rWTransaction.cancel();
            throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                    ErrorTag.DATA_EXISTS);
        }
    }

    /**
     * PUT data and submit {@link DOMDataReadWriteTransaction}
     *
     * @param point
     * @param insert
     */
    private CheckedFuture<Void, TransactionCommitFailedException> putDataViaTransaction(
            final DOMDataReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String insert, final String point) {
        LOG.trace("Put {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        putData(readWriteTransaction, datastore, path, payload, schemaContext, insert, point);
        return readWriteTransaction.submit();
    }

    /**
     * PUT data and do NOT submit {@link DOMDataReadWriteTransaction}
     *
     * @param insert
     * @param point
     */
    private void putDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Put {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        putData(writeTransaction, datastore, path, payload, schemaContext, null, null);
    }

    // FIXME: This is doing correct put for container and list children, not sure if this will work for choice case
    private void putData(final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
            final String insert, final String point) {
        if (insert == null) {
            makePut(rWTransaction, datastore, path, payload, schemaContext);
        } else {
            final DataSchemaNode schemaNode = checkListAndOrderedType(schemaContext, path);
            checkItemDoesNotExists(rWTransaction, datastore, path);
            switch (insert) {
                case "first":
                    if (schemaNode instanceof ListSchemaNode) {
                        final OrderedMapNode readList =
                                (OrderedMapNode) this.readConfigurationData(path.getParent());
                        if (readList == null || readList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            rWTransaction.delete(datastore, path.getParent());
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                            makePut(rWTransaction, datastore, path.getParent(), readList, schemaContext);
                        }
                    } else {
                        final OrderedLeafSetNode<?> readLeafList =
                                (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                        if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            rWTransaction.delete(datastore, path.getParent());
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                            makePut(rWTransaction, datastore, path.getParent(), readLeafList,
                                    schemaContext);
                        }
                    }
                    break;
                case "last":
                    simplePut(datastore, path, rWTransaction, schemaContext, payload);
                    break;
                case "before":
                    if (schemaNode instanceof ListSchemaNode) {
                        final OrderedMapNode readList =
                                (OrderedMapNode) this.readConfigurationData(path.getParent());
                        if (readList == null || readList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            insertWithPointListPut(rWTransaction, datastore, path, payload, schemaContext, point,
                                    readList, true);
                        }
                    } else {
                        final OrderedLeafSetNode<?> readLeafList =
                                (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                        if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            insertWithPointLeafListPut(rWTransaction, datastore, path, payload, schemaContext, point,
                                    readLeafList, true);
                        }
                    }
                    break;
                case "after":
                    if (schemaNode instanceof ListSchemaNode) {
                        final OrderedMapNode readList =
                                (OrderedMapNode) this.readConfigurationData(path.getParent());
                        if (readList == null || readList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            insertWithPointListPut(rWTransaction, datastore, path, payload, schemaContext, point,
                                    readList, false);
                        }
                    } else {
                        final OrderedLeafSetNode<?> readLeafList =
                                (OrderedLeafSetNode<?>) readConfigurationData(path.getParent());
                        if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                            simplePut(datastore, path, rWTransaction, schemaContext, payload);
                        } else {
                            insertWithPointLeafListPut(rWTransaction, datastore, path, payload, schemaContext, point,
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
    }

    private static void insertWithPointLeafListPut(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before) {
        rWTransaction.delete(datastore, path.getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ControllerContext.getInstance().toInstanceIdentifier(point);
        int p = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            p++;
        }
        if (!before) {
            p++;
        }
        int h = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (h == p) {
                simplePut(datastore, path, rWTransaction, schemaContext, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(nodeChild.getIdentifier());
            rWTransaction.put(datastore, childPath, nodeChild);
            h++;
        }
    }

    private static void insertWithPointListPut(final DOMDataReadWriteTransaction rWTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedMapNode readList,
            final boolean before) {
        rWTransaction.delete(datastore, path.getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ControllerContext.getInstance().toInstanceIdentifier(point);
        int p = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            p++;
        }
        if (!before) {
            p++;
        }
        int h = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (h == p) {
                simplePut(datastore, path, rWTransaction, schemaContext, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(mapEntryNode.getIdentifier());
            rWTransaction.put(datastore, childPath, mapEntryNode);
            h++;
        }
    }

    private void makePut(final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        if (payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            writeTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            ensureParentsByMerge(datastore, path, writeTransaction, schemaContext);
            for (final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                writeTransaction.put(datastore, childPath, child);
            }
        } else {
            simplePut(datastore, path, writeTransaction, schemaContext, payload);
        }
    }

    private static void simplePut(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final DOMDataReadWriteTransaction writeTransaction, final SchemaContext schemaContext,
            final NormalizedNode<?, ?> payload) {
        ensureParentsByMerge(datastore, path, writeTransaction, schemaContext);
        writeTransaction.put(datastore, path, payload);
    }

    private CheckedFuture<Void, TransactionCommitFailedException> deleteDataViaTransaction(
            final DOMDataReadWriteTransaction readWriteTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        LOG.trace("Delete {} via Restconf: {}", datastore.name(), path);
        checkItemExists(readWriteTransaction, datastore, path);
        readWriteTransaction.delete(datastore, path);
        return readWriteTransaction.submit();
    }

    private void deleteDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        checkItemExists(writeTransaction, datastore, path);
        removeDataWithinTransaction(writeTransaction, datastore, path);
    }

    private void removeDataWithinTransaction(final DOMDataWriteTransaction writeTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        LOG.trace("Delete {} within Restconf PATCH: {}", datastore.name(), path);
        writeTransaction.delete(datastore, path);
    }

    private void mergeDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Merge {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        ensureParentsByMerge(datastore, path, writeTransaction, schemaContext);

        // Since YANG Patch provides the option to specify what kind of operation for each edit,
        // OpenDaylight should not change it.
        writeTransaction.merge(datastore, path, payload);
    }

    public void setDomDataBroker(final DOMDataBroker domDataBroker) {
        this.domDataBroker = domDataBroker;
    }

    public void registerToListenNotification(final NotificationListenerAdapter listener) {
        checkPreconditions();

        if (listener.isListening()) {
            return;
        }

        final SchemaPath path = listener.getSchemaPath();
        final ListenerRegistration<DOMNotificationListener> registration = this.domNotification
                .registerNotificationListener(listener, path);

        listener.setRegistration(registration);
    }

    private final class PATCHStatusContextHelper {
        PATCHStatusContext status;

        public PATCHStatusContext getStatus() {
            return this.status;
        }

        public void setStatus(final PATCHStatusContext status) {
            this.status = status;
        }
    }

    private static void ensureParentsByMerge(final LogicalDatastoreType store, final YangInstanceIdentifier normalizedPath,
            final DOMDataReadWriteTransaction rwTx, final SchemaContext schemaContext) {
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
        rwTx.merge(store, rootNormalizedPath, parentStructure);
    }
}
