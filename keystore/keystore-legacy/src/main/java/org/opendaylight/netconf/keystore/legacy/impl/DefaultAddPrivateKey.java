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
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddPrivateKeyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109._private.keys.PrivateKeyBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

final class DefaultAddPrivateKey extends AbstractRpc implements AddPrivateKey {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddPrivateKey.class);

    DefaultAddPrivateKey(final DataBroker dataBroker) {
        super(dataBroker);
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
            final var base64key = new PrivateKeyBuilder()
                .setName(key.getName())
                .setData(key.getData().getBytes(StandardCharsets.US_ASCII))
                .setCertificateChain(key.getCertificateChain().stream()
                    .map(cert -> cert.getBytes(StandardCharsets.US_ASCII)).toList())
                .build();

            tx.put(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(PrivateKey.class, base64key.key()), base64key);
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Added private keys: {}", keys.keySet());
            return RpcResultBuilder.success(new AddPrivateKeyOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}