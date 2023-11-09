/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.EncodingName;
import org.opendaylight.yangtools.concepts.Registration;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionHandlerTest {
    private final class WebSocketTestSessionState {
        private final WebSocketSessionHandler webSocketSessionHandler;
        private final int heartbeatInterval;
        private final int maxFragmentSize;

        WebSocketTestSessionState(final int maxFragmentSize, final int heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            this.maxFragmentSize = maxFragmentSize;
            webSocketSessionHandler = new WebSocketSessionHandler(executorService, stream,
                ENCODING, null, maxFragmentSize, heartbeatInterval);

            if (heartbeatInterval != 0) {
                doReturn(pingFuture).when(executorService).scheduleWithFixedDelay(any(Runnable.class),
                    eq((long) heartbeatInterval), eq((long) heartbeatInterval), eq(TimeUnit.MILLISECONDS));
            }
        }
    }

    static final EncodingName ENCODING = new EncodingName("encoding");

    @Mock
    private RestconfStream<?> stream;
    @Mock
    private ScheduledExecutorService executorService;
    @Mock
    private ScheduledFuture pingFuture;
    @Mock
    private Session session;

    @Test
    void onWebSocketConnectedWithEnabledPing() throws Exception {
        final int heartbeatInterval = 1000;
        final var webSocketTestSessionState = new WebSocketTestSessionState(1000, heartbeatInterval);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(stream).addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null);
        verify(executorService).scheduleWithFixedDelay(any(Runnable.class),
                eq((long) webSocketTestSessionState.heartbeatInterval),
                eq((long) webSocketTestSessionState.heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void onWebSocketConnectedWithDisabledPing() throws Exception {
        final int heartbeatInterval = 0;
        final var webSocketTestSessionState = new WebSocketTestSessionState(1000, heartbeatInterval);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(stream).addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null);
        verifyNoMoreInteractions(executorService);
    }

    @Test
    void onWebSocketConnectedWithAlreadyOpenSession() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        when(session.isOpen()).thenReturn(true);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(stream).addSubscriber(any(), any(), any());
    }

    @Test
    void onWebSocketClosedWithOpenSession() throws Exception  {
        final var webSocketTestSessionState = new WebSocketTestSessionState(200, 10000);
        final var reg = mock(Registration.class);

        doReturn(reg).when(stream).addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(200, "Simulated close");
        verify(reg).close();
    }

    @Test
    void onWebSocketClosedWithNotInitialisedSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(500, "Simulated close");
        verifyNoMoreInteractions(stream);
    }

    @Test
    void onWebSocketErrorWithEnabledPingAndLivingSession() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(true);
        when(stream.addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        when(pingFuture.isCancelled()).thenReturn(false);
        when(pingFuture.isDone()).thenReturn(false);

        final var sampleError = new IllegalStateException("Simulated error");
        doNothing().when(reg).close();
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session).close();
        verify(pingFuture).cancel(anyBoolean());
    }

    @Test
    void onWebSocketErrorWithEnabledPingAndDeadSession() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(false);
        when(stream.addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final var sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session, never()).close();
        verify(pingFuture).cancel(anyBoolean());
    }

    @Test
    void onWebSocketErrorWithDisabledPingAndDeadSession() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(false);
        when(stream.addSubscriber(webSocketTestSessionState.webSocketSessionHandler, ENCODING, null))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        when(pingFuture.isDone()).thenReturn(true);

        final var sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session, never()).close();
        verify(pingFuture, never()).cancel(anyBoolean());
    }

    @Test
    void sendDataMessageWithDisabledFragmentation() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(100);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        verify(remoteEndpoint).sendString(testMessage);
    }

    @Test
    void sendDataMessageWithDisabledFragAndDeadSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(false);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(11);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        verifyNoMoreInteractions(remoteEndpoint);
    }

    @Test
    void sendDataMessageWithEnabledFragAndSmallMessage() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // in both cases, fragmentation should not be applied
        final String testMessage1 = generateRandomStringOfLength(100);
        final String testMessage2 = generateRandomStringOfLength(50);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage1);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage2);
        verify(remoteEndpoint).sendString(testMessage1);
        verify(remoteEndpoint).sendString(testMessage2);
        verify(remoteEndpoint, never()).sendPartialString(anyString(), anyBoolean());
    }

    @Test
    void sendDataMessageWithZeroLength() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage("");
        verifyNoMoreInteractions(remoteEndpoint);
    }

    @Test
    void sendDataMessageWithEnabledFragAndLargeMessage1() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final var messageCaptor = ArgumentCaptor.forClass(String.class);
        final var isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(remoteEndpoint, times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final var allMessages = messageCaptor.getAllValues();
        final var isLastFlags = isLastCaptor.getAllValues();
        assertTrue(allMessages.stream().allMatch(s -> s.length() == webSocketTestSessionState.maxFragmentSize));
        assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        assertTrue(isLastFlags.get(9));
    }

    @Test
    void sendDataMessageWithEnabledFragAndLargeMessage2() throws Exception {
        final var webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments, the last fragment should be the shortest one
        final String testMessage = generateRandomStringOfLength(950);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final var messageCaptor = ArgumentCaptor.forClass(String.class);
        final var isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(remoteEndpoint, times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final var allMessages = messageCaptor.getAllValues();
        final var isLastFlags = isLastCaptor.getAllValues();
        assertTrue(allMessages.subList(0, 9).stream().allMatch(s ->
                s.length() == webSocketTestSessionState.maxFragmentSize));
        assertEquals(50, allMessages.get(9).length());
        assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        assertTrue(isLastFlags.get(9));
    }

    private static String generateRandomStringOfLength(final int length) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
        final var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int) (alphabet.length() * Math.random());
            sb.append(alphabet.charAt(index));
        }
        return sb.toString();
    }
}