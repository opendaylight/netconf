/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

class HelloMessageTest {
    @Test
    void testConstructor() {
        var caps = Set.of("cap1");
        var additionalHeader = new NetconfHelloMessageAdditionalHeader("name", "host", "1", "transp", "id");
        var message = HelloMessage.createClientHello(caps, Optional.of(additionalHeader));
        assertTrue(HelloMessage.isHelloMessage(message));
        assertEquals(Optional.of(additionalHeader), message.getAdditionalHeader());

        var serverMessage = HelloMessage.createServerHello(caps, new SessionIdType(Uint32.valueOf(100)));
        assertTrue(HelloMessage.isHelloMessage(serverMessage));
    }
}
