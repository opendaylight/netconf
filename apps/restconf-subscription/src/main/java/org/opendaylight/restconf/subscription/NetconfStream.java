/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Default {@link RestconfStream} called "NETCONF" providing all controller's YANG notifications as described in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a> for purposes of subscribed notifications
 * defined in <a href="https://www.rfc-editor.org/rfc/rfc8639">RFC 8639</a>.
 *
 * <p>It automatically re-registers to current YANG notifications on controller's {@code EffectiveModelContext} change.
 */
@Singleton
@Component(service = { })
public final class NetconfStream implements AutoCloseable {
    private final @NonNull Registration contextListenerReg;

    @Inject
    @Activate
    public NetconfStream(@Reference final DOMSchemaService schemaService,
            @Reference final DOMNotificationService notificationService,
            @Reference final RestconfStream.Registry streamRegistry) {

        // start listener on model context change
        contextListenerReg = new ContextListener(notificationService, schemaService, streamRegistry);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        contextListenerReg.close();
    }
}
