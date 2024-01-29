/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.Mutable;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract substrate for implementing security services based on the contents of {@link Keystore}.
 */
public abstract class AbstractNetconfKeystore {
    @NonNullByDefault
    protected record CertifiedPrivateKey(
            java.security.PrivateKey key,
            List<X509Certificate> certificateChain) implements Immutable {
        public CertifiedPrivateKey {
            requireNonNull(key);
            certificateChain = List.copyOf(certificateChain);
            if (certificateChain.isEmpty()) {
                throw new IllegalArgumentException("Certificate chain must not be empty");
            }
        }
    }

    @NonNullByDefault
    protected record State(
            Map<String, CertifiedPrivateKey> privateKeys,
            Map<String, X509Certificate> trustedCertificates) implements Immutable {
        public static final State EMPTY = new State(Map.of(), Map.of());

        public State {
            privateKeys = Map.copyOf(privateKeys);
            trustedCertificates = Map.copyOf(trustedCertificates);
        }
    }

    @NonNullByDefault
    private record ConfigState(
            Map<String, PrivateKey> privateKeys,
            Map<String, TrustedCertificate> trustedCertificates) implements Immutable {
        static final ConfigState EMPTY = new ConfigState(Map.of(), Map.of());

        ConfigState {
            privateKeys = Map.copyOf(privateKeys);
            trustedCertificates = Map.copyOf(trustedCertificates);
        }
    }

    @NonNullByDefault
    record ConfigStateBuilder(
            HashMap<String, PrivateKey> privateKeys,
            HashMap<String, TrustedCertificate> trustedCertificates) implements Mutable {
        ConfigStateBuilder {
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfKeystore.class);

    private final AtomicReference<@NonNull ConfigState> state = new AtomicReference<>(ConfigState.EMPTY);
    private final SecurityHelper securityHelper = new SecurityHelper();

    private @Nullable Registration configListener;

    protected final void start(final DataBroker dataBroker) {
        if (configListener == null) {
            configListener = dataBroker.registerTreeChangeListener(
                DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
                new ConfigListener(this));
        }
    }

    protected final void stop() {
        final var listener = configListener;
        if (listener != null) {
            configListener = null;
            listener.close();
            state.set(ConfigState.EMPTY);
        }
    }

    protected abstract void onStateUpdated(@NonNull State newState);

    final void runUpdate(final Consumer<@NonNull ConfigStateBuilder> task) {
        final var prevState = state.getAcquire();

        final var builder = new ConfigStateBuilder(new HashMap<>(prevState.privateKeys),
            new HashMap<>(prevState.trustedCertificates));
        task.accept(builder);
        final var newState = new ConfigState(builder.privateKeys, builder.trustedCertificates);

        // Careful application -- check if listener is still up and whether the state was not updated.
        if (configListener == null || state.compareAndExchangeRelease(prevState, newState) != prevState) {
            return;
        }

        Throwable failure = null;

        final var keys = Maps.<String, CertifiedPrivateKey>newHashMapWithExpectedSize(newState.privateKeys.size());
        for (var key : newState.privateKeys.values()) {
            final var keyName = key.requireName();

            final byte[] keyBytes;
            try {
                keyBytes = base64Decode(key.requireData());
            } catch (IllegalArgumentException e) {
                LOG.debug("Failed to decode private key {}", keyName, e);
                failure = updateFailure(failure, e);
                continue;
            }

            final java.security.PrivateKey privateKey;
            try {
                privateKey = securityHelper.generatePrivateKey(keyBytes);
            } catch (GeneralSecurityException e) {
                LOG.debug("Failed to generate key for {}", keyName, e);
                failure = updateFailure(failure, e);
                continue;
            }

            final var certChain = key.requireCertificateChain();
            if (certChain.isEmpty()) {
                LOG.debug("Key {} has an empty certificate chain", keyName);
                failure = updateFailure(failure,
                    new IllegalArgumentException("Empty certificate chain for private key " + keyName));
                continue;
            }

            final var certs = new ArrayList<X509Certificate>(certChain.size());
            for (int i = 0, size = certChain.size(); i < size; i++) {
                final byte[] bytes;
                try {
                    bytes = base64Decode(certChain.get(i));
                } catch (IllegalArgumentException e) {
                    LOG.debug("Failed to decode certificate chain item {} for private key {}", i, keyName, e);
                    failure = updateFailure(failure, e);
                    continue;
                }

                final X509Certificate x509cert;
                try {
                    x509cert = securityHelper.generateCertificate(bytes);
                } catch (GeneralSecurityException e) {
                    LOG.debug("Failed to generate certificate chain item {} for private key {}", i, keyName, e);
                    failure = updateFailure(failure, e);
                    continue;
                }

                certs.add(x509cert);
            }

            keys.put(keyName, new CertifiedPrivateKey(privateKey, certs));
        }

        final var certs = Maps.<String, X509Certificate>newHashMapWithExpectedSize(newState.trustedCertificates.size());
        for (var cert : newState.trustedCertificates.values()) {
            final var certName = cert.requireName();

            final byte[] bytes;
            try {
                bytes = base64Decode(cert.requireCertificate());
            } catch (IllegalArgumentException e) {
                LOG.debug("Failed to decode trusted certificate {}", certName, e);
                failure = updateFailure(failure, e);
                continue;
            }

            final X509Certificate x509cert;
            try {
                x509cert = securityHelper.generateCertificate(bytes);
            } catch (GeneralSecurityException e) {
                LOG.debug("Failed to generate certificate for {}", certName, e);
                failure = updateFailure(failure, e);
                continue;
            }

            certs.put(certName, x509cert);
        }

        if (failure != null) {
            LOG.warn("New configuration is invalid, not applying it", failure);
            return;
        }

        onStateUpdated(new State(keys, certs));

        // FIXME: tickle operational updater (which does not exist yet)
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

    private static @NonNull Throwable updateFailure(final @Nullable Throwable failure, final @NonNull Exception ex) {
        if (failure != null) {
            failure.addSuppressed(ex);
            return failure;
        }
        return ex;
    }
}
