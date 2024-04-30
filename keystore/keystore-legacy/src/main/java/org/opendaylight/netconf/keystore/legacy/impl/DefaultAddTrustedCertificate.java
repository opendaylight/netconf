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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.AddTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev231109.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddTrustedCertificate extends AbstractRpc implements AddTrustedCertificate {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddTrustedCertificate.class);
    private final SecurityHelper securityHelper = new SecurityHelper();

    DefaultAddTrustedCertificate(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public ListenableFuture<RpcResult<AddTrustedCertificateOutput>> invoke(final AddTrustedCertificateInput input) {
        final var certs = input.getTrustedCertificate();
        if (certs == null || certs.isEmpty()) {
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).buildFuture();
        }

        // Validate certificates
        for (var certificate : certs.values()) {
            try {
                securityHelper.generateCertificate(certificate.getCertificate().getBytes(StandardCharsets.UTF_8));
            } catch (GeneralSecurityException e) {
                LOG.debug("Failed to generate certificate for {}", certificate.getName(), e);
                throw new IllegalArgumentException("Failed to generate certificate for: " + certificate.getName(), e);
            }
        }

        LOG.debug("Updating trusted certificates: {}", certs);
        final var tx = newTransaction();
        for (var certificate : certs.values()) {
            final var base64certificate = new TrustedCertificateBuilder()
                .setName(certificate.getName())
                .setCertificate(certificate.getCertificate().getBytes(StandardCharsets.UTF_8))
                .build();

            tx.put(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(Keystore.class).child(TrustedCertificate.class, base64certificate.key()),
                    base64certificate);
        }

        return tx.commit().transform(commitInfo -> {
            LOG.debug("Updated trusted certificates: {}", certs.keySet());
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}