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
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import org.opendaylight.netconf.client.SslContextFactory;

final class SslContextFactoryImpl implements SslContextFactory {
    private final DefaultSslContextFactoryProvider keyStoreProvider;
    private final Set<String> excludedVersions;

    SslContextFactoryImpl(final DefaultSslContextFactoryProvider keyStoreProvider, final Set<String> excludedVersions) {
        this.keyStoreProvider = requireNonNull(keyStoreProvider);
        this.excludedVersions = requireNonNull(excludedVersions);
    }

    @Override
    public SslContext createSslContext(final Set<String> allowedKeys) {
        try {
            final KeyStore keyStore = keyStoreProvider.getJavaKeyStore(allowedKeys);

            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "".toCharArray());

            final TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final var javaCtx = SSLContext.getInstance("TLS");
            javaCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            return new DelegatingSslContext(new JdkSslContext(javaCtx, true, ClientAuth.NONE)) {
                @Override
                protected void initEngine(final SSLEngine engine) {
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
                }
            };
        } catch (GeneralSecurityException | IOException exc) {
            throw new IllegalStateException(exc);
        }
    }
}