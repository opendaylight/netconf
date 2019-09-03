/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.Transaction;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
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

    private TransactionChain txChain;

    private final TransactionChainListener transactionChainListener =  new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain chain, final Transaction transaction,
                final Throwable cause) {
            LOG.error("{}: TransactionChain({}) {} FAILED!", id, chain, transaction.getIdentifier(), cause);
            chain.close();
            resetTransactionChainForAdapaters();
            throw new IllegalStateException(id + "  TransactionChain(" + chain + ") not committed correctly", cause);
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain chain) {
            LOG.trace("{}: TransactionChain({}) SUCCESSFUL", id, chain);
        }
    };

    public NetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService) {
        this(deviceId, mountService, null);
    }

    public NetconfDeviceSalProvider(final RemoteDeviceId deviceId, final DOMMountPointService mountService,
            final DataBroker dataBroker) {
        this.id = deviceId;
        mountInstance = new MountInstance(mountService, id);
        this.dataBroker = dataBroker;
        if (dataBroker != null) {
            txChain = requireNonNull(dataBroker).createTransactionChain(transactionChainListener);
            topologyDatastoreAdapter = new NetconfDeviceTopologyAdapter(id, txChain);
        }
    }

    public MountInstance getMountInstance() {
        checkState(mountInstance != null, "%s: Mount instance was not initialized by sal. Cannot get mount instance",
                id);
        return mountInstance;
    }

    public NetconfDeviceTopologyAdapter getTopologyDatastoreAdapter() {
        checkState(topologyDatastoreAdapter != null,
                "%s: Sal provider %s was not initialized by sal. Cannot get topology datastore adapter", id);
        return topologyDatastoreAdapter;
    }

    private void resetTransactionChainForAdapaters() {
        txChain = requireNonNull(dataBroker).createTransactionChain(transactionChainListener);

        topologyDatastoreAdapter.setTxChain(txChain);

        LOG.trace("{}: Resetting TransactionChain {}", id, txChain);

    }

    @Override
    public void close() {
        mountInstance.close();
        if (topologyDatastoreAdapter != null) {
            topologyDatastoreAdapter.close();
        }
        topologyDatastoreAdapter = null;
        if (txChain != null) {
            txChain.close();
        }
    }

    public static class MountInstance implements AutoCloseable {

        private final DOMMountPointService mountService;
        private final RemoteDeviceId id;

        private NetconfDeviceNotificationService notificationService;
        private ObjectRegistration<DOMMountPoint> topologyRegistration;

        MountInstance(final DOMMountPointService mountService, final RemoteDeviceId id) {
            this.mountService = requireNonNull(mountService);
            this.id = requireNonNull(id);
        }

        public void onTopologyDeviceConnected(final SchemaContext initialCtx,
                final DOMDataBroker broker, final DOMRpcService rpc,
                final NetconfDeviceNotificationService newNotificationService) {
            onTopologyDeviceConnected(initialCtx, broker, rpc, newNotificationService, null);
        }

        public synchronized void onTopologyDeviceConnected(final SchemaContext initialCtx,
                final DOMDataBroker broker, final DOMRpcService rpc,
                final NetconfDeviceNotificationService newNotificationService, final DOMActionService deviceAction) {
            requireNonNull(mountService, "Closed");
            checkState(topologyRegistration == null, "Already initialized");

            final DOMMountPointService.DOMMountPointBuilder mountBuilder =
                    mountService.createMountPoint(id.getTopologyPath());
            mountBuilder.addInitialSchemaContext(initialCtx);

            mountBuilder.addService(DOMDataBroker.class, broker);
            mountBuilder.addService(DOMRpcService.class, rpc);
            mountBuilder.addService(DOMNotificationService.class, newNotificationService);
            if (deviceAction != null) {
                mountBuilder.addService(DOMActionService.class, deviceAction);
            }
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
        public synchronized void close() {
            onTopologyDeviceDisconnected();
        }

        public synchronized void publish(final DOMNotification domNotification) {
            checkNotNull(notificationService, "Device not set up yet, cannot handle notification %s", domNotification)
                .publishNotification(domNotification);
        }
    }

}
