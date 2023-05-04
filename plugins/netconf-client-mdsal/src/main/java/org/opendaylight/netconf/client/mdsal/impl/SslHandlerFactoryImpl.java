/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Sets;
import io.netty.handler.ssl.SslHandler;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import org.opendaylight.netconf.client.SslHandlerFactory;

final class SslHandlerFactoryImpl implements SslHandlerFactory {
    private final DefaultSslHandlerFactoryProvider keyStoreProvider;
    private final Set<String> excludedVersions;

    SslHandlerFactoryImpl(final DefaultSslHandlerFactoryProvider keyStoreProvider, final Set<String> excludedVersions) {
        this.keyStoreProvider = requireNonNull(keyStoreProvider);
        this.excludedVersions = requireNonNull(excludedVersions);
    }

    @Override
    public SslHandler createSslHandler(final Set<String> allowedKeys) {
        try {
            final KeyStore keyStore = keyStoreProvider.getJavaKeyStore(allowedKeys);

            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "".toCharArray());

            final TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            final SSLEngine engine = sslCtx.createSSLEngine();
            engine.setUseClientMode(true);

            final String[] engineProtocols = engine.getSupportedProtocols();
            final String[] enabledProtocols;
            if (!excludedVersions.isEmpty()) {
                final var protocols = Sets.newHashSet(engineProtocols);
                protocols.removeAll(excludedVersions);
                enabledProtocols = protocols.toArray(new String[0]);
            } else {
                enabledProtocols = engineProtocols;
            }

            engine.setEnabledProtocols(enabledProtocols);
            engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
            engine.setEnableSessionCreation(true);
            return new SslHandler(engine);
        } catch (GeneralSecurityException | IOException exc) {
            throw new IllegalStateException(exc);
        }
    }
}