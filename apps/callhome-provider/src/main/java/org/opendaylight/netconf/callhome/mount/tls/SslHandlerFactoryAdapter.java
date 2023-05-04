/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslHandler;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslHandlerFactoryAdapter implements SslHandlerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SslHandlerFactoryAdapter.class);

    private final TlsAllowedDevicesMonitor allowedDevicesMonitor;
    private final SslHandlerFactory delegate;

    public SslHandlerFactoryAdapter(final @NonNull SslHandlerFactory delegate,
            final @NonNull TlsAllowedDevicesMonitor allowedDevicesMonitor) {
        this.delegate = requireNonNull(delegate);
        this.allowedDevicesMonitor = requireNonNull(allowedDevicesMonitor);
    }

    @Override
    public SslHandler createSslHandler() {
        return createSslHandlerFilteredByKeys();
    }

    @Override
    public SslHandler createSslHandler(final Set<String> allowedKeys) {
        // FIXME: we are ignoring passed in keys?!
        return createSslHandlerFilteredByKeys();
    }

    private SslHandler createSslHandlerFilteredByKeys() {
        final var allowedKeys = allowedDevicesMonitor.findAllowedKeys();
        if (allowedKeys.isEmpty()) {
            LOG.error("No associated keys for TLS authentication were found");
            throw new IllegalStateException("No associated keys for TLS authentication were found");
        }
        return delegate.createSslHandler(allowedKeys);
    }
}