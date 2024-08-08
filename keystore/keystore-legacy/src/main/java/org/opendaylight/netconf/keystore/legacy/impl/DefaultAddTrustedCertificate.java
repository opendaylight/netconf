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
import java.util.ArrayList;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultAddTrustedCertificate extends AbstractEncryptingRpc implements AddTrustedCertificate {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAddTrustedCertificate.class);

    DefaultAddTrustedCertificate(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker, encryptionService);
    }

    @Override
    public ListenableFuture<RpcResult<AddTrustedCertificateOutput>> invoke(final AddTrustedCertificateInput input) {
        final var certs = input.getTrustedCertificate();
        if (certs == null || certs.isEmpty()) {
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).buildFuture();
        }

        LOG.debug("Updating trusted certificates: {}", certs);
        final var certificates = new ArrayList<TrustedCertificate>(certs.size());
        for (var certificate : certs.values()) {
            final var certName = certificate.getName();
            final TrustedCertificate cert;
            try {
                final var validCert = SecurityHelper.decodeCertificate(certificate.getCertificate());
                cert = new TrustedCertificateBuilder()
                    .setName(certName)
                    .setCertificate(encryptEncoded(validCert.getEncoded()))
                    .build();
            } catch (GeneralSecurityException e) {
                LOG.debug("Cannot encrypt certificate {}}", certificate, e);
                return returnFailed("Failed to encrypt certificate " + certName, e);
            } catch (IOException e) {
                LOG.debug("Cannot decode certificate {}}", certificate, e);
                return returnFailed("Failed to decode certificate " + certName, e);
            }
            certificates.add(cert);
        }

        final var tx = newTransaction();
        certificates.forEach(cert -> tx.put(LogicalDatastoreType.CONFIGURATION,
            InstanceIdentifier.create(Keystore.class).child(TrustedCertificate.class, cert.key()), cert));
        return tx.commit().transform(commitInfo -> {
            LOG.debug("Updated trusted certificates: {}", certs.keySet());
            return RpcResultBuilder.success(new AddTrustedCertificateOutputBuilder().build()).build();
        }, MoreExecutors.directExecutor());
    }
}