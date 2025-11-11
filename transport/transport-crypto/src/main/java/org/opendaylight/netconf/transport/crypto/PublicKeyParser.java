/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import com.google.common.annotations.Beta;
import java.io.IOException;
import java.security.PublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;

/**
 * Support for parsing public/private key pairs encoded as {@link PublicKeyGrouping}.
 */
@Beta
@NonNullByDefault
public final class PublicKeyParser {
    private PublicKeyParser() {
        // Hidden on purpose
    }

    public static PublicKey parsePublicKey(final PublicKeyGrouping publicKey) throws UnsupportedConfigurationException {
        final var format = publicKey.getPublicKeyFormat();
        if (format == null) {
            throw new UnsupportedConfigurationException("Missing public key format");
        }
        final var body = publicKey.getPublicKey();
        if (body == null) {
            throw new UnsupportedConfigurationException("Missing public key");
        }
        return parsePublicKey(format, body);
    }

    static PublicKey parsePublicKey(final PublicKeyFormat format, final byte[] body)
            throws UnsupportedConfigurationException {
        final SubjectPublicKeyInfo spki;
        try {
            spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(parseKeyParam(format, body));
        } catch (IOException e) {
            throw new UnsupportedConfigurationException("Cannot parse public key: " + e.getMessage(), e);
        }

        final PublicKey publicKey;
        try {
            publicKey = BouncyCastleProvider.getPublicKey(spki);
        } catch (IOException e) {
            throw new UnsupportedConfigurationException("Cannot extract public key: " + e.getMessage(), e);
        }
        if (publicKey == null) {
            throw new UnsupportedConfigurationException("Unsupported public key algorithm " + spki.getAlgorithm());
        }
        return publicKey;
    }

    private static AsymmetricKeyParameter parseKeyParam(final PublicKeyFormat format, final byte[] body)
            throws UnsupportedConfigurationException {
        if (SubjectPublicKeyInfoFormat.VALUE.equals(format)) {
            try {
                return PublicKeyFactory.createKey(body);
            } catch (IOException e) {
                throw new UnsupportedConfigurationException("Cannot parse X.509 public key: " + e.getMessage(), e);
            }
        }
        if (SshPublicKeyFormat.VALUE.equals(format)) {
            try {
                return OpenSSHPublicKeyUtil.parsePublicKey(body);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new UnsupportedConfigurationException("Cannot parse SSH public key: " + e.getMessage(), e);
            }
        }
        throw new UnsupportedConfigurationException("Unsupported public key format " + format);
    }
}
