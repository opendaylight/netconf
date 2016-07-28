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
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
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
import org.opendaylight.restconf.restful.utils.TransactionUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
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
        if ((this.context == null) || (this.domDataBroker == null)) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
    }

    // READ configuration
    public NormalizedNode<?, ?> readConfigurationData(final YangInstanceIdentifier path) {
        checkPreconditions();
        return readDataViaTransaction(this.domDataBroker.newReadOnlyTransaction(), CONFIGURATION, path);
    }

    public NormalizedNode<?, ?> readConfigurationData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return readDataViaTransaction(domDataBrokerService.get().newReadOnlyTransaction(), CONFIGURATION, path);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    // READ operational
    public NormalizedNode<?, ?> readOperationalData(final YangInstanceIdentifier path) {
        checkPreconditions();
        return readDataViaTransaction(this.domDataBroker.newReadOnlyTransaction(), OPERATIONAL, path);
    }

    public NormalizedNode<?, ?> readOperationalData(final DOMMountPoint mountPoint, final YangInstanceIdentifier path) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return readDataViaTransaction(domDataBrokerService.get().newReadOnlyTransaction(), OPERATIONAL, path);
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
     * @return wrapper of status and future of PUT
     */
    public PutResult commitConfigurationDataPut(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        Preconditions.checkNotNull(globalSchema);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        checkPreconditions();

        final DOMDataReadWriteTransaction newReadWriteTransaction = this.domDataBroker.newReadWriteTransaction();
        final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null ? Status.OK
                : Status.CREATED;
        final CheckedFuture<Void, TransactionCommitFailedException> future = putDataViaTransaction(
                newReadWriteTransaction, CONFIGURATION, path, payload, globalSchema);
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
     * @return wrapper of status and future of PUT
     */
    public PutResult commitMountPointDataPut(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        Preconditions.checkNotNull(mountPoint);
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(payload);

        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            final DOMDataReadWriteTransaction newReadWriteTransaction = domDataBrokerService.get().newReadWriteTransaction();
            final Status status = readDataViaTransaction(newReadWriteTransaction, CONFIGURATION, path) != null
                    ? Status.OK : Status.CREATED;
            final CheckedFuture<Void, TransactionCommitFailedException> future = putDataViaTransaction(
                    newReadWriteTransaction, CONFIGURATION, path,
                    payload, mountPoint.getSchemaContext());
            return new PutResult(status, future);
        }
        final String errMsg = "DOM data broker service isn't available for mount point " + path;
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    public PATCHStatusContext patchConfigurationDataWithinTransaction(final PATCHContext context,
                                                                      final SchemaContext globalSchema)
            throws InterruptedException {
        final DOMDataReadWriteTransaction patchTransaction = this.domDataBroker.newReadWriteTransaction();
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();

        List<RestconfError> editErrors;
        int errorCounter = 0;

        for (final PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation().toUpperCase());

            switch (operation) {
                case CREATE:
                    if (errorCounter == 0) {
                        try {
                            postDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case REPLACE:
                    if (errorCounter == 0) {
                        try {
                            putDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode(), patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case DELETE:
                    if (errorCounter == 0) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
                case REMOVE:
                    if (errorCounter == 0) {
                        try {
                            deleteDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity
                                    .getTargetNode());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            LOG.error("Error removing {} by {} operation", patchEntity.getTargetNode().toString(),
                                    patchEntity.getEditId(), e);
                        }
                    }
                    break;
                case MERGE:
                    if (errorCounter == 0) {
                        try {
                            mergeDataWithinTransaction(patchTransaction, CONFIGURATION, patchEntity.getTargetNode(),
                                    patchEntity.getNode(), globalSchema);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editErrors = new ArrayList<>();
                            editErrors.addAll(e.getErrors());
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), false, editErrors));
                            errorCounter++;
                        }
                    }
                    break;
            }
        }

        // if errors then cancel transaction and return error status
        if (errorCounter != 0) {
            patchTransaction.cancel();
            return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false, null);
        }

        // if no errors commit transaction
        final CountDownLatch waiter = new CountDownLatch(1);
        final CheckedFuture<Void, TransactionCommitFailedException> future = patchTransaction.submit();
        final PATCHStatusContextHelper status = new PATCHStatusContextHelper();

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                status.setStatus(new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                        true, null));
                waiter.countDown();
            }

            @Override
            public void onFailure(final Throwable t) {
                // if commit failed it is global error
                status.setStatus(new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                        false, Lists.newArrayList(
                        new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, t.getMessage()))));
                waiter.countDown();
            }
        });

        waiter.await();
        return status.getStatus();
    }

    // POST configuration
    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final SchemaContext globalSchema, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        checkPreconditions();
        return postDataViaTransaction(this.domDataBroker.newReadWriteTransaction(), CONFIGURATION, path, payload, globalSchema);
    }

    public CheckedFuture<Void, TransactionCommitFailedException> commitConfigurationDataPost(
            final DOMMountPoint mountPoint, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return postDataViaTransaction(domDataBrokerService.get().newReadWriteTransaction(), CONFIGURATION, path,
                    payload, mountPoint.getSchemaContext());
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
        LOG.trace("Read {} via Restconf: {}", datastore.name(), path);
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> listenableFuture = transaction.read(datastore, path);
        final ReadDataResult<NormalizedNode<?, ?>> readData = new ReadDataResult<>();
        final CountDownLatch responseWaiter = new CountDownLatch(1);

        Futures.addCallback(listenableFuture, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {

            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                responseWaiter.countDown();
                handlingCallback(null, datastore, path, result, readData);
            }

            @Override
            public void onFailure(final Throwable t) {
                responseWaiter.countDown();
                handlingCallback(t, datastore, path, null, null);
            }
        });

        try {
            responseWaiter.await();
        } catch (final InterruptedException e) {
            final String msg = "Problem while waiting for response";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg, e);
        }
        return readData.getResult();
    }

    private CheckedFuture<Void, TransactionCommitFailedException> postDataViaTransaction(
            final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        // FIXME: This is doing correct post for container and list children
        //        not sure if this will work for choice case
        if(payload instanceof MapNode) {
            LOG.trace("POST {} via Restconf: {} with payload {}", datastore.name(), path, payload);
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                checkItemDoesNotExists(rWTransaction, datastore, childPath);
                rWTransaction.put(datastore, childPath, child);
            }
        } else {
            checkItemDoesNotExists(rWTransaction,datastore, path);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            rWTransaction.put(datastore, path, payload);
        }
        return rWTransaction.submit();
    }

    private void postDataWithinTransaction(
            final DOMDataReadWriteTransaction rWTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        // FIXME: This is doing correct post for container and list children
        //        not sure if this will work for choice case
        if(payload instanceof MapNode) {
            LOG.trace("POST {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                checkItemDoesNotExists(rWTransaction, datastore, childPath);
                rWTransaction.put(datastore, childPath, child);
            }
        } else {
            checkItemDoesNotExists(rWTransaction,datastore, path);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            rWTransaction.put(datastore, path, payload);
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
        final CountDownLatch responseWaiter = new CountDownLatch(1);
        final ReadDataResult<Boolean> readData = new ReadDataResult<>();
        final CheckedFuture<Boolean, ReadFailedException> future = rWTransaction.exists(store, path);

        Futures.addCallback(future, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(@Nullable final Boolean result) {
                responseWaiter.countDown();
                handlingCallback(null, store, path, Optional.of(result), readData);
            }

            @Override
            public void onFailure(final Throwable t) {
                responseWaiter.countDown();
                handlingCallback(t, store, path, null, null);
            }
        });

        try {
            responseWaiter.await();
        } catch (final InterruptedException e) {
            final String msg = "Problem while waiting for response";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg, e);
        }

        if (!readData.getResult()) {
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
    private void checkItemDoesNotExists(final DOMDataReadWriteTransaction rWTransaction,
                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final CountDownLatch responseWaiter = new CountDownLatch(1);
        final ReadDataResult<Boolean> readData = new ReadDataResult<>();
        final CheckedFuture<Boolean, ReadFailedException> future = rWTransaction.exists(store, path);

        Futures.addCallback(future, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(@Nullable final Boolean result) {
                responseWaiter.countDown();
                handlingCallback(null, store, path, Optional.of(result), readData);
            }

            @Override
            public void onFailure(final Throwable t) {
                responseWaiter.countDown();
                handlingCallback(t, store, path, null, null);
            }
        });

        try {
            responseWaiter.await();
        } catch (final InterruptedException e) {
            final String msg = "Problem while waiting for response";
            LOG.warn(msg);
            throw new RestconfDocumentedException(msg, e);
        }

        if (readData.getResult()) {
            final String errMsg = "Operation via Restconf was not executed because data already exists";
            LOG.trace("{}:{}", errMsg, path);
            rWTransaction.cancel();
            throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                    ErrorTag.DATA_EXISTS);
        }
    }

    private CheckedFuture<Void, TransactionCommitFailedException> putDataViaTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Put {} via Restconf: {} with payload {}", datastore.name(), path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTransaction);
        writeTransaction.put(datastore, path, payload);
        return writeTransaction.submit();
    }

    private void putDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Put {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTransaction);
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
            final DOMDataWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path) {
        LOG.trace("Delete {} within Restconf PATCH: {}", datastore.name(), path);
        writeTransaction.delete(datastore, path);
    }

    private void mergeDataWithinTransaction(
            final DOMDataReadWriteTransaction writeTransaction, final LogicalDatastoreType datastore,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload, final SchemaContext schemaContext) {
        LOG.trace("Merge {} within Restconf PATCH: {} with payload {}", datastore.name(), path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTransaction);

        // merging is necessary only for lists otherwise we can call put method
        if (payload instanceof MapNode) {
            writeTransaction.merge(datastore, path, payload);
        } else {
            writeTransaction.put(datastore, path, payload);
        }
    }

    public void setDomDataBroker(final DOMDataBroker domDataBroker) {
        this.domDataBroker = domDataBroker;
    }

    /**
     * Helper class for result of transaction commit callback.
     * @param <T> Type of result
     */
    private final class ReadDataResult<T> {
        T result = null;

        T getResult() {
            return this.result;
        }

        void setResult(final T result) {
            this.result = result;
        }
    }

    /**
     * Set result from transaction commit callback.
     * @param t Throwable if transaction commit failed
     * @param datastore Datastore from which data are read
     * @param path Path from which data are read
     * @param result Result of read from {@code datastore}
     * @param readData Result value which will be set
     * @param <X> Result type
     */
    protected final static <X> void handlingCallback(final Throwable t, final LogicalDatastoreType datastore,
                                                     final YangInstanceIdentifier path, final Optional<X> result,
                                                     final ReadDataResult<X> readData) {
        if (t != null) {
            LOG.warn("Exception by reading {} via Restconf: {}", datastore.name(), path, t);
            throw new RestconfDocumentedException("Problem to get data from transaction.", t);
        } else {
            LOG.debug("Reading result data from transaction.");
            if (result != null) {
                if (result.isPresent()) {
                    readData.setResult(result.get());
                }
            }
        }
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

        public void setStatus(PATCHStatusContext status) {
            this.status = status;
        }
    }
}
