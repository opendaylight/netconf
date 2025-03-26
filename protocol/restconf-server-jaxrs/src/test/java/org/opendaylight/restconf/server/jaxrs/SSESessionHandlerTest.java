/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.TimeUnit;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.LegacyRestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.yangtools.concepts.Registration;

@ExtendWith(MockitoExtension.class)
class SSESessionHandlerTest {
    @Mock
    private PingExecutor pingExecutor;
    @Mock
    private LegacyRestconfStream<?> stream;
    @Mock
    private Registration pingRegistration;
    @Mock
    private SseEventSink eventSink;
    @Mock
    private Sse sse;
    @Mock
    private Registration reg;

    private SSESender setup(final int maxFragmentSize, final long heartbeatInterval) throws Exception {
        final var sseSessionHandler = new SSESender(pingExecutor, eventSink, sse, stream,
            EncodingName.RFC8040_XML, new EventStreamGetParams(null, null, null, null, null, null, null),
            maxFragmentSize, heartbeatInterval);
        doReturn(reg).when(stream).addSubscriber(eq(sseSessionHandler), any(), any());
        return sseSessionHandler;
    }

    private void setupEvent() {
        doAnswer(inv -> new OutboundEvent.Builder().data(String.class, inv.getArgument(0, String.class)).build())
          .when(sse).newEvent(any());
    }

    private void setupPing(final long maxFragmentSize, final long heartbeatInterval) {
        doReturn(pingRegistration).when(pingExecutor)
            .startPingProcess(any(Runnable.class), eq(heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void onSSEConnectedWithEnabledPing() throws Exception {
        final var heartbeatInterval = 1000L;
        final var sseSessionHandler = setup(1000, heartbeatInterval);

        sseSessionHandler.init();
        verify(pingExecutor).startPingProcess(any(Runnable.class), eq(heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void onSSEConnectedWithDisabledPing() throws Exception {
        final int heartbeatInterval = 0;
        final var sseSessionHandler = setup(1000, heartbeatInterval);

        sseSessionHandler.init();
        verifyNoMoreInteractions(pingExecutor);
    }

    @Test
    void onSSEClosedWithOpenSession() throws Exception {
        final var sseSessionHandler = setup(200, 10000);

        sseSessionHandler.init();

        sseSessionHandler.close();
        verify(reg).close();
    }

    @Test
    void onSSECloseWithEnabledPingAndLivingSession() throws Exception {
        final var sseSessionHandler = setup(150, 8000);
        setupPing(150, 8000);
        sseSessionHandler.init();

        doNothing().when(pingRegistration).close();
        sseSessionHandler.close();
        verify(reg).close();
    }

    @Test
    void onSSECloseWithEnabledPingAndDeadSession() throws Exception {
        final var sseSessionHandler = setup(150, 8000);
        setupPing(150, 8000);
        sseSessionHandler.init();

        doNothing().when(pingRegistration).close();
        sseSessionHandler.close();
        verify(reg).close();
    }

    @Test
    void onSSECloseWithDisabledPingAndDeadSession() throws Exception {
        final var sseSessionHandler = setup(150, 8000);
        sseSessionHandler.init();

        sseSessionHandler.close();
        verify(reg).close();
        verifyNoMoreInteractions(pingRegistration);
    }

    @Test
    void sendDataMessageWithDisabledFragmentation() throws Exception {
        final var sseSessionHandler = setup(0, 0);
        doReturn(false).when(eventSink).isClosed();
        setupEvent();
        sseSessionHandler.init();
        final String testMessage = generateRandomStringOfLength(100);
        sseSessionHandler.sendDataMessage(testMessage);

        final var cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventSink, times(1)).send(cap.capture());
        final var event = cap.getAllValues().get(0);
        assertNotNull(event);
        assertEquals(event.getData(), testMessage);
    }

    @Test
    void sendDataMessageWithDisabledFragAndDeadSession() throws Exception {
        final var sseSessionHandler = setup(0, 0);
        doReturn(true).when(eventSink).isClosed();
        sseSessionHandler.init();

        final String testMessage = generateRandomStringOfLength(11);
        sseSessionHandler.sendDataMessage(testMessage);
        verify(eventSink, times(0)).send(any());
    }

    @Test
    void sendDataMessageWithEnabledFragAndSmallMessage() throws Exception {
        final var sseSessionHandler = setup(100, 0);
        doReturn(false).when(eventSink).isClosed();
        setupEvent();
        sseSessionHandler.init();

        // in both cases, fragmentation should not be applied
        final String testMessage1 = generateRandomStringOfLength(100);
        final String testMessage2 = generateRandomStringOfLength(50);
        sseSessionHandler.sendDataMessage(testMessage1);
        sseSessionHandler.sendDataMessage(testMessage2);

        final var cap = ArgumentCaptor.forClass(OutboundEvent.class);
        // without fragmentation there will be 2 write calls
        verify(eventSink, times(2)).send(cap.capture());
        OutboundEvent event1 = cap.getAllValues().get(0);
        OutboundEvent event2 = cap.getAllValues().get(1);
        assertNotNull(event1);
        assertNotNull(event2);
        assertEquals(event1.getData(), testMessage1);
        assertEquals(event2.getData(), testMessage2);
        String[] lines1 = ((String) event1.getData()).split("\r\n|\r|\n");
        assertEquals(lines1.length, 1);
        String[] lines2 = ((String) event2.getData()).split("\r\n|\r|\n");
        assertEquals(lines2.length, 1);
    }

    @Test
    void sendDataMessageWithZeroLength() throws Exception {
        final var sseSessionHandler = setup(100, 0);
        sseSessionHandler.init();

        sseSessionHandler.sendDataMessage("");
        verifyNoMoreInteractions(eventSink);
    }

    @Test
    void sendDataMessageWithEnabledFragAndLargeMessage1() throws Exception {
        final var sseSessionHandler = setup(100, 0);
        doReturn(false).when(eventSink).isClosed();
        setupEvent();
        sseSessionHandler.init();

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        sseSessionHandler.sendDataMessage(testMessage);
        final var cap = ArgumentCaptor.forClass(OutboundEvent.class);
        // SSE automatically send fragmented packet ended with new line character due to eventOutput
        // have only 1 write call
        verify(eventSink, times(1)).send(cap.capture());
        OutboundEvent event = cap.getAllValues().get(0);
        assertNotNull(event);
        String[] lines = ((String) event.getData()).split("\r\n|\r|\n");
        assertEquals(lines.length, 10);
    }

    private static String generateRandomStringOfLength(final int length) {
        final var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
        final var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int) (alphabet.length() * Math.random());
            sb.append(alphabet.charAt(index));
        }
        return sb.toString();
    }
}
