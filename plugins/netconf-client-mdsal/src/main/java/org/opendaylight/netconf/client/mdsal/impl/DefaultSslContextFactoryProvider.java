/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystore;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.parameters.protocol.Specification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.parameters.protocol.specification.TlsCase;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component(service = SslContextFactoryProvider.class)
public final class DefaultSslContextFactoryProvider implements SslContextFactoryProvider, AutoCloseable {
    private static final X509Certificate[] EMPTY_CERTS = { };
    private static final char[] EMPTY_CHARS = { };

    private final @NonNull DefaultSslContextFactory nospecFactory = new DefaultSslContextFactory(this);
    private final Registration reg;

    private volatile @NonNull NetconfKeystore keystore = NetconfKeystore.EMPTY;

    @Inject
    @Activate
    public DefaultSslContextFactoryProvider(@Reference final NetconfKeystoreService keystoreService) {
        reg = keystoreService.registerKeystoreConsumer(this::onKeystoreUpdated);
    }

    @Override
    @PreDestroy
    @Deactivate
    public void close() {
        reg.close();
    }

    private void onKeystoreUpdated(final @NonNull NetconfKeystore newKeystore) {
        keystore = newKeystore;
    }

    @Override
    public SslContextFactory getSslContextFactory(final Specification specification) {
        if (specification == null) {
            return nospecFactory;
        } else if (specification instanceof TlsCase tlsSpecification) {
            final var excludedVersions = tlsSpecification.nonnullTls().getExcludedVersions();
            return excludedVersions == null || excludedVersions.isEmpty() ? nospecFactory
                : new FilteredSslContextFactory(this, excludedVersions);
        } else {
            throw new IllegalArgumentException("Cannot get TLS specification from: " + specification);
        }
    }

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which
     * will be used by TLS clients to create <code>SSLEngine</code>. The private keys are essential
     * to create JDK <code>KeyStore</code> while the trusted certificates are optional.
     *
     * @param allowedKeys Set of keys to include during KeyStore generation, empty set will create
     *                   a KeyStore with all possible keys.
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException If there is an I/O problem with the keystore data
     */
    KeyStore getJavaKeyStore(final Set<String> allowedKeys) throws GeneralSecurityException, IOException {
        requireNonNull(allowedKeys);
        final var current = keystore;
        if (current.privateKeys().isEmpty()) {
            throw new KeyStoreException("No keystore private key found");
        }

        final var keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        // Private keys first
        for (var entry : current.privateKeys().entrySet()) {
            final var alias = entry.getKey();
            if (allowedKeys.isEmpty() || allowedKeys.contains(alias)) {
                final var privateKey = entry.getValue();
                keyStore.setKeyEntry(alias, privateKey.key(), EMPTY_CHARS,
                    privateKey.certificateChain().toArray(EMPTY_CERTS));
            }
        }

        for (var entry : current.trustedCertificates().entrySet()) {
            keyStore.setCertificateEntry(entry.getKey(), entry.getValue());
        }

        return keyStore;
    }
}
