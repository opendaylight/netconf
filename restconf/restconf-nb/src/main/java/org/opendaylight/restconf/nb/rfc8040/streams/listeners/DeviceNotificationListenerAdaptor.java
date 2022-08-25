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
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

/**
 * {@link DeviceNotificationListenerAdaptor} is responsible to track events on notifications.
 */
public final class DeviceNotificationListenerAdaptor extends AbstractNotificationListenerAdaptor {
    private final @NonNull EffectiveModelContext effectiveModel;

    public DeviceNotificationListenerAdaptor(final String streamName, final NotificationOutputType outputType,
            final EffectiveModelContext effectiveModel) {
        // FIXME: a dummy QName due to contracts
        super(QName.create("dummy", "dummy"), streamName, outputType);
        this.effectiveModel = requireNonNull(effectiveModel);
    }

    public synchronized void listen(final DOMNotificationService notificationService, final Set<Absolute> paths) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, paths));
        }
    }

    @Override
    EffectiveModelContext effectiveModel() {
        return effectiveModel;
    }
}
