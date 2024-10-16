/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.api;

import java.security.KeyPair;
import javax.crypto.SecretKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.keystore.grouping.asymmetric.keys.AsymmetricKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.keystore.grouping.asymmetric.keys.AsymmetricKeyKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.keystore.grouping.symmetric.keys.SymmetricKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.keystore.grouping.symmetric.keys.SymmetricKeyKey;

/**
 * Access to the contents of a keystore.
 */
@NonNullByDefault
public interface KeystoreAccess {
    /**
     * Look up an {@link AsymmetricKey} in its native Java form.
     *
     * @param key the name of an asymmetric key, encapsulated
     * @return A {@link KeyPair}
     */
    default @Nullable KeyPair lookup(final AsymmetricKeyKey key) {
        return lookupAsymmetric(key.getName());
    }

    /**
     * Look up an {@link AsymmetricKey} in its native Java form.
     *
     * @param key the name of an asymmetric key, encapsulated
     * @return A {@link KeyPair}
     */
    default @Nullable SecretKey lookup(final SymmetricKeyKey key) {
        return lookupSymmetric(key.getName());
    }

    /**
     * Look up an {@link AsymmetricKey} in its native Java form.
     *
     * @param name the name of an asymmetric key
     * @return A {@link KeyPair}
     */
    @Nullable KeyPair lookupAsymmetric(String name);

    /**
     * Look up an {@link SymmetricKey} in its native Java form.
     *
     * @param name the name of an asymmetric key
     * @return A {@link SecretKey}
     */
    @Nullable SecretKey lookupSymmetric(String name);
}
