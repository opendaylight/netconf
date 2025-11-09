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
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;

/**
 * {@link PublicKeySupport} for {@link SshPublicKeyFormat}.
 */
//FIXME: turn this into a component once we have Dagger
@NonNullByDefault
final class SshPublicKeySupport implements PublicKeySupport<SshPublicKeyFormat> {
    SshPublicKeySupport() {
        // Nothing else
    }

    @Override
    public SshPublicKeyFormat format() {
        return SshPublicKeyFormat.VALUE;
    }

    @Override
    public PublicKey createKey(final String algorithm, final byte[] bytes) throws UnsupportedConfigurationException {
        final SubjectPublicKeyInfo spki;
        try {
            spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(OpenSSHPublicKeyUtil.parsePublicKey(bytes));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new UnsupportedConfigurationException("Failed to parse SubjectPublicKeyInfo: " + e.getMessage(), e);
        }

        final PublicKey publicKey;
        try {
            publicKey = BouncyCastleProvider.getPublicKey(spki);
        } catch (IOException e) {
            throw new UnsupportedConfigurationException("Failed to parse public key: " + e.getMessage(), e);
        }
        if (publicKey == null) {
            throw new UnsupportedConfigurationException("Unsupported public key type " + spki.getAlgorithm());
        }
        return publicKey;
    }
}
