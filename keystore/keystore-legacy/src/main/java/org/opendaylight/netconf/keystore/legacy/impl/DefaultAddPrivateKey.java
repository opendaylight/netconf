/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKeyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKeyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKeyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddPrivateKey extends AbstractEncryptingRpc implements AddPrivateKey {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddPrivateKey.class);

    DefaultAddPrivateKey(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker, encryptionService);
    }

    @Override
    public ListenableFuture<RpcResult<AddPrivateKeyOutput>> invoke(final AddPrivateKeyInput input) {
        final var keys = input.getPrivateKey();
        if (keys == null || keys.isEmpty()) {
            return RpcResultBuilder.success(new AddPrivateKeyOutputBuilder().build()).buildFuture();
        }

        LOG.debug("Adding private keys: {}", keys);
        final var privateKeys = new ArrayList<PrivateKey>(keys.size());
        for (var key : keys.values()) {
            final java.security.PrivateKey validPrivateKey;
            try {
                validPrivateKey = new SecurityHelper().decodePrivateKey(key.getData(), null).getPrivate();
            } catch (IOException e) {
                LOG.debug("Cannot decode private key {}", key, e);
                return returnFailed("Failed to decode private key " + key.getName(), e);
            }

            final byte[] encryptedPrivateKey;
            try {
                encryptedPrivateKey = encryptEncoded(validPrivateKey.getEncoded());
            } catch (GeneralSecurityException e) {
                LOG.debug("Cannot encrypt private key {}", key, e);
                return returnFailed("Failed to encrypt private key " + key.getName(), e);
            }

            final List<byte[]> encryptedCerts;
            final var certChain = key.getCertificateChain();
            if (certChain != null) {
                encryptedCerts = new ArrayList<>(certChain.size());
                for (var cert : certChain) {
                    final X509Certificate decoded;
                    try {
                        decoded = SecurityHelper.decodeCertificate(cert);
                    } catch (IOException | GeneralSecurityException e) {
                        return returnFailed("Cannot decode certificate " + cert, e);
                    }

                    final byte[] encoded;
                    try {
                        encoded = decoded.getEncoded();
                    } catch (CertificateEncodingException e) {
                        return returnFailed("Cannot re-encode certificate " + decoded, e);
                    }

                    final byte[] encrypted;
                    try {
                        encrypted = encryptEncoded(encoded);
                    } catch (GeneralSecurityException e) {
                        return returnFailed("Cannot encrypt certificate " + decoded, e);
                    }

                    encryptedCerts.add(encrypted);
                }
            } else {
                encryptedCerts = List.of();
            }

            privateKeys.add(new PrivateKeyBuilder()
                .setName(key.getName())
                .setData(encryptedPrivateKey)
                .setAlgorithm(validPrivateKey.getAlgorithm())
                .setCertificateChain(encryptedCerts)
                .build());
        }

        final var tx = newTransaction();
        privateKeys.forEach(privateKey -> tx.put(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(Keystore.class).child(PrivateKey.class, privateKey.key()), privateKey));
        return tx.commit().transform(commitInfo -> {
            LOG.debug("Added private keys: {}", keys.keySet());
            return RpcResultBuilder.success(new AddPrivateKeyOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}