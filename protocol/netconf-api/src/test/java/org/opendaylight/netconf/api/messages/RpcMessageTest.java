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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

class RpcMessageTest {
    private final Document document = UntrustedXML.newDocumentBuilder().newDocument();

    @Test
    void testOf() throws Exception {
        final var rootElement = document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var msg = RpcMessage.of(document);
        assertEquals("foo", msg.messageId());
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="foo"/>
            """, msg.toString());
    }

    @Test
    void testOfBadElementName() {
        final var rootElement = document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "bad");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var ex = assertThrows(DocumentedException.class, () -> RpcMessage.of(document));
        assertEquals("Unexpected element name bad", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "bad"), ex.getErrorInfo());
    }

    @Test
    void testOfBadElementNs() {
        final var rootElement = document.createElementNS("bad", "rpc");
        rootElement.setAttribute("message-id", "foo");
        document.appendChild(rootElement);

        final var ex = assertThrows(DocumentedException.class, () -> RpcMessage.of(document));
        assertEquals("Unexpected element namespace bad", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_NAMESPACE, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "rpc", "bad-namespace", "bad"), ex.getErrorInfo());
    }

    @Test
    void testOfMissingMessageId() {
        document.appendChild(document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc"));

        final var ex = assertThrows(DocumentedException.class, () -> RpcMessage.of(document));
        assertEquals("Missing message-id attribute", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.RPC, ex.getErrorType());
        assertEquals(ErrorTag.MISSING_ATTRIBUTE, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "rpc", "bad-attribute", "message-id"), ex.getErrorInfo());
    }

    @Test
    void testOfOperation() {
        final var rootElement = document.createElement("test-root");
        document.appendChild(rootElement);

        final var msg = RpcMessage.ofOperation("1014", document);
        assertEquals("1014", msg.messageId());
        final var msgRoot = msg.getDocument().getDocumentElement();
        assertEquals("urn:ietf:params:xml:ns:netconf:base:1.0", msgRoot.getNamespaceURI());
        assertEquals("rpc", msgRoot.getLocalName());

        final var rootList = msgRoot.getChildNodes();
        assertEquals(1, rootList.getLength());
        assertSame(rootElement, rootList.item(0));
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1014">
                <test-root/>
            </rpc>
            """, msg.toString());
    }

    @Test
    void testIsRpcMessage() {
        document.appendChild(document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc"));
        assertTrue(RpcMessage.isRpcMessage(document));
    }

    @Test
    void testIsRpcMessageNegative() {
        document.appendChild(document.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "other"));
        assertFalse(RpcMessage.isRpcMessage(document));
    }
}
