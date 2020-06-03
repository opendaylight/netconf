/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.sse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;

public class SSESessionHandlerTest {

    private static final class SSETestSessionState {
        private final BaseListenerInterface listener;
        private final ScheduledExecutorService executorService;
        private final SSESessionHandler sseSessionHandler;
        private final int heartbeatInterval;
        private final ScheduledFuture pingFuture;
        private final EventOutput eventOutput;

        private SSETestSessionState(final int maxFragmentSize, final int heartbeatInterval) {
            listener = mock(BaseListenerInterface.class);
            executorService = mock(ScheduledExecutorService.class);
            eventOutput = mock(EventOutput.class);
            this.heartbeatInterval = heartbeatInterval;
            sseSessionHandler = new SSESessionHandler(executorService, eventOutput, listener, maxFragmentSize,
                    heartbeatInterval);
            pingFuture = mock(ScheduledFuture.class);
            when(executorService.scheduleWithFixedDelay(any(Runnable.class), eq((long) heartbeatInterval),
                    eq((long) heartbeatInterval), eq(TimeUnit.MILLISECONDS))).thenReturn(pingFuture);
        }
    }

    @Test
    public void onSSEConnectedWithEnabledPing() {
        final int heartbeatInterval = 1000;
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(1000, heartbeatInterval);

        sSESocketTestSessionState.sseSessionHandler.init();
        verify(sSESocketTestSessionState.listener).addSubscriber(sSESocketTestSessionState.sseSessionHandler);
        verify(sSESocketTestSessionState.executorService).scheduleWithFixedDelay(any(Runnable.class),
                eq((long) sSESocketTestSessionState.heartbeatInterval),
                eq((long) sSESocketTestSessionState.heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void onSSEConnectedWithDisabledPing() {
        final int heartbeatInterval = 0;
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(1000, heartbeatInterval);

        sSESocketTestSessionState.sseSessionHandler.init();
        verify(sSESocketTestSessionState.listener).addSubscriber(sSESocketTestSessionState.sseSessionHandler);
        verifyNoMoreInteractions(sSESocketTestSessionState.executorService);
    }

    @Test
    public void onSSEClosedWithOpenSession() {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(200, 10000);

        sSESocketTestSessionState.sseSessionHandler.init();
        verify(sSESocketTestSessionState.listener).addSubscriber(sSESocketTestSessionState.sseSessionHandler);

        sSESocketTestSessionState.sseSessionHandler.close();
        verify(sSESocketTestSessionState.listener).removeSubscriber(sSESocketTestSessionState.sseSessionHandler);
    }

    @Test
    public void onSSECloseWithEnabledPingAndLivingSession() throws IOException {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(150, 8000);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(false);
        sSESocketTestSessionState.sseSessionHandler.init();
        when(sSESocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        when(sSESocketTestSessionState.pingFuture.isDone()).thenReturn(false);

        sSESocketTestSessionState.sseSessionHandler.close();
        verify(sSESocketTestSessionState.listener).removeSubscriber(sSESocketTestSessionState.sseSessionHandler);
        verify(sSESocketTestSessionState.pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onSSECloseWithEnabledPingAndDeadSession() {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(150, 8000);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(true);
        sSESocketTestSessionState.sseSessionHandler.init();

        sSESocketTestSessionState.sseSessionHandler.close();
        verify(sSESocketTestSessionState.listener).removeSubscriber(sSESocketTestSessionState.sseSessionHandler);
        verify(sSESocketTestSessionState.pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onSSECloseWithDisabledPingAndDeadSession() {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(150, 8000);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(true);
        sSESocketTestSessionState.sseSessionHandler.init();
        when(sSESocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        when(sSESocketTestSessionState.pingFuture.isDone()).thenReturn(true);

        sSESocketTestSessionState.sseSessionHandler.close();
        verify(sSESocketTestSessionState.listener).removeSubscriber(sSESocketTestSessionState.sseSessionHandler);
        verify(sSESocketTestSessionState.pingFuture, never()).cancel(anyBoolean());
    }

    @Test
    public void sendDataMessageWithDisabledFragmentation() throws IOException {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(0, 0);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(false);
        sSESocketTestSessionState.sseSessionHandler.init();
        final String testMessage = generateRandomStringOfLength(100);
        sSESocketTestSessionState.sseSessionHandler.sendDataMessage(testMessage);

        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(sSESocketTestSessionState.eventOutput, times(1)).write(cap.capture());
        OutboundEvent event = cap.getAllValues().get(0);
        assertNotNull(event);
        assertEquals(event.getData(), testMessage);

    }

    @Test
    public void sendDataMessageWithDisabledFragAndDeadSession() throws IOException {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(0, 0);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(true);
        sSESocketTestSessionState.sseSessionHandler.init();

        final String testMessage = generateRandomStringOfLength(11);
        sSESocketTestSessionState.sseSessionHandler.sendDataMessage(testMessage);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(sSESocketTestSessionState.eventOutput, times(0)).write(cap.capture());
    }

    @Test
    public void sendDataMessageWithEnabledFragAndSmallMessage() throws IOException {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(100, 0);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(false);
        sSESocketTestSessionState.sseSessionHandler.init();

        // in both cases, fragmentation should not be applied
        final String testMessage1 = generateRandomStringOfLength(100);
        final String testMessage2 = generateRandomStringOfLength(50);
        sSESocketTestSessionState.sseSessionHandler.sendDataMessage(testMessage1);
        sSESocketTestSessionState.sseSessionHandler.sendDataMessage(testMessage2);

        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        // without fragmentation there will be 2 write calls
        verify(sSESocketTestSessionState.eventOutput, times(2)).write(cap.capture());
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
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(100, 0);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(false);
        sSESocketTestSessionState.sseSessionHandler.init();

        sSESocketTestSessionState.sseSessionHandler.sendDataMessage("");
        verifyNoMoreInteractions(sSESocketTestSessionState.eventOutput);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage1() throws IOException {
        final SSETestSessionState sSESocketTestSessionState = new SSETestSessionState(100, 0);
        when(sSESocketTestSessionState.eventOutput.isClosed()).thenReturn(false);
        sSESocketTestSessionState.sseSessionHandler.init();

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        sSESocketTestSessionState.sseSessionHandler.sendDataMessage(testMessage);
        ArgumentCaptor<OutboundEvent> cap = ArgumentCaptor.forClass(OutboundEvent.class);
        // SSE automatically send fragmented packet ended with new line character due to eventOutput
        // have only 1 write call
        verify(sSESocketTestSessionState.eventOutput, times(1)).write(cap.capture());
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
