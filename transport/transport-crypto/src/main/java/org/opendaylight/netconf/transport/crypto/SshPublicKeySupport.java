/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SshPublicKeyFormat;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * {@link PublicKeySupport} for {@link SshPublicKeyFormat}.
 */
@Singleton
@Component
@NonNullByDefault
public final class SshPublicKeySupport implements PublicKeySupport<SshPublicKeyFormat> {
    @Inject
    @Activate
    public SshPublicKeySupport() {
        // Nothing else
    }

    @Override
    public SshPublicKeyFormat format() {
        return SshPublicKeyFormat.VALUE;
    }

    @Override
    public PublicKey createKey(final String algorithm, final byte[] bytes) throws UnsupportedConfigurationException {
        try {
            return KeyFactory.getInstance(algorithm).generatePublic(new OpenSSHPublicKeySpec(bytes));
        } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new UnsupportedConfigurationException("Failed to create public key: " + e.getMessage(), e);
        }
    }
}
