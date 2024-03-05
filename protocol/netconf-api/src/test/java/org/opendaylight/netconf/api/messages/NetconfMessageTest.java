/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

class NetconfMessageTest {
    private static final String MESSAGE_ID = "1014";

    @Test
    void testOfHello() throws Exception {
        final var expected = HelloMessage.createClientHello(List.of(), Optional.empty());
        final var msg = assertInstanceOf(HelloMessage.class, NetconfMessage.of(expected.getDocument()));
        assertEquals("""
            <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <capabilities/>
            </hello>
            """, msg.toString());
    }

    @Test
    void testOfNotification() throws Exception {
        final var expected = NotificationMessage.ofNotificationContent(getTestElement(), Instant.ofEpochSecond(42));
        final var msg = assertInstanceOf(NotificationMessage.class, NetconfMessage.of(expected.getDocument()));
        assertEquals("""
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                <test-root/>
                <eventTime>1970-01-01T00:00:42Z</eventTime>
            </notification>
            """, msg.toString());
    }

    @Test
    void testOfNotificationNanos() throws Exception {
        final var eventTime = Instant.ofEpochSecond(42, 123456789);
        final var expected = NotificationMessage.ofNotificationContent(getTestElement(), eventTime);
        final var msg = assertInstanceOf(NotificationMessage.class, NetconfMessage.of(expected.getDocument()));
        assertEquals("""
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">
                <test-root/>
                <eventTime>1970-01-01T00:00:42.123456789Z</eventTime>
            </notification>
            """, msg.toString());
        assertEquals(eventTime, msg.getEventTime());
    }

    @Test
    void testOfRpc() throws Exception {
        final var expected = RpcMessage.ofOperation(MESSAGE_ID, getTestElement());
        final var msg = assertInstanceOf(RpcMessage.class, NetconfMessage.of(expected.getDocument()));
        assertEquals("""
            <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1014">
                <test-root/>
            </rpc>
            """, msg.toString());
    }

    @Test
    void testOfRpcRply() throws Exception {
        final var expected = RpcReplyMessage.ofOperation(MESSAGE_ID, getTestElement());
        final var msg = assertInstanceOf(RpcReplyMessage.class, NetconfMessage.of(expected.getDocument()));
        assertEquals("""
            <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="1014">
                <test-root/>
            </rpc-reply>
            """, msg.toString());
    }

    void testOfInvalid() {
        final var ex = assertThrows(DocumentedException.class, () -> NetconfMessage.of(getTestElement()));
        assertEquals("", ex.getMessage());
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, ex.getErrorTag());
        assertEquals(ImmutableMap.of("bad-element", "test-root"), ex.getErrorInfo());
    }

    private static Document getTestElement() {
        final var document = UntrustedXML.newDocumentBuilder().newDocument();
        final var rootElement = document.createElement("test-root");
        document.appendChild(rootElement);
        return document;
    }
}
