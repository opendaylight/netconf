/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.security;

import java.security.KeyStore;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class SecurityConfig {

    private final KeyStore keyStore;
    private final String password;
    private final SslContextFactory sslContextFactory;

    public SecurityConfig(final KeyStore keyStore, final String password) {
        this.keyStore = keyStore;
        this.password = password;
        sslContextFactory = new SslContextFactory.Server();
        initFactoryCtx();
    }

    private void initFactoryCtx() {
        sslContextFactory.setTrustStore(keyStore);
        sslContextFactory.setTrustStorePassword(password);
        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(password);
        sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public String getPassword() {
        return password;
    }

    public SslContextFactory getSSLFactoryContext() {
        return sslContextFactory;
    }

    /**
     * Init ssl connection factory with specific protocol.
     *
     * @param protocol
     *            - specific protocol
     * @return ssl connection factory with specific protocol
     */
    public SslConnectionFactory getSslConnectionFactory(final String protocol) {
        return new SslConnectionFactory(sslContextFactory, protocol);
    }
}
