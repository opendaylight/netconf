/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import java.util.Arrays;
import java.util.Set;
import javax.net.ssl.SSLEngine;

final class FilteredSslContext extends DelegatingSslContext {
    private final Set<String> excludedVersions;

    FilteredSslContext(final SslContext ctx, final Set<String> excludedVersions) {
        super(ctx);
        this.excludedVersions = requireNonNull(excludedVersions);
    }

    @Override
    protected void initEngine(final SSLEngine engine) {
        engine.setEnabledProtocols(Arrays.stream(engine.getSupportedProtocols())
            .filter(protocol -> !excludedVersions.contains(protocol))
            .toArray(String[]::new));
        engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
        engine.setEnableSessionCreation(true);
    }
}
