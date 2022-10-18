/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfDocumentedException;

public class NetconfHelloMessageTest {
    private final Set<String> caps = Set.of("cap1");

    @Test
    public void testConstructor() throws NetconfDocumentedException {
        var additionalHeader = new NetconfHelloMessageAdditionalHeader("name", "host", "1", "transp", "id");
        var message = NetconfHelloMessage.createClientHello(caps, Optional.of(additionalHeader));
        assertTrue(NetconfHelloMessage.isHelloMessage(message));
        assertEquals(Optional.of(additionalHeader), message.getAdditionalHeader());

        var serverMessage = NetconfHelloMessage.createServerHello(caps, 100L);
        assertTrue(NetconfHelloMessage.isHelloMessage(serverMessage));
    }
}
