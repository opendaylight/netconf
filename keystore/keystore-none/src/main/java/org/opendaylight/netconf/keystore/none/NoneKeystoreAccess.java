/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.none;

import java.security.KeyPair;
import javax.crypto.SecretKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.osgi.service.component.annotations.Component;

/**
 * A {@link KeystoreAccess} for deployments without a central keystore. It contains no keys.
 *
 * <p>It is registered with the lowest service ranking, so that any real keystore implementation takes precedence
 * once it is installed.
 */
@NonNullByDefault
@Component(service = KeystoreAccess.class, property = "service.ranking:Integer=-2147483648")
public final class NoneKeystoreAccess implements KeystoreAccess {
    @Override
    public @Nullable KeyPair lookupAsymmetric(final String name) {
        return null;
    }

    @Override
    public @Nullable SecretKey lookupSymmetric(final String name) {
        return null;
    }
}
