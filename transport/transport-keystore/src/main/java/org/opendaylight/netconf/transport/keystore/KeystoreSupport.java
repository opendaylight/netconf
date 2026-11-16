/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.keystore;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;

/**
 * Support for parsing {@code ietf-keystore.yang} constructs.
 */
@NonNullByDefault
public sealed interface KeystoreSupport permits DefaultKeystoreSupport {
    /**
     * {@return the AsymmetricKeyParser}
     */
    AsymmetricKeyParser asymmetric();

    /**
     * {@return an instance supporting only inline definitions}
     */
    static KeystoreSupport inlineOnly() {
        return InlineAsymmetricKeyParser.SUPPORT;
    }

    /**
     * {@return an instance supporting inline definitions and central keystore references}
     *
     * @param central {@link KeystoreAccess} to use for resolution of central keystore references
     */
    static KeystoreSupport inlineAndCentral(final KeystoreAccess central) {
        return new DefaultKeystoreSupport(new KeystoreAsymmetricKeyParser(central));
    }
}
