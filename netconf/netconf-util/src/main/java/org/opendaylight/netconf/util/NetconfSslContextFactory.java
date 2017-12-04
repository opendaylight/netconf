/*
 * Copyright (c) 2017 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import com.google.common.collect.Sets;
import io.netty.handler.ssl.SslHandler;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import org.opendaylight.netconf.util.osgi.NetconfConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfSslContextFactory {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfSslContextFactory.class);

    private static final String PROTOCOL = "TLS";
    private static final String[] TLS_VERSIONS = new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};
    private static SSLContext sslContext;

    private NetconfSslContextFactory() {
    }

    public static synchronized SSLContext getContextInstance(final String keyStoreFile,
                                                                final String keyStorePassword,
                                                                final String trustStoreFile,
                                                                final String trustStorePassword) {
        if (sslContext == null) {
            try {
                KeyStore ks = KeyStore.getInstance("JKS");
                KeyStore ts = KeyStore.getInstance("JKS");

                ks.load(new FileInputStream(keyStoreFile), keyStorePassword.toCharArray());
                ts.load(new FileInputStream(trustStoreFile), trustStorePassword.toCharArray());

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, keyStorePassword.toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);

                sslContext = SSLContext.getInstance(PROTOCOL);
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            } catch (final GeneralSecurityException | IOException exc) {
                throw new IllegalStateException(exc);
            }

            LOG.info("Initialize SSL context successfully");
        }

        return sslContext;
    }

    public static final SslHandler getSslHandler(final String keyStoreFile,
                                                   final String keyStorePassword,
                                                   final String trustStoreFile,
                                                   final String trustStorePassword,
                                                   Boolean isClientSide) {
        SSLContext sslCtx = getContextInstance(keyStoreFile, keyStorePassword, trustStoreFile, trustStorePassword);
        SSLEngine engine = sslCtx.createSSLEngine();
        if (isClientSide) {
            engine.setUseClientMode(true);
        } else {
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(false);
        }

        Set<String> expected = Sets.newHashSet(TLS_VERSIONS);
        Set<String> supported = Sets.newHashSet(engine.getSupportedProtocols());
        Sets.SetView<String> setView = Sets.intersection(expected, supported);

        engine.setEnabledProtocols(setView.toArray(new String[setView.size()]));
        engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
        engine.setEnableSessionCreation(true);

        return new SslHandler(engine);
    }

    public static final SslHandler getClientSslHandler(NetconfConfiguration netconfConfiguration) {
        return getSslHandler(netconfConfiguration.getKeyStoreFile(),
                netconfConfiguration.getKeyStorePassword(),
                netconfConfiguration.getTrustStoreFile(),
                netconfConfiguration.getTrustStorePassword(),
                true);
    }
}
