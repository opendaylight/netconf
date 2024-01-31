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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddTrustedCertificate extends AbstractRpc implements AddTrustedCertificate {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddTrustedCertificate.class);

    DefaultAddTrustedCertificate(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public ListenableFuture<RpcResult<AddTrustedCertificateOutput>> invoke(final AddTrustedCertificateInput input) {
        final var certs = input.getTrustedCertificate();
        if (certs == null || certs.isEmpty()) {
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).buildFuture();
        }

        LOG.debug("Updating trusted certificates: {}", certs);
        final var tx = newTransaction();
        for (var certificate : certs.values()) {
            tx.put(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(TrustedCertificate.class, certificate.key()),
                certificate);
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Updated trusted certificates: {}", certs.keySet());
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}