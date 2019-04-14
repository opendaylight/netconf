/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-socket session handler that is responsible for handling of incoming web-socket session events, frames and
 * messages, and for periodical sending of ping frames to the remote endpoint of the web-socket session.
 *
 * @see WebSocket more information about Jetty's web-socket implementation
 */
@WebSocket
class WebSocketPingSessionHandler extends WebSocketSessionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPingSessionHandler.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final String pingMessage;
    private final int pingInterval;

    private ScheduledFuture<?> pingProcess;

    /**
     * Creation of the web-socket session handler using ping settings.
     *
     * @param scheduledExecutorService Ping service executor.
     * @param pingMessage              Text of the ping message.
     * @param pingInterval             Interval ath which the ping messages should be sent to remote server.
     */
    WebSocketPingSessionHandler(final ScheduledExecutorService scheduledExecutorService, final String pingMessage,
            final int pingInterval) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.pingMessage = pingMessage;
        this.pingInterval = pingInterval;
    }

    /**
     * Handling of the initialized web-socket session. Created web-socket session is saved and the periodical ping
     * process is executed.
     *
     * @param session Just created web-socket session.
     * @see OnWebSocketConnect more information about this event
     */
    @Override
    public synchronized void onWebSocketConnected(final Session session) {
        super.onWebSocketConnected(session);
        if (session != null) {
            if (pingInterval != 0) {
                startPingProcess();
            }
        }
    }

    /**
     * Handling of the closed web-socket session. Related log messages are generated, the session latch is unreleased,
     * and the ping process is stopped.
     *
     * @param statusCode Status code of the closed session.
     * @param reason     Reason why the web-socket session has been closed.
     * @see OnWebSocketClose more information about this event
     */
    @Override
    public synchronized void onWebSocketClosed(final int statusCode, final String reason) {
        super.onWebSocketClosed(statusCode, reason);
        if (pingInterval != 0) {
            stopPingProcess();
        }
    }

    private void startPingProcess() {
        if (pingProcess == null || pingProcess.isDone() || pingProcess.isCancelled()) {
            pingProcess = Objects.requireNonNull(scheduledExecutorService).scheduleWithFixedDelay(
                () -> sendPingMessage(pingMessage), pingInterval, pingInterval, TimeUnit.MILLISECONDS);
            LOG.info("{}: PING process has been started with setup delay {}.", getUri(), pingInterval);
        } else {
            LOG.warn("{}: PING process cannot be started because the previous process hasn't been stopped yet.",
                    getUri());
        }
    }

    private void stopPingProcess() {
        if (pingProcess != null && !pingProcess.isCancelled() && !pingProcess.isDone()) {
            pingProcess.cancel(true);
            if (webSocketSession != null) {
                LOG.info("{}: PING process has been cancelled.", getUri());
            } else {
                LOG.warn("PING process of non-initialized session has been cancelled.");
            }
        } else {
            if (webSocketSession != null) {
                LOG.warn("{}: PING process han't been started - it doesn't have to cancelled.", getUri());
            } else {
                LOG.warn("PING process of non-initialized session han't been started - it doesn't have to cancelled.");
            }
        }
    }

    private synchronized void sendPingMessage(final String message) {
        if (webSocketSession != null) {
            if (webSocketSession.isOpen()) {
                try {
                    webSocketSession.getRemote().sendPing(ByteBuffer.wrap(message.getBytes(Charset.defaultCharset())));
                    LOG.info("{}: Sent PING message to remote endpoint with body:\n{}", getUri(), message);
                } catch (final IOException e) {
                    LOG.error("{}: Cannot send PING frame with message {} to remote endpoint.", getUri(), message, e);
                }
            } else {
                LOG.warn("{}: PING frame with message {} cannot be sent to remote endpoint - "
                        + "web-socket session is closed.", getUri(), message);
            }
        } else {
            LOG.warn("PING frame with message {} cannot be sent to remote endpoint - "
                    + "web-socket session hasn't been initialised yet", message);
        }
    }
}