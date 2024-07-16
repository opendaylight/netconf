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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemovePrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemovePrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemovePrivateKeyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemovePrivateKeyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKeyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultRemovePrivateKey extends AbstractRpc implements RemovePrivateKey {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRemovePrivateKey.class);

    DefaultRemovePrivateKey(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public ListenableFuture<RpcResult<RemovePrivateKeyOutput>> invoke(final RemovePrivateKeyInput input) {
        final var names = input.getName();
        if (names == null || names.isEmpty()) {
            return RpcResultBuilder.success(new RemovePrivateKeyOutputBuilder().build()).buildFuture();
        }

        final var keys = names.stream().map(PrivateKeyKey::new).collect(Collectors.toSet());
        LOG.debug("Removing private keys: {}", keys);


        final var tx = newTransaction();
        for (var key : keys) {
            tx.delete(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(PrivateKey.class, key));
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Removed trusted keys: {}", keys);
            return RpcResultBuilder.success(new RemovePrivateKeyOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}