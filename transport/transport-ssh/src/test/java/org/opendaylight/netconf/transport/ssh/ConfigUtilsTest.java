/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildAsymmetricKeyCentral;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;
import static org.opendaylight.netconf.transport.ssh.TestUtils.toKeyPair;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.keystore.api.KeystoreAccess;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystoreBuilder;

@ExtendWith(MockitoExtension.class)
class ConfigUtilsTest {
    private static final String KEY_NAME = "central-key";

    @Mock
    private KeystoreAccess keystoreAccess;

    @Test
    void centralKeystoreReferenceIsResolved() throws Exception {
        final var keyPair = toKeyPair(generateKeyPairWithCertificate("RSA"));
        when(keystoreAccess.lookupAsymmetric(KEY_NAME)).thenReturn(keyPair);

        assertSame(keyPair, ConfigUtils.extractKeyPair(keystoreAccess, buildAsymmetricKeyCentral(KEY_NAME)));
    }

    @Test
    void missingCentralKeystoreKeyIsRejected() {
        final var ex = assertThrows(UnsupportedConfigurationException.class,
            () -> ConfigUtils.extractKeyPair(KeystoreAccess.empty(), buildAsymmetricKeyCentral(KEY_NAME)));
        assertEquals("Cannot resolve key " + KEY_NAME, ex.getMessage());
    }

    @Test
    void missingCentralKeystoreReferenceIsRejected() {
        final var input = new CentralKeystoreBuilder().build();
        final var ex = assertThrows(UnsupportedConfigurationException.class,
            () -> ConfigUtils.extractKeyPair(keystoreAccess, input));
        assertEquals("Missing central keystore reference in " + input, ex.getMessage());
    }
}
