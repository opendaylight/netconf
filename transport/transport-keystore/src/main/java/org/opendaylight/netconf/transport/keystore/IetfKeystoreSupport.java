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
 * Support for accessing {@code ietf-keystore.yang} constructs.
 */
@NonNullByDefault
public sealed interface IetfKeystoreSupport permits DefaultIetfKeystoreSupport {
    /**
     * {@return the AsymmetricKeyParser}
     */
    AsymmetricKeyParser asymmetric();

    /**
     * {@return an instance supporting only inline definitions}
     */
    static IetfKeystoreSupport inlineOnly() {
        return InlineAsymmetricKeyParser.SUPPORT;
    }

    /**
     * {@return an instance supporting inline definitions and central keystore references}
     *
     * @param central {@link KeystoreAccess} to use for resolution of central keystore references
     */
    static IetfKeystoreSupport inlineAndCentral(final KeystoreAccess central) {
        return new DefaultIetfKeystoreSupport(new KeystoreAsymmetricKeyParser(central));
    }
}
