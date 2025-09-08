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
import java.security.Provider;
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

/**
 * Support for certificates in their various forms. This class is considered a Beta-level API for internal use. It can
 * change incompatibly between minor releases.
 *
 * @since 10.0.1
 */
@Beta
@NonNullByDefault
public final class CertificateSupport {
    private static final Provider BCPROV;

    static {
        var existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        BCPROV = existing != null ? existing : new BouncyCastleProvider();
    }

    private CertificateSupport() {
        // Hidden on purpose
    }

    // FIXME: support also degenerate cases:

    //    typedef trust-anchor-cert-x509 {
    //      type x509;
    //      description
    //        "A Certificate structure that MUST encode a self-signed
    //         root certificate.";
    //    }
    //
    //    typedef end-entity-cert-x509 {
    //      type x509;
    //      description
    //        "A Certificate structure that MUST encode a certificate
    //         that is neither self-signed nor has Basic constraint
    //         CA true.";
    //    }

    /**
     * Parse an X.509 certificate.
     *
     * @param certificate the certificate
     * @return A {@link Certificate}
     * @throws UnsupportedConfigurationException when the certificate cannot be parsed
     */
    public static Certificate parseCertificate(final EndEntityCertCms certificate)
            throws UnsupportedConfigurationException {
        // FIXME: this not right as the definition is:

        //        typedef end-entity-cert-cms {
        //            type signed-data-cms;
        //            description
        //              "A CMS SignedData structure that MUST contain the end-entity
        //               certificate itself and MAY contain any number
        //               of intermediate certificates leading up to a trust
        //               anchor certificate.  The trust anchor certificate
        //               MAY be included as well.
        //
        //               The CMS MUST contain a single end-entity certificate.
        //               The CMS MUST NOT contain any spurious certificates.
        //
        //               This CMS structure MAY (as applicable where this type is
        //               used) also contain suitably fresh (as defined by local
        //               policy) revocation objects with which the device can
        //               verify the revocation status of the certificates.
        //
        //               This CMS encodes the degenerate form of the SignedData
        //               structure (RFC 5652, Section 5.2) that is commonly
        //               used to disseminate X.509 certificates and revocation
        //               objects (RFC 5280).";
        //
        //            reference
        //              "RFC 5280:
        //                 Internet X.509 Public Key Infrastructure Certificate
        //                 and Certificate Revocation List (CRL) Profile
        //               RFC 5652:
        //                 Cryptographic Message Syntax (CMS)";
        //          }

z        return parseX509Certificate(certificate.getValue());
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
        // FIXME: this is not right as the definition is:
        //
        //    typedef trust-anchor-cert-cms {
        //        type signed-data-cms;
        //        description
        //          "A CMS SignedData structure that MUST contain the chain of
        //           X.509 certificates needed to authenticate the certificate
        //           presented by a client or end entity.
        //
        //           The CMS MUST contain only a single chain of certificates.
        //           The client or end-entity certificate MUST only authenticate
        //           to the last intermediate CA certificate listed in the chain.
        //
        //           In all cases, the chain MUST include a self-signed root
        //           certificate.  In the case where the root certificate is
        //           itself the issuer of the client or end-entity certificate,
        //           only one certificate is present.
        //
        //           This CMS structure MAY (as applicable where this type is
        //           used) also contain suitably fresh (as defined by local
        //           policy) revocation objects with which the device can
        //           verify the revocation status of the certificates.
        //
        //           This CMS encodes the degenerate form of the SignedData
        //           structure (RFC 5652, Section 5.2) that is commonly used
        //           to disseminate X.509 certificates and revocation objects
        //           (RFC 5280).";
        //        reference
        //          "RFC 5280:
        //             Internet X.509 Public Key Infrastructure Certificate
        //             and Certificate Revocation List (CRL) Profile
        //           RFC 5652:
        //             Cryptographic Message Syntax (CMS)";
        //      }
        //
        //  So this method needs to actually:
        //    - parse multiple certificates
        //    - check if they form a single chain, as explained in
        //      https://stackoverflow.com/questions/72044547/how-is-an-ssl-certificate-chain-bundle-arranged
        //    - for bonus points: discovered unordered chains
        //    - report any certificates (CRLs or otherwise) which are not part of the chain
        //
        //  At the end of the day we should produce:
        //
        //  record TrustAnchorCertificates(
        //    List<Certificate> chain; // the certificate chain, starting with the entity and ending with the root
        //    List<Certificate> extra; // certificates outside of the chain, including CRLs
        //  );

        return parseX509Certificate(certificate.getValue());
    }

    private static Certificate parseX509Certificate(final byte[] certificateBytes)
            throws UnsupportedConfigurationException {
        final CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509", BCPROV);
        } catch (CertificateException e) {
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