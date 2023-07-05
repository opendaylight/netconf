/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static org.opendaylight.netconf.transport.tls.KeyStoreUtils.newKeyStore;

import io.netty.handler.ssl.SslHandler;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev230417.HelloParamsGrouping;

/**
 * A pre-configured factory for creating {@link SslHandler}s.
 */
final class SSLEngineFactory {
    private static final char[] EMPTY_CHARS = new char[0];

    private final SSLContext sslContext;

    private SSLEngineFactory(final HelloParamsGrouping helloParams) throws UnsupportedConfigurationException {
        final KeyStore keyStore = newKeyStore();

        // FIXME: store keys

        final KeyManagerFactory kmf;
        try {
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate key manager", e);
        }
        try {
            kmf.init(keyStore, EMPTY_CHARS);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot initialize key manager", e);
        }

        final TrustManagerFactory tmf;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate trust manager", e);
        }
        try {
            tmf.init(keyStore);
        } catch (KeyStoreException e) {
            throw new UnsupportedConfigurationException("Cannot initialize trust manager", e);
        }

        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("TLS context cannot be allocated", e);
        }
        try {
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException e) {
            throw new UnsupportedConfigurationException("TLS context cannot be initialized", e);
        }
    }
}
