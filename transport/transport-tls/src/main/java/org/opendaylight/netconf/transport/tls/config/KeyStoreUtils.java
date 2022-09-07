/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

public final class KeyStoreUtils {

    private static final char[] EMPTY_SECRET = new char[0];
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;
    private static final String BKS = "BKS";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private KeyStoreUtils() {
        // utility class
    }

    /**
     * Creates and initializes new key store instance.
     *
     * @return key store instance
     * @throws UnsupportedConfigurationException if key store cannot be instantiated
     */
    public static KeyStore newKeyStore() throws UnsupportedConfigurationException {
        final KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(BKS, BC);
            keyStore.load(null, null);
        } catch (NoSuchProviderException | NoSuchAlgorithmException | CertificateException
                 | IOException | KeyStoreException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate key store", e);
        }
        return keyStore;
    }

    /**
     * Instantiates key manager factory, initializes it with key store instance provided.
     *
     * @param keyStore key store instance
     * @return key manager factory instance
     * @throws UnsupportedConfigurationException if key manager factory cannot be instantiated
     */
    public static @NonNull KeyManagerFactory buildKeyManagerFactory(final @NonNull KeyStore keyStore)
            throws UnsupportedConfigurationException {
        try {
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, EMPTY_SECRET);
            return kmf;
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate key manager", e);
        }
    }

    /**
     * Instantiates trust manager factory, initializes it with key store instance provided.
     *
     * @param keyStore key store
     * @return trust manager factory instance
     * @throws UnsupportedConfigurationException if trust manager factory cannot be instantiated
     */
    public static @NonNull TrustManagerFactory buildTrustManagerFactory(final @NonNull KeyStore keyStore)
            throws UnsupportedConfigurationException {
        try {
            final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            return tmf;
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate trust manager", e);
        }
    }

    /**
     * Builds X509 Certificate instance.
     *
     * @param bytes certificate encoded
     * @return certificate instance
     * @throws CertificateException if certificate error occurs
     * @throws IOException if input read error occurs
     */
    public static Certificate buildX509Certificate(final byte[] bytes)
            throws CertificateException, IOException {
        try (var in = new ByteArrayInputStream(bytes)) {
            return CertificateFactory.getInstance("X.509", BC).generateCertificate(in);
        } catch (NoSuchProviderException e) {
            throw new IllegalStateException(e);
        }
    }
}
