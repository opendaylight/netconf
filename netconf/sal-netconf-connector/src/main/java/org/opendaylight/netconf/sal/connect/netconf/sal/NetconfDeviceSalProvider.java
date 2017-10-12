/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSalProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalProvider.class);

    private final RemoteDeviceId id;
    private final MountInstance mountInstance;
    private final DataBroker dataBroker;

    private volatile NetconfDeviceTopologyAdapter topologyDatastoreAdapter;

    private BindingTransactionChain txChain;

    private final TransactionChainListener transactionChainListener =  new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> chain,
                                             final AsyncTransaction<?, ?> transaction, final Throwable cause) {
            LOG.error("{}: TransactionChain({}) {} FAILED!", id, chain, transaction.getIdentifier(), cause);
            chain.close();
            resetTransactionChainForAdapaters();
            throw new IllegalStateException(id + "  TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> chain) {
            LOG.trace("{}: TransactionChain({}) {} SUCCESSFUL", id, chain);
        }
    };

    public NetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService,
                                    final DataBroker dataBroker) {
        this.id = deviceId;
        mountInstance = new MountInstance(mountService, id);
        this.dataBroker = dataBroker;
        txChain = Preconditions.checkNotNull(dataBroker).createTransactionChain(transactionChainListener);

        topologyDatastoreAdapter = new NetconfDeviceTopologyAdapter(id, txChain);
    }

    public NetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService) {
        this.id = deviceId;
        mountInstance = new MountInstance(mountService, id);
        this.dataBroker = null;
    }

    public MountInstance getMountInstance() {
        Preconditions.checkState(mountInstance != null,
                "%s: Mount instance was not initialized by sal. Cannot get mount instance", id);
        return mountInstance;
    }

    public NetconfDeviceTopologyAdapter getTopologyDatastoreAdapter() {
        Preconditions.checkState(topologyDatastoreAdapter != null,
                "%s: Sal provider %s was not initialized by sal. Cannot get topology datastore adapter", id);
        return topologyDatastoreAdapter;
    }

    private void resetTransactionChainForAdapaters() {
        txChain = Preconditions.checkNotNull(dataBroker).createTransactionChain(transactionChainListener);

        topologyDatastoreAdapter.setTxChain(txChain);

        LOG.trace("{}: Resetting TransactionChain {}", id, txChain);

    }

    public void close() throws Exception {
        mountInstance.close();
        if (topologyDatastoreAdapter != null) {
            topologyDatastoreAdapter.close();
        }
        topologyDatastoreAdapter = null;
        if (txChain != null) {
            txChain.close();
        }
    }

    public static final class MountInstance implements AutoCloseable {

        private final DOMMountPointService mountService;
        private final RemoteDeviceId id;
        private NetconfDeviceNotificationService notificationService;

        private ObjectRegistration<DOMMountPoint> topologyRegistration;

        public MountInstance(final DOMMountPointService mountService, final RemoteDeviceId id) {
            this.mountService = Preconditions.checkNotNull(mountService);
            this.id = Preconditions.checkNotNull(id);
        }

        public synchronized void onTopologyDeviceConnected(final SchemaContext initialCtx,
                final DOMDataBroker broker, final DOMRpcService rpc,
                final NetconfDeviceNotificationService newNotificationService) {
            Preconditions.checkNotNull(mountService, "Closed");
            Preconditions.checkState(topologyRegistration == null, "Already initialized");

            final DOMMountPointService.DOMMountPointBuilder mountBuilder =
                    mountService.createMountPoint(id.getTopologyPath());
            mountBuilder.addInitialSchemaContext(initialCtx);

            mountBuilder.addService(DOMDataBroker.class, broker);
            mountBuilder.addService(DOMRpcService.class, rpc);
            mountBuilder.addService(DOMNotificationService.class, newNotificationService);
            this.notificationService = newNotificationService;

            topologyRegistration = mountBuilder.register();
            LOG.debug("{}: TOPOLOGY Mountpoint exposed into MD-SAL {}", id, topologyRegistration);

        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        public synchronized void onTopologyDeviceDisconnected() {
            if (topologyRegistration == null) {
                LOG.trace("{}: Not removing TOPOLOGY mountpoint from MD-SAL, mountpoint was not registered yet", id);
                return;
            }

            try {
                topologyRegistration.close();
            } catch (final Exception e) {
                // Only log and ignore
                LOG.warn("Unable to unregister mount instance for {}. Ignoring exception", id.getTopologyPath(), e);
            } finally {
                LOG.debug("{}: TOPOLOGY Mountpoint removed from MD-SAL {}", id, topologyRegistration);
                topologyRegistration = null;
            }
        }

        @Override
        public synchronized void close() throws Exception {
            onTopologyDeviceDisconnected();
        }

        public synchronized void publish(final DOMNotification domNotification) {
            Preconditions.checkNotNull(notificationService, "Device not set up yet, cannot handle notification {}",
                    domNotification);
            notificationService.publishNotification(domNotification);
        }
    }

}
