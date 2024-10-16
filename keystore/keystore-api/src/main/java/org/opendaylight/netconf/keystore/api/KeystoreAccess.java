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

/**
 * Access to the contents of a keystore.
 */
@NonNullByDefault
public interface KeystoreAccess {
    /**
     * Return a {@link KeystoreAccess} which contains no keys. All its lookups return {@code null}.
     *
     * @return an empty {@link KeystoreAccess}
     */
    static KeystoreAccess empty() {
        return EmptyKeystoreAccess.INSTANCE;
    }

    /**
     * Look up an asymmetric key in its native Java form.
     *
     * @param name the name of an asymmetric key
     * @return A {@link KeyPair}, {@code null} if not found
     */
    @Nullable KeyPair lookupAsymmetric(String name);

    /**
     * Look up a symmetric key in its native Java form.
     *
     * @param name the name of a symmetric key
     * @return A {@link SecretKey}, {@code null} if not found
     */
    @Nullable SecretKey lookupSymmetric(String name);
}
