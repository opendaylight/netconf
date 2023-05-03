/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.opendaylight.netconf.client.mdsal.api.NetconfKeystoreAdapter;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
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
@Component(service = NetconfKeystoreAdapter.class)
public final class DefaultNetconfKeystoreAdapter
        implements NetconfKeystoreAdapter, ClusteredDataTreeChangeListener<Keystore>, AutoCloseable {
    /**
     * Internal state, updated atomically.
     */
    private record State(
        @NonNull Map<String, KeyCredential> pairs,
        @NonNull Map<String, PrivateKey> privateKeys,
        @NonNull Map<String, TrustedCertificate> trustedCertificates) {

        State {
            requireNonNull(pairs);
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }
    }

    /**
     * Intermediate build for state.
     */
    private record StateBuilder(
        @NonNull HashMap<String, KeyCredential> pairs,
        @NonNull HashMap<String, PrivateKey> privateKeys,
        @NonNull HashMap<String, TrustedCertificate> trustedCertificates) {

        StateBuilder {
            requireNonNull(pairs);
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }

        StateBuilder(final State state) {
            this(new HashMap<>(state.pairs), new HashMap<>(state.privateKeys),
                new HashMap<>(state.trustedCertificates));
        }

        @NonNull State build() {
            return new State(Map.copyOf(pairs), Map.copyOf(privateKeys), Map.copyOf(trustedCertificates));
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNetconfKeystoreAdapter.class);
    private static final char[] EMPTY_CHARS = { };
    private static final VarHandle STATE_VH;

    static {
        try {
            STATE_VH = MethodHandles.lookup().findVarHandle(DefaultNetconfKeystoreAdapter.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull Registration reg;

    private volatile @NonNull State state = new State(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of());

    @Inject
    @Activate
    public DefaultNetconfKeystoreAdapter(@Reference final DataBroker dataBroker) {
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
    public Optional<KeyCredential> getKeypairFromId(final String keyId) {
        return Optional.ofNullable(currentState().pairs.get(keyId));
    }

    @Override
    public KeyStore getJavaKeyStore(final Set<String> allowedKeys) throws GeneralSecurityException, IOException {
        requireNonNull(allowedKeys);
        final var current = currentState();
        if (current.privateKeys.isEmpty()) {
            throw new KeyStoreException("No keystore private key found");
        }

        final var keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        // Private keys first
        for (var entry : current.privateKeys.entrySet()) {
            final var alias = entry.getKey();
            if (!allowedKeys.isEmpty() && !allowedKeys.contains(alias)) {
                continue;
            }

            final var privateKey = entry.getValue();
            final var key = getJavaPrivateKey(privateKey.getData());
            // TODO: do not do toArray()?
            // TODO: require() here and filter in update path
            final var certificateChain = getCertificateChain(privateKey.getCertificateChain().toArray(new String[0]));
            // TODO: filter these and do not throw?
            if (certificateChain.isEmpty()) {
                throw new CertificateException("No certificate chain associated with private key found");
            }

            keyStore.setKeyEntry(alias, key, EMPTY_CHARS, certificateChain.toArray(Certificate[]::new));
        }

        for (var entry : current.trustedCertificates.entrySet()) {
            // TODO: ahem: single entry and single get
            final var x509Certificates = getCertificateChain(new String[] { entry.getValue().getCertificate() });
            keyStore.setCertificateEntry(entry.getKey(), x509Certificates.get(0));
        }

        return keyStore;
    }

    private @NonNull State currentState() {
        return verifyNotNull((@NonNull State) STATE_VH.getAcquire(this));
    }

    private static java.security.PrivateKey getJavaPrivateKey(final String base64PrivateKey)
            throws GeneralSecurityException {
        final var keySpec = new PKCS8EncodedKeySpec(base64Decode(base64PrivateKey));
        java.security.PrivateKey key;

        try {
            // FIXME: cache instances, or something smarter?
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            key = keyFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException ignore) {
            final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            key = keyFactory.generatePrivate(keySpec);
        }

        return key;
    }

    private static List<X509Certificate> getCertificateChain(final String[] base64Certificates)
            throws GeneralSecurityException {
        // TODO: https://stackoverflow.com/questions/43809909/is-certificatefactory-getinstancex-509-thread-safe
        //        indicates this is thread-safe in most cases, but can we get a better assurance?
        final var factory = CertificateFactory.getInstance("X.509");
        final var certificates = new ArrayList<X509Certificate>();

        for (var cert : base64Certificates) {
            final byte[] buffer = base64Decode(cert);
            certificates.add((X509Certificate)factory.generateCertificate(new ByteArrayInputStream(buffer)));
        }

        return certificates;
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Starting update with {} changes", changes.size());
        final var builder = new StateBuilder(currentState());
        onDataTreeChanged(builder, changes);
        STATE_VH.setRelease(builder.build());
    }

    private static void onDataTreeChanged(final StateBuilder builder,
            final Collection<DataTreeModification<Keystore>> changes) {
        for (var change : changes) {
            LOG.debug("Processing change {}", change);
            final var rootNode = change.getRootNode();

            for (var changedChild : rootNode.getModifiedChildren()) {
                if (changedChild.getDataType().equals(KeyCredential.class)) {
                    final var dataAfter = rootNode.getDataAfter();
                    builder.pairs.clear();
                    if (dataAfter != null) {
                        dataAfter.nonnullKeyCredential().values()
                            .forEach(pair -> builder.pairs.put(pair.key().getKeyId(), pair));
                    }
                } else if (changedChild.getDataType().equals(PrivateKey.class)) {
                    onPrivateKeyChanged(builder.privateKeys, (DataObjectModification<PrivateKey>)changedChild);
                } else if (changedChild.getDataType().equals(TrustedCertificate.class)) {
                    onTrustedCertificateChanged(builder.trustedCertificates,
                        (DataObjectModification<TrustedCertificate>)changedChild);
                }
            }
        }
    }

    private static void onPrivateKeyChanged(final HashMap<String, PrivateKey> privateKeys,
            final DataObjectModification<PrivateKey> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final var privateKey = objectModification.getDataAfter();
                privateKeys.put(privateKey.getName(), privateKey);
                break;
            case DELETE:
                privateKeys.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }

    private static void onTrustedCertificateChanged(final HashMap<String, TrustedCertificate> trustedCertificates,
            final DataObjectModification<TrustedCertificate> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final var trustedCertificate = objectModification.getDataAfter();
                trustedCertificates.put(trustedCertificate.getName(), trustedCertificate);
                break;
            case DELETE:
                trustedCertificates.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }
}
