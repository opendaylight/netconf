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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for functionality shared between {@link NotificationStream} and
 * {@link DeviceNotificationStream}.
 */
abstract class AbstractNotificationStream extends RestconfStream<DOMNotification> implements DOMNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationStream.class);
    private static final ImmutableMap<EncodingName, NotificationFormatterFactory> ENCODINGS = ImmutableMap.of(
        EncodingName.RFC8040_JSON, JSONNotificationFormatter.FACTORY,
        EncodingName.RFC8040_XML, XMLNotificationFormatter.FACTORY);

    AbstractNotificationStream(final ListenersBroker listenersBroker, final String name) {
        super(listenersBroker, name, ENCODINGS);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void onNotification(final DOMNotification notification) {
        eventData(effectiveModel(), notification,
            notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
    }

    abstract @NonNull EffectiveModelContext effectiveModel();
}
