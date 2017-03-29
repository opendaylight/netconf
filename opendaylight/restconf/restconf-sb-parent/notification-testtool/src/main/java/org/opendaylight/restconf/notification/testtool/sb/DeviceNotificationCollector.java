/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notification.testtool.sb;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.sal.core.api.mount.MountProvisionListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.Notifications;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.ResetInputBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When new mount point is created, instance of this class creates new {@link DeviceNotificationListener} and registers
 * it to listen all device notifications.
 */
class DeviceNotificationCollector implements MountProvisionListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationCollector.class);

    private final DOMMountPointService mountPointService;
    private final NotificationStoreServiceImpl notificationStoreService;
    private final DOMDataBroker dataBroker;
    private final ListenerRegistration<MountProvisionListener> listenerRegistration;
    private final Map<YangInstanceIdentifier, DeviceNotificationListener> notificationsListeners = new ConcurrentHashMap<>();


    public DeviceNotificationCollector(final DOMMountPointService service, final DOMDataBroker dataBroker,
                                       final NotificationStoreServiceImpl notificationStoreService) {
        this.mountPointService = service;
        this.notificationStoreService = notificationStoreService;
        this.dataBroker = dataBroker;
        final DOMTransactionChain txChain = dataBroker.createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(final TransactionChain<?, ?> transactionChain,
                                                 final AsyncTransaction<?, ?> asyncTransaction,
                                                 final Throwable throwable) {
                LOG.error("Notification write failed", throwable);
            }

            @Override
            public void onTransactionChainSuccessful(final TransactionChain<?, ?> transactionChain) {
                LOG.info("Notification write successful");
            }
        });
        final DOMDataWriteTransaction writeTransaction = txChain.newWriteOnlyTransaction();
        final ContainerNode notificationStore = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Notifications.QNAME))
                .build();
        final YangInstanceIdentifier nsId = YangInstanceIdentifier.builder().node(Notifications.QNAME).build();
        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, nsId, notificationStore);
        try {
            writeTransaction.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            LOG.error("Failed to write transaction", e);
        }
        listenerRegistration = service.registerProvisionListener(this);
    }


    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        final Optional<DOMMountPoint> mountPoint = mountPointService.getMountPoint(path);
        final String nodeId = getNodeId(path);
        final Future<RpcResult<Void>> reset = notificationStoreService.reset(new ResetInputBuilder().setDeviceId(nodeId).build());
        try {
            reset.get();
            if (mountPoint.isPresent()) {
                final Optional<DOMNotificationService> service = mountPoint.get().getService(DOMNotificationService.class);
                if (service.isPresent()) {
                    final DeviceNotificationListener deviceNotificationListener = new DeviceNotificationListener(nodeId, dataBroker, mountPoint.get());
                    deviceNotificationListener.registerToAllNotifications();
                    notificationsListeners.put(path, deviceNotificationListener);
                } else {
                    LOG.warn("Notification service not present on {}", path);
                }
            } else {
                LOG.warn("Mount point not present on {}", path);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Reset unsuccessful", e);
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        notificationsListeners.remove(path);
    }

    @Override
    public void close() throws Exception {
        listenerRegistration.close();
        for (final DeviceNotificationListener listener : notificationsListeners.values()) {
            listener.close();
        }
        notificationsListeners.clear();

    }

    public static String getNodeId(final YangInstanceIdentifier path) {
        final YangInstanceIdentifier.PathArgument lastPathArgument = path.getLastPathArgument();
        Preconditions.checkState(lastPathArgument instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates);
        final YangInstanceIdentifier.NodeIdentifierWithPredicates listId = (YangInstanceIdentifier.NodeIdentifierWithPredicates) lastPathArgument;
        final Collection<Object> keyValues = listId.getKeyValues().values();
        Preconditions.checkState(keyValues.size() == 1);
        return  (String) keyValues.iterator().next();
    }
}
