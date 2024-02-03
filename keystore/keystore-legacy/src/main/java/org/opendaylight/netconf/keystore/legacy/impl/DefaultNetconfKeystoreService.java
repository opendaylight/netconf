/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.keystore.legacy.CertifiedPrivateKey;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystore;
import org.opendaylight.netconf.keystore.legacy.NetconfKeystoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.concepts.AbstractObjectRegistration;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.Mutable;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract substrate for implementing security services based on the contents of {@link Keystore}.
 */
@Singleton
@Component(service = NetconfKeystoreService.class)
public final class DefaultNetconfKeystoreService implements NetconfKeystoreService, AutoCloseable {
    @NonNullByDefault
    private record ConfigState(
            Map<String, PrivateKey> privateKeys,
            Map<String, TrustedCertificate> trustedCertificates,
            Map<String, KeyCredential> credentials) implements Immutable {
        static final ConfigState EMPTY = new ConfigState(Map.of(), Map.of(), Map.of());

        ConfigState {
            privateKeys = Map.copyOf(privateKeys);
            trustedCertificates = Map.copyOf(trustedCertificates);
            credentials = Map.copyOf(credentials);
        }
    }

    @NonNullByDefault
    record ConfigStateBuilder(
            HashMap<String, PrivateKey> privateKeys,
            HashMap<String, TrustedCertificate> trustedCertificates,
            HashMap<String, KeyCredential> credentials) implements Mutable {
        ConfigStateBuilder {
            requireNonNull(privateKeys);
            requireNonNull(trustedCertificates);
            requireNonNull(credentials);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNetconfKeystoreService.class);

    private final Set<ObjectRegistration<Consumer<NetconfKeystore>>> consumers = ConcurrentHashMap.newKeySet();
    private final AtomicReference<NetconfKeystore> keystore = new AtomicReference<>(null);
    private final AtomicReference<ConfigState> config = new AtomicReference<>(ConfigState.EMPTY);
    private final SecurityHelper securityHelper = new SecurityHelper();
    private final AAAEncryptionService encryptionService;
    private final Registration configListener;
    private final Registration rpcSingleton;

    @Inject
    @Activate
    public DefaultNetconfKeystoreService(@Reference final DataBroker dataBroker,
            @Reference final RpcProviderService rpcProvider,
            @Reference final ClusterSingletonServiceProvider cssProvider,
            @Reference final AAAEncryptionService encryptionService) {
        this.encryptionService = requireNonNull(encryptionService);
        configListener = dataBroker.registerTreeChangeListener(
            DataTreeIdentifier.of(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
            new ConfigListener(this));
        rpcSingleton = cssProvider.registerClusterSingletonService(
            new RpcSingleton(dataBroker, rpcProvider, encryptionService));

        // FIXME: create an operation datastore updater and register it as a consumer

        LOG.info("NETCONF keystore service started");
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        rpcSingleton.close();
        configListener.close();
        LOG.info("NETCONF keystore service stopped");
    }

    @Override
    public Registration registerKeystoreConsumer(final Consumer<NetconfKeystore> consumer) {
        final var reg = new AbstractObjectRegistration<>(consumer) {
            @Override
            protected void removeRegistration() {
                consumers.remove(this);
            }
        };

        consumers.add(reg);
        final var ks = keystore.getAcquire();
        if (ks != null) {
            consumer.accept(ks);
        }
        return reg;
    }

    void runUpdate(final Consumer<@NonNull ConfigStateBuilder> task) {
        final var prevState = config.getAcquire();

        final var builder = new ConfigStateBuilder(new HashMap<>(prevState.privateKeys),
            new HashMap<>(prevState.trustedCertificates), new HashMap<>(prevState.credentials));
        task.accept(builder);
        final var newState = new ConfigState(builder.privateKeys, builder.trustedCertificates, builder.credentials);

        // Careful application -- check if listener is still up and whether the state was not updated.
        if (configListener == null || config.compareAndExchangeRelease(prevState, newState) != prevState) {
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

        final var creds = Maps.<String, KeyPair>newHashMapWithExpectedSize(newState.credentials.size());
        for (var cred : newState.credentials.values()) {
            final var keyId = cred.requireKeyId();
            final String passPhrase;
            try {
                passPhrase = decryptString(requireNonNullElse(cred.getPassphrase(), ""));
            } catch (GeneralSecurityException e) {
                LOG.debug("Failed to decrypt pass phrase for {}", keyId, e);
                failure = updateFailure(failure, e);
                continue;
            }

            final String privateKey;
            try {
                privateKey = decryptString(cred.getPrivateKey());
            } catch (GeneralSecurityException e) {
                LOG.debug("Failed to decrypt private key for {}", keyId, e);
                failure = updateFailure(failure, e);
                continue;
            }

            final KeyPair keyPair;
            try {
                keyPair = securityHelper.decodePrivateKey(privateKey, passPhrase);
            } catch (IOException e) {
                LOG.debug("Failed to decode key pair for {}", keyId, e);
                failure = updateFailure(failure, e);
                continue;
            }

            creds.put(keyId, keyPair);
        }

        if (failure != null) {
            LOG.warn("New configuration is invalid, not applying it", failure);
            return;
        }

        final var newKeystore = new NetconfKeystore(keys, certs, creds);
        keystore.setRelease(newKeystore);
        consumers.forEach(consumer -> consumer.getInstance().accept(newKeystore));
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
    }

    private String decryptString(final String encrypted) throws GeneralSecurityException {
        return new String(encryptionService.decrypt(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }

    private static @NonNull Throwable updateFailure(final @Nullable Throwable failure, final @NonNull Exception ex) {
        if (failure != null) {
            failure.addSuppressed(ex);
            return failure;
        }
        return ex;
    }
}
