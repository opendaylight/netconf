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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddKeystoreEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.keystore.entry.KeyCredentialBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddKeystoreEntry extends AbstractRpc implements AddKeystoreEntry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddKeystoreEntry.class);

    private final AAAEncryptionService encryptionService;

    DefaultAddKeystoreEntry(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
    }

    @Override
    public ListenableFuture<RpcResult<AddKeystoreEntryOutput>> invoke(final AddKeystoreEntryInput input) {
        final var plain = input.getKeyCredential();
        if (plain == null || plain.isEmpty()) {
            return RpcResultBuilder.success(new AddKeystoreEntryOutputBuilder().build()).buildFuture();
        }

        LOG.debug("Adding keypairs: {}", plain);
        final var encrypted = new ArrayList<KeyCredential>(plain.size());
        final var invalidKeys = new ArrayList<String>();

        for (var credential : plain.values()) {
            try (var keyReader = new PEMParser(new StringReader(credential.getPrivateKey().replace("\\n", "\n")))) {
                final var obj = keyReader.readObject();

                if (!(obj instanceof PEMEncryptedKeyPair || obj instanceof PEMKeyPair)) {
                    throw new IOException();
                }
            } catch (IOException e) {
                LOG.debug("Failed to process key: {}", credential.getKeyId());
                invalidKeys.add(credential.getKeyId());
            }
        }

        if (!invalidKeys.isEmpty()){
            return RpcResultBuilder.<AddKeystoreEntryOutput>failed()
                .withError(ErrorType.APPLICATION, "Failed to process invalid keys: "+ invalidKeys)
                .buildFuture();
        }

        for (var credential : plain.values()) {
            final var keyId = credential.getKeyId();
            try {
                encrypted.add(new KeyCredentialBuilder()
                    .setKeyId(keyId)
                    .setPrivateKey(encryptToBytes(credential.getPrivateKey()))
                    .setPassphrase(encryptToBytes(credential.getPassphrase()))
                    .build());
            } catch (GeneralSecurityException e) {
                LOG.debug("Cannot decrypt key credential {}}", credential, e);
                return RpcResultBuilder.<AddKeystoreEntryOutput>failed()
                    .withError(ErrorType.APPLICATION, "Failed to decrypt key " + keyId, e)
                    .buildFuture();
            }
        }

        final var tx = newTransaction();
        for (var keypair : encrypted) {
            tx.put(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(KeyCredential.class, keypair.key()), keypair);
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Updated keypairs: {}", plain.keySet());
            return RpcResultBuilder.success(new AddKeystoreEntryOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }

    private byte[] encryptToBytes(final String plain) throws GeneralSecurityException {
        return Base64.getEncoder().encode(encryptionService.encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }
}