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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.RemoveTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultRemoveTrustedCertificate extends AbstractRpc implements RemoveTrustedCertificate {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRemoveTrustedCertificate.class);

    DefaultRemoveTrustedCertificate(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public ListenableFuture<RpcResult<RemoveTrustedCertificateOutput>> invoke(
            final RemoveTrustedCertificateInput input) {
        final var names = input.getName();
        if (names == null || names.isEmpty()) {
            return RpcResultBuilder.success(new RemoveTrustedCertificateOutputBuilder().build()).buildFuture();
        }

        final var keys = names.stream().map(TrustedCertificateKey::new).collect(Collectors.toSet());
        LOG.debug("Removing trusted certificates: {}", keys);
        final var tx = newTransaction();
        for (var key : keys) {
            tx.delete(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(TrustedCertificate.class, key));
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Removed trusted certificates: {}", keys);
            return RpcResultBuilder.success(new RemoveTrustedCertificateOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}