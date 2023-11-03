/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Abstract base class for functionality shared between {@link NotificationStream} and
 * {@link DeviceNotificationStream}.
 */
abstract class AbstractNotificationStream extends RestconfStream<DOMNotification> implements DOMNotificationListener {
    private static final ImmutableMap<EncodingName, NotificationFormatterFactory> ENCODINGS = ImmutableMap.of(
        EncodingName.RFC8040_JSON, JSONNotificationFormatter.FACTORY,
        EncodingName.RFC8040_XML, XMLNotificationFormatter.FACTORY);

    AbstractNotificationStream(final ListenersBroker listenersBroker, final String name,
            final NotificationOutputType outputType) {
        super(listenersBroker, name, ENCODINGS, outputType);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void onNotification(final DOMNotification notification) {
        sendDataMessage(effectiveModel(), notification,
            notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
    }

    abstract @NonNull EffectiveModelContext effectiveModel();
}
