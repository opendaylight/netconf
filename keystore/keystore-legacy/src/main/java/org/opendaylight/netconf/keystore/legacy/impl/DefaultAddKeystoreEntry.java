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
import java.security.KeyPair;
import java.util.ArrayList;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddKeystoreEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddKeystoreEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddKeystoreEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredentialBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddKeystoreEntry extends AbstractEncryptingRpc implements AddKeystoreEntry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddKeystoreEntry.class);

    DefaultAddKeystoreEntry(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker, encryptionService);
    }

    @Override
    public ListenableFuture<RpcResult<AddKeystoreEntryOutput>> invoke(final AddKeystoreEntryInput input) {
        final var plain = input.getKeyCredential();
        if (plain == null || plain.isEmpty()) {
            return RpcResultBuilder.success(new AddKeystoreEntryOutputBuilder().build()).buildFuture();
        }

        LOG.debug("Adding keypairs: {}", plain);
        final var encrypted = new ArrayList<KeyCredential>(plain.size());
        for (var credential : plain.values()) {
            final var keyId = credential.getKeyId();

            final KeyPair keyPair;
            try {
                keyPair = new SecurityHelper().decodePrivateKey(credential.getPrivateKey(), credential.getPassphrase());
            } catch (IOException e) {
                LOG.debug("Cannot decode private key {}}", keyId, e);
                return returnFailed("Failed to decode private key " + keyId, e);
            }

            final var priv = keyPair.getPrivate();
            final byte[] encodedPriv;
            final byte[] encodedPub;
            try {
                encodedPriv = encryptEncoded(priv.getEncoded());
                encodedPub = encryptEncoded(keyPair.getPublic().getEncoded());
            } catch (GeneralSecurityException e) {
                LOG.debug("Cannot encrypt key credential {}}", credential, e);
                return returnFailed("Failed to encrypt key credential " + keyId, e);
            }

            encrypted.add(new KeyCredentialBuilder()
                .setKeyId(credential.getKeyId())
                .setAlgorithm(priv.getAlgorithm())
                .setPrivateKey(encodedPriv)
                .setPublicKey(encodedPub)
                .build());
        }

        final var tx = newTransaction();
        encrypted.forEach(keypair -> tx.put(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(Keystore.class).child(KeyCredential.class, keypair.key()), keypair));
        return tx.commit().transform(commitInfo -> {
            LOG.debug("Updated keypairs: {}", plain.keySet());
            return RpcResultBuilder.success(new AddKeystoreEntryOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}