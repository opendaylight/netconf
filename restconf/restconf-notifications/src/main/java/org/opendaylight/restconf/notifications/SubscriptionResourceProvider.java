/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceProvider;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.subscription.SubscriptionStateMachine;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * {@link WebHostResourceProvider} of RESTCONF subscription.
 */
@Singleton
@NonNullByDefault
@Component(immediate = true)
public final class SubscriptionResourceProvider implements WebHostResourceProvider {
    private final RestconfStream.Registry streamRegistry;

    @Inject
    @Activate
    public SubscriptionResourceProvider(@Reference final RestconfStream.Registry streamRegistry) {
        this.streamRegistry = streamRegistry;
    }

    @Override
    public String defaultPath() {
        return "subscriptions";
    }

    // FIXME Consider not hardcoding SSE maximum fragment length and heartbeat interval millis
    @Override
    public WebHostResourceInstance createInstance(final String path) {
        return new SubscriptionResourceInstance(path, streamRegistry, Uint16.ZERO.toJava(),
            Uint16.valueOf(10_000).toJava());
    }
}
