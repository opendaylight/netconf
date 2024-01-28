/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslContext;
import java.util.Set;

final class FilteredSslContextFactory extends DefaultSslContextFactory {
    private final Set<String> excludedVersions;

    FilteredSslContextFactory(final DefaultSslContextFactoryProvider keyStoreProvider,
            final Set<String> excludedVersions) {
        super(keyStoreProvider);
        this.excludedVersions = requireNonNull(excludedVersions);
    }

    @Override
    SslContext wrapSslContext(final SslContext sslContext) {
        return new FilteredSslContext(sslContext, excludedVersions);
    }
}
