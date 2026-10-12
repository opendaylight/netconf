/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.keystore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.security.KeyPairGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystore;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystoreBuilder;

@ExtendWith(MockitoExtension.class)
class KeystoreSupportTest {
    private static final String KEY_NAME = "central-key";
    private static final CentralKeystore CENTRAL = new CentralKeystoreBuilder()
        .setCentralKeystoreReference(KEY_NAME)
        .build();

    @Mock
    private KeystoreAccess keystoreAccess;

    @Test
    void centralKeystoreReferenceIsResolved() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        when(keystoreAccess.lookupAsymmetric(KEY_NAME)).thenReturn(keyPair);

        assertSame(keyPair, KeystoreSupport.inlineAndCentral(keystoreAccess).asymmetric().keyOf(CENTRAL));
    }

    @Test
    void missingCentralKeystoreKeyIsRejected() {
        final var parser = KeystoreSupport.inlineAndCentral(KeystoreAccess.empty()).asymmetric();
        final var ex = assertThrows(UnsupportedConfigurationException.class, () -> parser.keyOf(CENTRAL));
        assertEquals("Cannot resolve key " + KEY_NAME, ex.getMessage());
    }

    @Test
    void missingCentralKeystoreReferenceIsRejected() {
        final var input = new CentralKeystoreBuilder().build();
        final var parser = KeystoreSupport.inlineAndCentral(keystoreAccess).asymmetric();
        final var ex = assertThrows(UnsupportedConfigurationException.class, () -> parser.keyOf(input));
        assertEquals("Missing central keystore reference in " + input, ex.getMessage());
    }

    @Test
    void inlineOnlyRejectsCentralKeystoreReference() {
        final var parser = KeystoreSupport.inlineOnly().asymmetric();
        final var ex = assertThrows(UnsupportedConfigurationException.class, () -> parser.keyOf(CENTRAL));
        assertEquals("Unsupported configuration " + CentralKeystore.class.getCanonicalName(), ex.getMessage());
    }
}
