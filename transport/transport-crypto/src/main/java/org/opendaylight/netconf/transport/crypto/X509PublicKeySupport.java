/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.io.IOException;
import java.security.PublicKey;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * {@link PublicKeySupport} for {@link SubjectPublicKeyInfoFormat}.
 */
@Singleton
@Component
@NonNullByDefault
public final class X509PublicKeySupport implements PublicKeySupport<SubjectPublicKeyInfoFormat> {
    @Inject
    @Activate
    public X509PublicKeySupport() {
        // Nothing else
    }

    @Override
    public SubjectPublicKeyInfoFormat format() {
        return SubjectPublicKeyInfoFormat.VALUE;
    }

    @Override
    public PublicKey createKey(final String algorithm, final byte[] bytes) throws UnsupportedConfigurationException {
        final SubjectPublicKeyInfo spki;
        try {
            spki = SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(bytes));
        } catch (IllegalArgumentException | IOException e) {
            throw new UnsupportedConfigurationException("Failed to parse SubjectPublicKeyInfo: " + e.getMessage(), e);
        }

        try {
            return BouncyCastleProvider.getPublicKey(spki);
        } catch (IOException e) {
            throw new UnsupportedConfigurationException("Failed to parse public key: " + e.getMessage(), e);
        }
    }
}
