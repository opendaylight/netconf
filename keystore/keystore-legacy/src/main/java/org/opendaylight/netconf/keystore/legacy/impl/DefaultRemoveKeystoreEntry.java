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
import java.util.stream.Collectors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveKeystoreEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveKeystoreEntryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveKeystoreEntryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveKeystoreEntryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredentialKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultRemoveKeystoreEntry extends AbstractRpc implements RemoveKeystoreEntry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRemoveKeystoreEntry.class);

    DefaultRemoveKeystoreEntry(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public ListenableFuture<RpcResult<RemoveKeystoreEntryOutput>> invoke(final RemoveKeystoreEntryInput input) {
        final var keyIds = input.getKeyId();
        if (keyIds == null || keyIds.isEmpty()) {
            return RpcResultBuilder.success(new RemoveKeystoreEntryOutputBuilder().build()).buildFuture();
        }

        final var keys = keyIds.stream().map(KeyCredentialKey::new).collect(Collectors.toSet());
        LOG.debug("Removing keypairs: {}", keys);
        final var tx = newTransaction();
        for (var key : keys) {
            tx.delete(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(KeyCredential.class, key));
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Removed keypairs: {}", keys);
            return RpcResultBuilder.success(new RemoveKeystoreEntryOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}