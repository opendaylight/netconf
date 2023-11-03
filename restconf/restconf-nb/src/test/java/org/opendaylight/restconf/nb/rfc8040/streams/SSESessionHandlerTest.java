/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SSESessionHandlerTest {
    @Mock
    private ScheduledExecutorService executorService;
    @Mock
    private RestconfStream<?> listener;
    @Mock
    private ScheduledFuture<?> pingFuture;
    @Mock
    private SseEventSink eventSink;
    @Mock
    private Sse sse;

    private SSESessionHandler setup(final int maxFragmentSize, final int heartbeatInterval) {
        doAnswer(inv -> new OutboundEvent.Builder().data(String.class, inv.getArgument(0, String.class)).build())
            .when(sse).newEvent(any());

        final SSESessionHandler sseSessionHandler = new SSESessionHandler(executorService, eventSink, sse, listener,
            maxFragmentSize, heartbeatInterval);
        doReturn(pingFuture).when(executorService)
            .scheduleWithFixedDelay(any(Runnable.class), eq((long) heartbeatInterval), eq((long) heartbeatInterval),
                eq(TimeUnit.MILLISECONDS));
        return sseSessionHandler;
    }

    @Test
    public void onSSEConnectedWithEnabledPing() {
        final int heartbeatInterval = 1000;
        final SSESessionHandler sseSessionHandler = setup(1000, heartbeatInterval);

        sseSessionHandler.init();
        verify(listener).addSubscriber(sseSessionHandler);
        verify(executorService).scheduleWithFixedDelay(any(Runnable.class), eq((long) heartbeatInterval),
                eq((long) heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void onSSEConnectedWithDisabledPing() {
        final int heartbeatInterval = 0;
        final SSESessionHandler sseSessionHandler = setup(1000, heartbeatInterval);

        sseSessionHandler.init();
        verify(listener).addSubscriber(sseSessionHandler);
        verifyNoMoreInteractions(executorService);
    }

    @Test
    public void onSSEClosedWithOpenSession() {
        final SSESessionHandler sseSessionHandler = setup(200, 10000);

        sseSessionHandler.init();
        verify(listener).addSubscriber(sseSessionHandler);

        sseSessionHandler.close();
        verify(listener).removeSubscriber(sseSessionHandler);
    }

    @Test
    public void onSSECloseWithEnabledPingAndLivingSession() throws IOException {
        final SSESessionHandler sseSessionHandler = setup(150, 8000);
        sseSessionHandler.init();
        doReturn(false).when(pingFuture).isCancelled();
        doReturn(false).when(pingFuture).isDone();

        sseSessionHandler.close();
        verify(listener).removeSubscriber(sseSessionHandler);
        verify(pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onSSECloseWithEnabledPingAndDeadSession() {
        final SSESessionHandler sseSessionHandler = setup(150, 8000);
        sseSessionHandler.init();

        sseSessionHandler.close();
        verify(listener).removeSubscriber(sseSessionHandler);
        verify(pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onSSECloseWithDisabledPingAndDeadSession() {
        final SSESessionHandler sseSessionHandler = setup(150, 8000);
        sseSessionHandler.init();
        doReturn(true).when(pingFuture).isDone();

        sseSessionHandler.close();
        verify(listener).removeSubscriber(sseSessionHandler);
        verify(pingFuture, never()).cancel(anyBoolean());
    }

    @Test
    public void sendDataMessageWithDisabledFragmentation() throws IOException {
        final SSESessionHandler sseSessionHandler = setup(0, 0);
        doReturn(false).when(eventSink).isClosed();
        sseSessionHandler.init();
        final String testMessage = generateRandomStringOfLength(100);
        sseSessionHandler.sendDataMessage(testMessage);

        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventSink, times(1)).send(cap.capture());
        OutboundEvent event = cap.getAllValues().get(0);
        assertNotNull(event);
        assertEquals(event.getData(), testMessage);
    }

    @Test
    public void sendDataMessageWithDisabledFragAndDeadSession() throws IOException {
        final SSESessionHandler sseSessionHandler = setup(0, 0);
        doReturn(true).when(eventSink).isClosed();
        sseSessionHandler.init();

        final String testMessage = generateRandomStringOfLength(11);
        sseSessionHandler.sendDataMessage(testMessage);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventSink, times(0)).send(cap.capture());
    }

    @Test
    public void sendDataMessageWithEnabledFragAndSmallMessage() throws IOException {
        final SSESessionHandler sseSessionHandler = setup(100, 0);
        doReturn(false).when(eventSink).isClosed();
        sseSessionHandler.init();

        // in both cases, fragmentation should not be applied
        final String testMessage1 = generateRandomStringOfLength(100);
        final String testMessage2 = generateRandomStringOfLength(50);
        sseSessionHandler.sendDataMessage(testMessage1);
        sseSessionHandler.sendDataMessage(testMessage2);

        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
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
    public void sendDataMessageWithZeroLength() {
        final SSESessionHandler sseSessionHandler = setup(100, 0);
        sseSessionHandler.init();

        sseSessionHandler.sendDataMessage("");
        verifyNoMoreInteractions(eventSink);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage1() throws IOException {
        final SSESessionHandler sseSessionHandler = setup(100, 0);
        doReturn(false).when(eventSink).isClosed();
        sseSessionHandler.init();

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        sseSessionHandler.sendDataMessage(testMessage);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        // SSE automatically send fragmented packet ended with new line character due to eventOutput
        // have only 1 write call
        verify(eventSink, times(1)).send(cap.capture());
        OutboundEvent event = cap.getAllValues().get(0);
        assertNotNull(event);
        String[] lines = ((String) event.getData()).split("\r\n|\r|\n");
        assertEquals(lines.length, 10);
    }

    private static String generateRandomStringOfLength(final int length) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int) (alphabet.length() * Math.random());
            sb.append(alphabet.charAt(index));
        }
        return sb.toString();
    }
}
