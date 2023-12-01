/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.connection.parameters.protocol.Specification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.connection.parameters.protocol.specification.TlsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = SslHandlerFactoryProvider.class)
public final class DefaultSslHandlerFactoryProvider
        implements SslHandlerFactoryProvider, ClusteredDataTreeChangeListener<Keystore>, AutoCloseable {

    /**
     * Parsed private key with certificate chain.
     */
    private record PrivateKeyRecord(java.security.PrivateKey privateKey, Certificate[] certificateChain) {
    }

    /**
     * Internal state, updated atomically.
     */
    private record State(
        @NonNull Map<String, PrivateKeyRecord> privateKeys,
        @NonNull Map<String, Certificate> trustedCertificates) {

        State {
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }

        @NonNull StateBuilder newBuilder() {
            return new StateBuilder(new HashMap<>(privateKeys), new HashMap<>(trustedCertificates));
        }
    }

    /**
     * Intermediate builder for State.
     */
    private record StateBuilder(
        @NonNull HashMap<String, PrivateKeyRecord> privateKeys,
        @NonNull HashMap<String, Certificate> trustedCertificates) {

        StateBuilder {
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }

        @NonNull State build() {
            return new State(Map.copyOf(privateKeys), Map.copyOf(trustedCertificates));
        }
    }

    private static final class SecurityHelper {
        private CertificateFactory certFactory;
        private KeyFactory dsaFactory;
        private KeyFactory rsaFactory;

        java.security.PrivateKey getJavaPrivateKey(final String base64PrivateKey) throws GeneralSecurityException {
            final var keySpec = new PKCS8EncodedKeySpec(base64Decode(base64PrivateKey));

            if (rsaFactory == null) {
                rsaFactory = KeyFactory.getInstance("RSA");
            }
            try {
                return rsaFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException ignore) {
                // Ignored
            }

            if (dsaFactory == null) {
                dsaFactory = KeyFactory.getInstance("DSA");
            }
            return dsaFactory.generatePrivate(keySpec);
        }

        private X509Certificate getCertificate(final String base64Certificate) throws GeneralSecurityException {
            if (certFactory == null) {
                certFactory = CertificateFactory.getInstance("X.509");
            }
            return (X509Certificate) certFactory.generateCertificate(
                new ByteArrayInputStream(base64Decode(base64Certificate)));
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSslHandlerFactoryProvider.class);
    private static final char[] EMPTY_CHARS = { };

    private final @NonNull SslHandlerFactory nospecFactory = new SslHandlerFactoryImpl(this, Set.of());
    private final @NonNull Registration reg;

    private volatile @NonNull State state = new State(Map.of(), Map.of());

    @Inject
    @Activate
    public DefaultSslHandlerFactoryProvider(@Reference final DataBroker dataBroker) {
        reg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
            this);
    }

    @Deactivate
    @PreDestroy
    @Override
    public void close() {
        reg.close();
    }

    @Override
    public SslHandlerFactory getSslHandlerFactory(final Specification specification) {
        if (specification == null) {
            return nospecFactory;
        }
        if (specification instanceof TlsCase tlsSpecification) {
            final var excludedVersions = tlsSpecification.nonnullTls().getExcludedVersions();
            return excludedVersions == null || excludedVersions.isEmpty() ? nospecFactory
                : new SslHandlerFactoryImpl(this, excludedVersions);
        }
        throw new IllegalArgumentException("Cannot get TLS specification from: " + specification);
    }

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which
     * will be used by TLS clients to create <code>SSLEngine</code>. The private keys are essential
     * to create JDK <code>KeyStore</code> while the trusted certificates are optional.
     *
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException If there is an I/O problem with the keystore data
     */
    KeyStore getJavaKeyStore() throws GeneralSecurityException, IOException {
        return getJavaKeyStore(Set.of());
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
        final var current = state;
        if (current.privateKeys.isEmpty()) {
            throw new KeyStoreException("No keystore private key found");
        }
        if (!allowedKeys.isEmpty()
                && allowedKeys.stream().filter(current.privateKeys::containsKey).findFirst().isEmpty()) {
            throw new KeyStoreException("None of allowed private keys found. Allowed: " + allowedKeys);
        }

        final var keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        // Private keys
        for (var entry : current.privateKeys.entrySet()) {
            final var alias = entry.getKey();
            if (allowedKeys.isEmpty() || allowedKeys.contains(alias)) {
                keyStore.setKeyEntry(alias, entry.getValue().privateKey(), EMPTY_CHARS,
                    entry.getValue().certificateChain());
            }
        }
        // trusted certificates
        for (var entry : current.trustedCertificates.entrySet()) {
            keyStore.setCertificateEntry(entry.getKey(), entry.getValue());
        }
        return keyStore;
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Starting update with {} changes", changes.size());
        final var builder = state.newBuilder();
        onDataTreeChanged(builder, changes);
        state = builder.build();
        LOG.debug("Update finished");
    }

    private static void onDataTreeChanged(final StateBuilder builder,
            final Collection<DataTreeModification<Keystore>> changes) {
        final var helper = new SecurityHelper();
        for (var change : changes) {
            LOG.debug("Processing change {}", change);
            final var rootNode = change.getRootNode();

            for (var changedChild : rootNode.getModifiedChildren()) {
                if (changedChild.getDataType().equals(PrivateKey.class)) {
                    onPrivateKeyChanged(builder.privateKeys, helper, (DataObjectModification<PrivateKey>)changedChild);
                } else if (changedChild.getDataType().equals(TrustedCertificate.class)) {
                    onTrustedCertificateChanged(builder.trustedCertificates, helper,
                        (DataObjectModification<TrustedCertificate>)changedChild);
                }
            }
        }
    }

    private static void onPrivateKeyChanged(final Map<String, PrivateKeyRecord> privateKeys,
            final SecurityHelper helper, final DataObjectModification<PrivateKey> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final var privateKey = objectModification.getDataAfter();
                try {
                    privateKeys.put(privateKey.getName(), toRecord(helper, privateKey));
                } catch (GeneralSecurityException e) {
                    LOG.warn("Invalid PrivateKey with alias {} is IGNORED due to parse exception",
                        privateKey.getName(), e);
                }
                break;
            case DELETE:
                privateKeys.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }

    private static PrivateKeyRecord toRecord(final SecurityHelper helper, final PrivateKey privateKey)
            throws GeneralSecurityException {
        final var javaPrivateKey = helper.getJavaPrivateKey(privateKey.getData());
        final var certChain = privateKey.getCertificateChain();
        if (certChain == null || certChain.isEmpty()) {
            throw new CertificateException("No certificate chain associated with private key");
        }
        final var certificateChain = new Certificate[certChain.size()];
        int index = 0;
        for (String certData : certChain) {
            certificateChain[index++] = helper.getCertificate(certData);
        }
        return new PrivateKeyRecord(javaPrivateKey, certificateChain);
    }

    private static void onTrustedCertificateChanged(final HashMap<String, Certificate> trustedCertificates,
            final SecurityHelper helper, final DataObjectModification<TrustedCertificate> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final var trustedCertificate = objectModification.getDataAfter();
                try {
                    trustedCertificates.put(trustedCertificate.getName(),
                        helper.getCertificate(trustedCertificate.getCertificate()));
                } catch (GeneralSecurityException e) {
                    LOG.warn("Invalid TrustedCertificate with alias {} is IGNORED due to parse exception",
                        trustedCertificate.getName(), e);
                }
                break;
            case DELETE:
                trustedCertificates.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }
}
