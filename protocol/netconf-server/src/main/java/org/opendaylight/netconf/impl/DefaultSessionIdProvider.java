/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl;

import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.osgi.service.component.annotations.Component;

/**
 * Default {@link SessionIdProvider}. Counts sessions from the start of the component.
 */
@Singleton
@Component(immediate = true, property = "type=default")
public final class DefaultSessionIdProvider implements SessionIdProvider {
    private final AtomicLong sessionCounter = new AtomicLong();

    @Inject
    public DefaultSessionIdProvider() {
        // Nothing here
    }

    @Override
    public long getNextSessionId() {
        return sessionCounter.incrementAndGet();
    }

    @Override
    public long getCurrentSessionId() {
        return sessionCounter.get();
    }
}
