/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * A {@link RestconfStream} reporting YANG notifications coming from a mounted device.
 */
public final class DeviceNotificationStream extends AbstractNotificationStream implements DOMMountPointListener {
    private final @NonNull EffectiveModelContext effectiveModel;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull YangInstanceIdentifier instanceIdentifier;

    private Registration reg;

    public DeviceNotificationStream(final ListenersBroker listenersBroker, final String name,
            final NotificationOutputType outputType, final EffectiveModelContext effectiveModel,
            final DOMMountPointService mountPointService, final YangInstanceIdentifier instanceIdentifier) {
        super(listenersBroker, name, outputType);
        this.effectiveModel = requireNonNull(effectiveModel);
        this.mountPointService = requireNonNull(mountPointService);
        this.instanceIdentifier = requireNonNull(instanceIdentifier);
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
            resetListenerRegistration();
            endOfStream();
        }
    }
}
