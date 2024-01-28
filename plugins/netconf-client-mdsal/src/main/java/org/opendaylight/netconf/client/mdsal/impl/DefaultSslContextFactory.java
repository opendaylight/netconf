/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.SslContextFactory;

class DefaultSslContextFactory implements SslContextFactory {
    private final DefaultSslContextFactoryProvider keyStoreProvider;

    DefaultSslContextFactory(final DefaultSslContextFactoryProvider keyStoreProvider) {
        this.keyStoreProvider = requireNonNull(keyStoreProvider);
    }

    @Override
    public final SslContext createSslContext(final Set<String> allowedKeys) {
        final SSLContext sslContext;
        try {
            final var keyStore = keyStoreProvider.getJavaKeyStore(allowedKeys);

            final var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "".toCharArray());

            final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize SSL context", e);
        }

        return wrapSslContext(new JdkSslContext(sslContext, true, ClientAuth.NONE));
    }

    @NonNull SslContext wrapSslContext(@NonNull
    final SslContext sslContext) {
        return sslContext;
    }
}
