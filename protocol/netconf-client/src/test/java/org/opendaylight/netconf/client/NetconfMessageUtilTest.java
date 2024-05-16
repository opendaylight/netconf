/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.test.util.XmlFileLoader;

class NetconfMessageUtilTest {
    @Test
    void testNetconfMessageUtil() throws Exception {
        final NetconfMessage okMessage = new NetconfMessage(XmlFileLoader.xmlFileToDocument(
            "netconfMessages/rpc-reply_ok.xml"));
        assertTrue(NetconfMessageUtil.isOKMessage(okMessage));
        assertFalse(NetconfMessageUtil.isErrorMessage(okMessage));

        final NetconfMessage errorMessage = new NetconfMessage(XmlFileLoader.xmlFileToDocument(
            "netconfMessages/communicationError/testClientSendsRpcReply_expectedResponse.xml"));
        assertTrue(NetconfMessageUtil.isErrorMessage(errorMessage));
        assertFalse(NetconfMessageUtil.isOKMessage(errorMessage));

        final Collection<String> caps = NetconfMessageUtil.extractCapabilitiesFromHello(
            XmlFileLoader.xmlFileToDocument("netconfMessages/client_hello.xml"));
        assertTrue(caps.contains("urn:ietf:params:netconf:base:1.0"));
        assertTrue(caps.contains("urn:ietf:params:netconf:base:1.1"));
    }
}
