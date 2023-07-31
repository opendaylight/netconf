/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.nb.rfc8040.streams.SSESessionHandler;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DeviceNotificationListenerAdaptor} is responsible to track events on notifications.
 */
public final class DeviceNotificationListenerAdaptor extends AbstractNotificationListenerAdaptor
        implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationListenerAdaptor.class);

    private final @NonNull EffectiveModelContext effectiveModel;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull YangInstanceIdentifier instanceIdentifier;

    private ListenerRegistration<DOMMountPointListener> reg;

    public DeviceNotificationListenerAdaptor(final String streamName, final NotificationOutputType outputType,
            final EffectiveModelContext effectiveModel, final DOMMountPointService mountPointService,
            final YangInstanceIdentifier path) {
        super(streamName, outputType);
        this.effectiveModel = requireNonNull(effectiveModel);
        this.mountPointService = requireNonNull(mountPointService);
        instanceIdentifier = requireNonNull(path);
    }

    public synchronized void listen(final DOMNotificationService notificationService, final Set<Absolute> paths) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, paths));
            reg = mountPointService.registerProvisionListener(this);
        }
    }

    private synchronized void resetListenerRegistration() {
        if (reg != null) {
            reg.close();
            reg = null;
        }
    }

    @Override
    EffectiveModelContext effectiveModel() {
        return effectiveModel;
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        // No-op
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        if (instanceIdentifier.equals(path)) {
            getSubscribers().forEach(subscriber -> {
                if (subscriber.isConnected()) {
                    subscriber.sendDataMessage("Device disconnected");
                }
                if (subscriber instanceof SSESessionHandler sseSessionHandler) {
                    try {
                        sseSessionHandler.close();
                    } catch (IllegalStateException e) {
                        LOG.warn("Ignoring exception while closing sse session");
                    }
                }
            });
            ListenersBroker.getInstance().removeAndCloseDeviceNotificationListener(this);
            resetListenerRegistration();
        }
    }
}
