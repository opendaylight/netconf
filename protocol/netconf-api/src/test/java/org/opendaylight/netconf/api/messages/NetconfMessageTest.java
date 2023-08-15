/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NetconfMessageTest {

    private static final String MESSAGE_ID = "1014";

    @Test
    void ofDocumentTest() {
        final var helloMessage = HelloMessage.createClientHello(List.of(), Optional.empty());
        final var notificationMessage = NotificationMessage.wrapDocumentAsNotification(getTestElement());
        final var rpcMessage = RpcMessage.wrapDocumentAsRpc(getTestElement(), MESSAGE_ID);
        final var rpcReplyMessage = RpcReplyMessage.wrapDocumentAsRpcReply(getTestElement(), MESSAGE_ID);

        final var shouldBeHello = NetconfMessage.of(helloMessage.getDocument());
        assertInstanceOf(HelloMessage.class, shouldBeHello);

        final var shouldBeNotification = NetconfMessage.of(notificationMessage.getDocument());
        assertInstanceOf(NotificationMessage.class, shouldBeNotification);

        final var shouldBeRpc = NetconfMessage.of(rpcMessage.getDocument());
        assertInstanceOf(RpcMessage.class, shouldBeRpc);

        final var shouldBeRpcReply = NetconfMessage.of(rpcReplyMessage.getDocument());
        assertInstanceOf(RpcReplyMessage.class, shouldBeRpcReply);
    }

    private static Document getTestElement() {
        final Document document = UntrustedXML.newDocumentBuilder().newDocument();
        final Element rootElement = document.createElement("test-root");
        document.appendChild(rootElement);
        return document;
    }
}
