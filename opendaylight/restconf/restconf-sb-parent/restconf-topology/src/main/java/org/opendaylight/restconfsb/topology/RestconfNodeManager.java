/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceId;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RestconfNodeManager manages node device connector and node state in operational data store.
 */
class RestconfNodeManager implements ConnectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfNodeManager.class);

    private final Node configNode;
    private final DOMMountPointService mountPointService;
    private final SenderFactory senderFactory;
    private final ThreadPool processingExecutor;
    private final ScheduledThreadPool reconnectExecutor;
    private final BindingTransactionChain transactionChain;

    private RestconfDeviceManager deviceManager;
    private List<Module> modules = new ArrayList<>();

    private static final TransactionChainListener listener = new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> transactionChain,
                                             final AsyncTransaction<?, ?> asyncTransaction,
                                             final Throwable throwable) {
            LOG.warn("Transaction failed");
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> transactionChain) {
            LOG.info("Transaction successful");
        }
    };

    private final FutureCallback<Void> callback = new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable final Void result) {
            LOG.info("Node {} state update successful", configNode.getNodeId().getValue());
        }

        @Override
        public void onFailure(final Throwable t) {
            LOG.error("Node {} state update failed", configNode.getNodeId().getValue());
            LOG.error("Reason:", t);
        }
    };

    RestconfNodeManager(final Node configNode, final DOMMountPointService mountPointService,
                        final DataBroker dataBroker, final SenderFactory senderFactory, final ThreadPool processingExecutor,
                        final ScheduledThreadPool reconnectExecutor) {
        this.transactionChain = dataBroker.createTransactionChain(listener);
        this.configNode = configNode;
        this.mountPointService = mountPointService;
        this.senderFactory = senderFactory;
        this.processingExecutor = processingExecutor;
        this.reconnectExecutor = reconnectExecutor;
    }

    /**
     * Creates connector to device specified by parameters and register its mount point.
     *
     * @return supported yang modules list
     */
    ListenableFuture<List<Module>> connect() {
        LOG.info("Connecting RemoteDevice{{}} , with config {}", configNode.getNodeId(), configNode);
        deviceManager = new RestconfDeviceManager(configNode, senderFactory, processingExecutor,
                reconnectExecutor, mountPointService);
        final ListenableFuture<List<Module>> connect = deviceManager.connect(this);
        Futures.addCallback(connect, new FutureCallback<List<Module>>() {
            @Override
            public void onSuccess(@Nullable final List<Module> result) {
                synchronized (RestconfNodeManager.this) {
                    modules = result;
                    setStatusConnected();
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("{} Restconf connector initialization failed: ", configNode.getNodeId().getValue(), t);
                setStatusFailed();
            }
        });
        return connect;
    }

    /**
     * Deregisters mount point and closes device connector.
     *
     * @return future
     */
    ListenableFuture<Void> disconnect() {
        deviceManager.disconnect();
        return deleteStatus(configNode.getNodeId());
    }

    @Override
    public synchronized void onConnectionReestablished() {
        setStatusConnected();
    }

    @Override
    public synchronized void onConnectionFailed(final Throwable t) {
        setStatusFailed();
    }

    private synchronized CheckedFuture<Void, TransactionCommitFailedException> deleteStatus(final NodeId node) {
        final RestconfDeviceId id = new RestconfDeviceId(node.getValue());
        final WriteTransaction writeTransaction = transactionChain.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.OPERATIONAL, id.getBindingTopologyPath());
        return writeTransaction.submit();
    }

    private void setStatusConnected() {
        final RestconfNode restconfNodeStatus = new RestconfNodeBuilder()
                .setStatus(NodeStatus.Status.Connected)
                .setModule(modules)
                .build();
        updateStatus(restconfNodeStatus);
    }

    private void setStatusFailed() {
        final RestconfNode restconfNodeStatus = new RestconfNodeBuilder()
                .setStatus(NodeStatus.Status.Failed)
                .setModule(modules)
                .build();
        updateStatus(restconfNodeStatus);
    }

    private synchronized void updateStatus(final RestconfNode status) {
        final RestconfDeviceId deviceId = new RestconfDeviceId(configNode.getNodeId().getValue());
        final Node nodeStatus = new NodeBuilder()
                .setNodeId(configNode.getNodeId())
                .setKey(configNode.getKey())
                .addAugmentation(RestconfNode.class, status)
                .build();
        final WriteTransaction transaction = transactionChain.newWriteOnlyTransaction();

        transaction.put(LogicalDatastoreType.OPERATIONAL, deviceId.getBindingTopologyPath(), nodeStatus);
        final CheckedFuture<Void, TransactionCommitFailedException> submit = transaction.submit();
        Futures.addCallback(submit, callback);
    }
}
