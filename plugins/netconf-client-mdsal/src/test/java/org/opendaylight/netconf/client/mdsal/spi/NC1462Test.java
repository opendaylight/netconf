/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NC1462Test {
    private static final int RPC_MESSAGE_LIMIT = 1;
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);
    private static final NetconfMessage MESSAGE = createNetconfMessage();

    @Mock
    private RemoteDevice<NetconfDeviceCommunicator> mockDevice;
    @Mock
    private NetconfClientSession mockNetconfClientSession;

    private NetconfDeviceCommunicator communicator;

    @BeforeEach
    void beforeEach() {
        // Prepare NetconfDeviceCommunicator.
        communicator = new NetconfDeviceCommunicator(new RemoteDeviceId("test-device",
            InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, RPC_MESSAGE_LIMIT);

        doReturn(Collections.emptyList()).when(mockNetconfClientSession).getServerCapabilities();
        doReturn(SESSION_ID).when(mockNetconfClientSession).sessionId();
        doReturn(mock(ChannelFuture.class)).when(mockNetconfClientSession).sendMessage(eq(MESSAGE));

        doNothing().when(mockDevice).onRemoteSessionUp(any(NetconfSessionPreferences.class),
            any(NetconfDeviceCommunicator.class));
        doNothing().when(mockDevice).onRemoteSessionDown();

        communicator.onSessionUp(mockNetconfClientSession);
    }

    @AfterEach
    void afterEach() {
        communicator.close();
    }

    @Test
    void testSemaphoreDecrease() {
        // Call onSessionDown to remove session from NetconfDeviceCommunicator.
        communicator.onSessionDown(mockNetconfClientSession, new RuntimeException("Test exception"));
        // Call sendRequest and verify that sendMessage was not invoked.
        communicator.sendRequest(MESSAGE);
        verify(mockNetconfClientSession, times(0)).sendMessage(MESSAGE);
        // Call session up to assign the session again to NetconfDeviceCommunicator.
        communicator.onSessionUp(mockNetconfClientSession);

        // Send a request and verify that the semaphore was not decreased by previous sendRequest call by verifying that
        // sendMessage was executed on session.
        communicator.sendRequest(MESSAGE);
        verify(mockNetconfClientSession, times(1)).sendMessage(MESSAGE);
    }

    // Create NetconfMessage for testing purpose.
    private static NetconfMessage createNetconfMessage() {
        final var doc = UntrustedXML.newDocumentBuilder().newDocument();
        final var element = doc.createElement("request");
        element.setAttribute("message-id", "test-messageID");
        doc.appendChild(element);
        return new NetconfMessage(doc);
    }
}
