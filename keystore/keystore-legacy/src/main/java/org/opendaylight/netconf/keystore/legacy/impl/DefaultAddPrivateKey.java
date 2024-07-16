/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
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

final class DefaultAddPrivateKey extends AbstractRpc implements AddPrivateKey {
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
        final var tx = newTransaction();
        for (var key : keys.values()) {
            final PrivateKey privateKey;
            try {
                final var validPrivateKey = new SecurityHelper()
                    .decodePrivateKey(key.getData(), null)
                    .getPrivate();
                final var validEncodedCerts = requireNonNull(key.getCertificateChain()).stream()
                    .map(cert -> {
                        try {
                            return new SecurityHelper().decodeCertificate(cert);
                        } catch (GeneralSecurityException | IOException e) {
                            LOG.error("Cannot decode certificate {}", cert, e);
                            throw new RuntimeException("Cannot decode certificate " + cert, e);
                        }
                    })
                    .map(x509Certificate -> {
                        try {
                            return x509Certificate.getEncoded();
                        } catch (CertificateEncodingException e) {
                            LOG.error("Cannot encode certificate {}", x509Certificate, e);
                            throw new RuntimeException("Cannot encode certificate " + x509Certificate, e);
                        }
                    })
                    .toList();

                privateKey = new PrivateKeyBuilder()
                    .setName(key.getName())
                    .setData(encryptEncoded(validPrivateKey.getEncoded()))
                    .setAlgorithm(validPrivateKey.getAlgorithm())
                    .setCertificateChain(validEncodedCerts.stream().map(cert -> {
                        try {
                            return encryptEncoded(cert);
                        } catch (GeneralSecurityException e) {
                            LOG.error("Cannot encrypt certificate {}", cert, e);
                            throw new RuntimeException("Cannot encrypt certificate " + cert, e);
                        }
                    }).toList())
                    .build();
            } catch (IOException e) {
                LOG.debug("Cannot decode private key {}", key, e);
                return returnFailed("Failed to decode private key " + key.getName(), e);
            } catch (GeneralSecurityException e) {
                LOG.debug("Cannot encrypt private key {}", key, e);
                return returnFailed("Failed to encrypt private key " + key.getName(), e);
            }

            tx.put(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(PrivateKey.class, privateKey.key()), privateKey);
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Added private keys: {}", keys.keySet());
            return RpcResultBuilder.success(new AddPrivateKeyOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}