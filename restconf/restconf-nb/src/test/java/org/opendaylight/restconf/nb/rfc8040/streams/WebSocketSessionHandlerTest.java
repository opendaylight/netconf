/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.EncodingName;
import org.opendaylight.yangtools.concepts.Registration;

public class WebSocketSessionHandlerTest {
    private static final class WebSocketTestSessionState {
        private final RestconfStream<?> listener;
        private final ScheduledExecutorService executorService;
        private final WebSocketSessionHandler webSocketSessionHandler;
        private final int heartbeatInterval;
        private final int maxFragmentSize;
        private final ScheduledFuture pingFuture;

        WebSocketTestSessionState(final int maxFragmentSize, final int heartbeatInterval) {
            listener = mock(RestconfStream.class);
            executorService = mock(ScheduledExecutorService.class);
            this.heartbeatInterval = heartbeatInterval;
            this.maxFragmentSize = maxFragmentSize;
            webSocketSessionHandler = new WebSocketSessionHandler(executorService, listener,
                new EncodingName("encoding"), null, maxFragmentSize, heartbeatInterval);
            pingFuture = mock(ScheduledFuture.class);
            when(executorService.scheduleWithFixedDelay(any(Runnable.class), eq((long) heartbeatInterval),
                eq((long) heartbeatInterval), eq(TimeUnit.MILLISECONDS))).thenReturn(pingFuture);
        }
    }

    @Test
    public void onWebSocketConnectedWithEnabledPing() {
        final int heartbeatInterval = 1000;
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(
                1000, heartbeatInterval);
        final Session session = mock(Session.class);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(webSocketTestSessionState.listener).addSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        verify(webSocketTestSessionState.executorService).scheduleWithFixedDelay(any(Runnable.class),
                eq((long) webSocketTestSessionState.heartbeatInterval),
                eq((long) webSocketTestSessionState.heartbeatInterval), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void onWebSocketConnectedWithDisabledPing() {
        final int heartbeatInterval = 0;
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(
                1000, heartbeatInterval);
        final Session session = mock(Session.class);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(webSocketTestSessionState.listener).addSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        verifyNoMoreInteractions(webSocketTestSessionState.executorService);
    }

    @Test
    public void onWebSocketConnectedWithAlreadyOpenSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(webSocketTestSessionState.listener).addSubscriber(any());
    }

    @Test
    public void onWebSocketClosedWithOpenSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(200, 10000);
        final var session = mock(Session.class);
        final var reg = mock(Registration.class);

        doReturn(reg).when(webSocketTestSessionState.listener)
            .addSubscriber(webSocketTestSessionState.webSocketSessionHandler);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        verify(webSocketTestSessionState.listener).addSubscriber(webSocketTestSessionState.webSocketSessionHandler);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(200, "Simulated close");
        verify(reg).close();
    }

    @Test
    public void onWebSocketClosedWithNotInitialisedSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(300, 12000);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(500, "Simulated close");
        verifyNoMoreInteractions(webSocketTestSessionState.listener);
    }

    @Test
    public void onWebSocketErrorWithEnabledPingAndLivingSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var session = mock(Session.class);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(true);
        when(webSocketTestSessionState.listener.addSubscriber(webSocketTestSessionState.webSocketSessionHandler))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        when(webSocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        when(webSocketTestSessionState.pingFuture.isDone()).thenReturn(false);

        final var sampleError = new IllegalStateException("Simulated error");
        doNothing().when(reg).close();
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session).close();
        verify(webSocketTestSessionState.pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onWebSocketErrorWithEnabledPingAndDeadSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var session = mock(Session.class);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(false);
        when(webSocketTestSessionState.listener.addSubscriber(webSocketTestSessionState.webSocketSessionHandler))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final var sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session, never()).close();
        verify(webSocketTestSessionState.pingFuture).cancel(anyBoolean());
    }

    @Test
    public void onWebSocketErrorWithDisabledPingAndDeadSession() {
        final var webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final var session = mock(Session.class);
        final var reg = mock(Registration.class);

        when(session.isOpen()).thenReturn(false);
        when(webSocketTestSessionState.listener.addSubscriber(webSocketTestSessionState.webSocketSessionHandler))
            .thenReturn(reg);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        when(webSocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        when(webSocketTestSessionState.pingFuture.isDone()).thenReturn(true);

        final var sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        verify(reg).close();
        verify(session, never()).close();
        verify(webSocketTestSessionState.pingFuture, never()).cancel(anyBoolean());
    }

    @Test
    public void sendDataMessageWithDisabledFragmentation() throws IOException {
        final var webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final var session = mock(Session.class);
        final var remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(100);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        verify(remoteEndpoint).sendString(testMessage);
    }

    @Test
    public void sendDataMessageWithDisabledFragAndDeadSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final Session session = mock(Session.class);
        final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(false);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(11);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        verifyNoMoreInteractions(remoteEndpoint);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndSmallMessage() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = mock(Session.class);
        final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
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
    public void sendDataMessageWithZeroLength() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = mock(Session.class);
        final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage("");
        verifyNoMoreInteractions(remoteEndpoint);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage1() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = mock(Session.class);
        final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(remoteEndpoint, times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final List<String> allMessages = messageCaptor.getAllValues();
        final List<Boolean> isLastFlags = isLastCaptor.getAllValues();
        assertTrue(allMessages.stream().allMatch(s -> s.length() == webSocketTestSessionState.maxFragmentSize));
        assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        assertTrue(isLastFlags.get(9));
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage2() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = mock(Session.class);
        final RemoteEndpoint remoteEndpoint = mock(RemoteEndpoint.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments, the last fragment should be the shortest one
        final String testMessage = generateRandomStringOfLength(950);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(remoteEndpoint, times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final List<String> allMessages = messageCaptor.getAllValues();
        final List<Boolean> isLastFlags = isLastCaptor.getAllValues();
        assertTrue(allMessages.subList(0, 9).stream().allMatch(s ->
                s.length() == webSocketTestSessionState.maxFragmentSize));
        assertEquals(50, allMessages.get(9).length());
        assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        assertTrue(isLastFlags.get(9));
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