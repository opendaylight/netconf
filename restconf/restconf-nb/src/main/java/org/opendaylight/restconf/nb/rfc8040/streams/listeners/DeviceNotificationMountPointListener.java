/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.PreDestroy;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.ContentParam;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.CreateStreamUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeviceNotificationMountPointListener implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationMountPointListener.class);
    private static final ConcurrentMap<YangInstanceIdentifier, DeviceNotificationListenerAdaptor> LISTENERS =
            new ConcurrentHashMap<>();
    private static ListenerRegistration<DOMMountPointListener> REG = null;
    private static DOMMountPointService mountPointServices;
    private static RestconfStrategy restconfStr;
    private static EffectiveModelContext effectiveModelContext;

    private static boolean isListeningOnMount() {
        return REG != null;
    }

    public static synchronized void addDeviceNotificationListener(final YangInstanceIdentifier yangInstanceIdentifier,
            final DeviceNotificationListenerAdaptor listenerRegistration,final DOMMountPointService mountPointService,
                                                                  final RestconfStrategy restconfStrategy,
                                                                  final EffectiveModelContext refSchemaCtx) {
        LISTENERS.put(yangInstanceIdentifier, listenerRegistration);
        // Staring MountPoint listener
        if (!isListeningOnMount()) {
            REG = mountPointService.registerProvisionListener(new DeviceNotificationMountPointListener());
        }
        mountPointServices = mountPointService;
        restconfStr = restconfStrategy;
        effectiveModelContext = refSchemaCtx;
    }

    private static synchronized void resetListenerRegistration() {
        if (REG != null) {
            REG.close();
            REG = null;
        }
    }

    @PreDestroy
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void stop() {
        REG.close();
        final Iterator<DeviceNotificationListenerAdaptor> it = LISTENERS.values().iterator();
        while (it.hasNext()) {
            try {
                it.next().close();
            } catch (Exception e) {
                LOG.warn("Ignoring exception while closing device notification listener adaptor", e);
            }
            it.remove();
        }
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        reRegisterDeviceNotification(path);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        final DeviceNotificationListenerAdaptor listener = LISTENERS.remove(path);
        if (listener != null) {
            listener.getSubscribers().forEach(subscriber -> {
                if (subscriber.isConnected()) {
                    subscriber.sendDataMessage("Device disconnected");
                }
                if (subscriber instanceof SSESessionHandler) {
                    SSESessionHandler sseSessionHandler = (SSESessionHandler) subscriber;
                    sseSessionHandler.close();
                }
            });
            ListenersBroker.getInstance()
                    .removeAndCloseDeviceNotificationListener(listener);
            // removing MountPoint listener if not Device notification is available
            boolean flg = false;
            if (LISTENERS.isEmpty() && flg) {
                resetListenerRegistration();
            }
        }
    }

    private void reRegisterDeviceNotification(YangInstanceIdentifier path) {
        String node = String.valueOf(
                        ((YangInstanceIdentifier.NodeIdentifierWithPredicates)path.getLastPathArgument()).values()
                .iterator().next());
        node = "network-topology:network-topology/topology=topology-netconf/node=" + node + "/yang-ext:mount";
        InstanceIdentifierContext instanceIdentifier = ParserIdentifier.toInstanceIdentifier(node,
                effectiveModelContext,
                Optional.of(mountPointServices));
        NormalizedNode normalizedNode = ReadDataTransactionUtil.readData(ContentParam.ALL, path,
                restconfStr, null, effectiveModelContext);
        LOG.info("hello {}{}",normalizedNode.toString(),instanceIdentifier.toString());
        CreateStreamUtil.createDeviceNotificationListener(null,
                node,
                effectiveModelContext, instanceIdentifier,
                mountPointServices, restconfStr);
    }

}