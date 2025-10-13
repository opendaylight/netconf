/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.function.Supplier;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
import org.opendaylight.restconf.server.spi.EventFormatterFactory;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Abstract base class for functionality shared between {@link DOMNotification}-based sources.
 */
public abstract class AbstractNotificationSource extends Source<DOMNotification> {
    protected static final class Listener implements DOMNotificationListener {
        private final Sink<DOMNotification> sink;
        private final Supplier<EffectiveModelContext> modelContext;

        public Listener(final Sink<DOMNotification> sink, final Supplier<EffectiveModelContext> modelContext) {
            this.sink = requireNonNull(sink);
            this.modelContext = requireNonNull(modelContext);
        }

        @Override
        public void onNotification(final DOMNotification notification) {
            sink.publish(modelContext.get(), notification,
                notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        }
    }

    protected AbstractNotificationSource(
            final ImmutableMap<MonitoringEncoding, ? extends EventFormatterFactory<DOMNotification>> encodings) {
        super(encodings);
    }
}
