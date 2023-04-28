/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.impl;

import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.osgi.service.component.annotations.Component;

/**
 * Default {@link SessionIdProvider}. Counts sessions from the start of the component.
 */
@Singleton
@Component(immediate = true, property = "type=default")
public final class DefaultSessionIdProvider implements SessionIdProvider {
    private final AtomicInteger sessionCounter = new AtomicInteger();

    @Inject
    public DefaultSessionIdProvider() {
        // Nothing here
    }

    @Override
    public SessionIdType getNextSessionId() {
        int id;
        do {
            id = sessionCounter.incrementAndGet();
        } while (id == 0);
        return sessionId(id);
    }

    @Override
    public SessionIdType getCurrentSessionId() {
        return sessionId(sessionCounter.get());
    }

    private static @NonNull SessionIdType sessionId(final int intBits) {
        return new SessionIdType(Uint32.fromIntBits(intBits));
    }
}
