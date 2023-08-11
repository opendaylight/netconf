/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

class RpcReplyMessageTest {
    private final Document document = UntrustedXML.newDocumentBuilder().newDocument();

    @Test
    void testOfDocument() throws Exception {
        final var rootElement = document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc-reply");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var msg = RpcReplyMessage.of(document);
        assertEquals("foo", msg.messageId());
        assertEquals("""
            <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="foo"/>
            """, msg.toString());
    }

    @Test
    void testOfException() throws Exception {
        final var msg = RpcReplyMessage.of(new DocumentedException("Missing message-id attribute",
            ErrorType.RPC, ErrorTag.MISSING_ATTRIBUTE, ErrorSeverity.ERROR, ImmutableMap.of(
                "bad-attribute", XmlNetconfConstants.MESSAGE_ID,
                "bad-element", XmlNetconfConstants.RPC_KEY)));
        assertEquals("""
            <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <rpc-error>
                    <error-type>rpc</error-type>
                    <error-tag>missing-attribute</error-tag>
                    <error-severity>error</error-severity>
                    <error-message>Missing message-id attribute</error-message>
                    <error-info>
                        <bad-attribute>message-id</bad-attribute>
                        <bad-element>rpc</bad-element>
                    </error-info>
                </rpc-error>
            </rpc-reply>
            """, msg.toString());
    }

    @Test
    void testOfBadElementName() {
        final var rootElement = document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "bad");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var ex = assertThrows(DocumentedException.class, () -> RpcReplyMessage.of(document));
        assertEquals("Unexpected element name bad", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "bad"), ex.getErrorInfo());
    }

    @Test
    void testOfBadElementNs() {
        final var rootElement = document.createElementNS("bad", "rpc-reply");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var ex = assertThrows(DocumentedException.class, () -> RpcReplyMessage.of(document));
        assertEquals("Unexpected element namespace bad", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_NAMESPACE, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "rpc-reply", "bad-namespace", "bad"), ex.getErrorInfo());
    }

    @Test
    void testIsRpcReplyMessage() {
        document.appendChild(document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc-reply"));
        assertTrue(RpcReplyMessage.isRpcReplyMessage(document));
    }

    @Test
    void testIsRpcReplyMessageNegative() {
        document.appendChild(document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "other"));
        assertFalse(RpcReplyMessage.isRpcReplyMessage(document));
    }
}
