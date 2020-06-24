/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount.tls;

import io.netty.handler.ssl.SslHandler;
import java.util.Set;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.util.SslHandlerFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslHandlerFactoryAdapter implements SslHandlerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SslHandlerFactoryAdapter.class);

    private final TlsAllowedDevicesMonitor allowedDevicesMonitor;
    private final SslHandlerFactory sslHandlerFactory;

    public SslHandlerFactoryAdapter(final DataBroker dataBroker, final TlsAllowedDevicesMonitor allowedDevicesMonitor) {
        final NetconfKeystoreAdapter keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
        this.sslHandlerFactory = new SslHandlerFactoryImpl(keystoreAdapter);
        this.allowedDevicesMonitor = allowedDevicesMonitor;
    }

    @Override
    public SslHandler createSslHandler() {
        return createSslHandlerFilteredByKeys();
    }

    @Override
    public SslHandler createSslHandler(final Set<String> allowedKeys) {
        return createSslHandlerFilteredByKeys();
    }

    private SslHandler createSslHandlerFilteredByKeys() {
        if (allowedDevicesMonitor.findAllowedKeys().isEmpty()) {
            LOG.error("No associated keys for TLS authentication were found");
            throw new IllegalStateException("No associated keys for TLS authentication were found");
        }
        return sslHandlerFactory.createSslHandler(allowedDevicesMonitor.findAllowedKeys());
    }
}