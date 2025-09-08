/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import com.google.common.annotations.Beta;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.X509;

/**
 * Support for certificates in their various forms. This class is considered a Beta-level API for internal use. It can
 * change incompatibly between minor releases.
 *
 * @since 10.0.1
 */
@Beta
@NonNullByDefault
public final class CertificateSupport {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private CertificateSupport() {
        // Hidden on purpose
    }

    /**
     * Parse an X.509 certificate.
     *
     * @param certificate the certificate
     * @return A {@link Certificate}
     * @throws UnsupportedConfigurationException when the certificate cannot be parsed
     */
    public static Certificate parseCertificate(final EndEntityCertCms certificate)
            throws UnsupportedConfigurationException {
        return parseX509Certificate(certificate.getValue());
    }

    /**
     * Parse an X.509 certificate.
     *
     * @param certificate the certificate
     * @return A {@link Certificate}
     * @throws UnsupportedConfigurationException when the certificate cannot be parsed
     */
    public static Certificate parseCertificate(final TrustAnchorCertCms certificate)
            throws UnsupportedConfigurationException {
        return parseX509Certificate(certificate.getValue());
    }

    /**
     * Parse an X.509 certificate.
     *
     * @param certificate the certificate
     * @return A {@link Certificate}
     * @throws UnsupportedConfigurationException when the certificate cannot be parsed
     */
    public static Certificate parseCertificate(final X509 certificate)
            throws UnsupportedConfigurationException {
        return parseX509Certificate(certificate.getValue());
    }

    private static Certificate parseX509Certificate(final byte[] certificateBytes)
            throws UnsupportedConfigurationException {
        final CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        } catch (CertificateException | NoSuchProviderException e) {
            throw new UnsupportedConfigurationException("X.509 certificates are not supported", e);
        }

        try (var in = new ByteArrayInputStream(certificateBytes)) {
            return factory.generateCertificate(in);
        } catch (CertificateException | IOException e) {
            throw new UnsupportedConfigurationException(
                "Cannot parse certificate " + Base64.getEncoder().encodeToString(certificateBytes), e);
        }
    }
}