/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;

public class WebSocketSessionHandlerTest {

    private static final class WebSocketTestSessionState {
        private final BaseListenerInterface listener;
        private final ScheduledExecutorService executorService;
        private final WebSocketSessionHandler webSocketSessionHandler;
        private final int heartbeatInterval;
        private final int maxFragmentSize;
        private final ScheduledFuture pingFuture;

        private WebSocketTestSessionState(final int maxFragmentSize, final int heartbeatInterval) {
            listener = Mockito.mock(BaseListenerInterface.class);
            executorService = Mockito.mock(ScheduledExecutorService.class);
            this.heartbeatInterval = heartbeatInterval;
            this.maxFragmentSize = maxFragmentSize;
            webSocketSessionHandler = new WebSocketSessionHandler(executorService, listener, maxFragmentSize,
                    heartbeatInterval);
            pingFuture = Mockito.mock(ScheduledFuture.class);
            Mockito.when(executorService.scheduleWithFixedDelay(Mockito.any(Runnable.class),
                    Mockito.eq((long) heartbeatInterval), Mockito.eq((long) heartbeatInterval),
                    Mockito.eq(TimeUnit.MILLISECONDS))).thenReturn(pingFuture);
        }
    }

    @Test
    public void onWebSocketConnectedWithEnabledPing() {
        final int heartbeatInterval = 1000;
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(
                1000, heartbeatInterval);
        final Session session = Mockito.mock(Session.class);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.verify(webSocketTestSessionState.listener).addSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        Mockito.verify(webSocketTestSessionState.executorService).scheduleWithFixedDelay(Mockito.any(Runnable.class),
                Mockito.eq((long) webSocketTestSessionState.heartbeatInterval),
                Mockito.eq((long) webSocketTestSessionState.heartbeatInterval), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void onWebSocketConnectedWithDisabledPing() {
        final int heartbeatInterval = 0;
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(
                1000, heartbeatInterval);
        final Session session = Mockito.mock(Session.class);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.verify(webSocketTestSessionState.listener).addSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        Mockito.verifyZeroInteractions(webSocketTestSessionState.executorService);
    }

    @Test
    public void onWebSocketConnectedWithAlreadyOpenSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.isOpen()).thenReturn(true);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.verify(webSocketTestSessionState.listener, Mockito.times(1)).addSubscriber(Mockito.any());
    }

    @Test
    public void onWebSocketClosedWithOpenSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(200, 10000);
        final Session session = Mockito.mock(Session.class);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.verify(webSocketTestSessionState.listener).addSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(200, "Simulated close");
        Mockito.verify(webSocketTestSessionState.listener).removeSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
    }

    @Test
    public void onWebSocketClosedWithNotInitialisedSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(300, 12000);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketClosed(500, "Simulated close");
        Mockito.verifyZeroInteractions(webSocketTestSessionState.listener);
    }

    @Test
    public void onWebSocketErrorWithEnabledPingAndLivingSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        final Throwable sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.when(webSocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        Mockito.when(webSocketTestSessionState.pingFuture.isDone()).thenReturn(false);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        Mockito.verify(webSocketTestSessionState.listener).removeSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        Mockito.verify(session).close();
        Mockito.verify(webSocketTestSessionState.pingFuture).cancel(Mockito.anyBoolean());
    }

    @Test
    public void onWebSocketErrorWithEnabledPingAndDeadSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.isOpen()).thenReturn(false);
        final Throwable sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        Mockito.verify(webSocketTestSessionState.listener).removeSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        Mockito.verify(session, Mockito.never()).close();
        Mockito.verify(webSocketTestSessionState.pingFuture).cancel(Mockito.anyBoolean());
    }

    @Test
    public void onWebSocketErrorWithDisabledPingAndDeadSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(150, 8000);
        final Session session = Mockito.mock(Session.class);
        Mockito.when(session.isOpen()).thenReturn(false);
        final Throwable sampleError = new IllegalStateException("Simulated error");
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);
        Mockito.when(webSocketTestSessionState.pingFuture.isCancelled()).thenReturn(false);
        Mockito.when(webSocketTestSessionState.pingFuture.isDone()).thenReturn(true);

        webSocketTestSessionState.webSocketSessionHandler.onWebSocketError(sampleError);
        Mockito.verify(webSocketTestSessionState.listener).removeSubscriber(
                webSocketTestSessionState.webSocketSessionHandler);
        Mockito.verify(session, Mockito.never()).close();
        Mockito.verify(webSocketTestSessionState.pingFuture, Mockito.never()).cancel(Mockito.anyBoolean());
    }

    @Test
    public void sendDataMessageWithDisabledFragmentation() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(100);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        Mockito.verify(remoteEndpoint).sendString(testMessage);
    }

    @Test
    public void sendDataMessageWithDisabledFragAndDeadSession() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(0, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(false);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        final String testMessage = generateRandomStringOfLength(11);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        Mockito.verifyZeroInteractions(remoteEndpoint);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndSmallMessage() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // in both cases, fragmentation should not be applied
        final String testMessage1 = generateRandomStringOfLength(100);
        final String testMessage2 = generateRandomStringOfLength(50);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage1);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage2);
        Mockito.verify(remoteEndpoint).sendString(testMessage1);
        Mockito.verify(remoteEndpoint).sendString(testMessage2);
        Mockito.verify(remoteEndpoint, Mockito.never()).sendPartialString(Mockito.anyString(), Mockito.anyBoolean());
    }

    @Test
    public void sendDataMessageWithZeroLength() {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage("");
        Mockito.verifyZeroInteractions(remoteEndpoint);
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage1() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments of length 100 characters
        final String testMessage = generateRandomStringOfLength(1000);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(remoteEndpoint, Mockito.times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final List<String> allMessages = messageCaptor.getAllValues();
        final List<Boolean> isLastFlags = isLastCaptor.getAllValues();
        Assert.assertTrue(allMessages.stream().allMatch(s -> s.length() == webSocketTestSessionState.maxFragmentSize));
        Assert.assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        Assert.assertTrue(isLastFlags.get(9));
    }

    @Test
    public void sendDataMessageWithEnabledFragAndLargeMessage2() throws IOException {
        final WebSocketTestSessionState webSocketTestSessionState = new WebSocketTestSessionState(100, 0);
        final Session session = Mockito.mock(Session.class);
        final RemoteEndpoint remoteEndpoint = Mockito.mock(RemoteEndpoint.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        Mockito.when(session.getRemote()).thenReturn(remoteEndpoint);
        webSocketTestSessionState.webSocketSessionHandler.onWebSocketConnected(session);

        // there should be 10 fragments, the last fragment should be the shortest one
        final String testMessage = generateRandomStringOfLength(950);
        webSocketTestSessionState.webSocketSessionHandler.sendDataMessage(testMessage);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(remoteEndpoint, Mockito.times(10)).sendPartialString(
                messageCaptor.capture(), isLastCaptor.capture());

        final List<String> allMessages = messageCaptor.getAllValues();
        final List<Boolean> isLastFlags = isLastCaptor.getAllValues();
        Assert.assertTrue(allMessages.subList(0, 9).stream().allMatch(s ->
                s.length() == webSocketTestSessionState.maxFragmentSize));
        Assert.assertEquals(50, allMessages.get(9).length());
        Assert.assertTrue(isLastFlags.subList(0, 9).stream().noneMatch(isLast -> isLast));
        Assert.assertTrue(isLastFlags.get(9));
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