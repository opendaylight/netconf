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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.common.frames.PingFrame;
import org.eclipse.jetty.websocket.common.frames.PongFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket
public class WebSocketSessionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessionHandler.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final String pingMessage;
    private final int pingInterval;

    private Session webSocketSession;
    private ScheduledFuture<?> pingProcess;

    public WebSocketSessionHandler() {
        scheduledExecutorService = null;
        pingMessage = null;
        pingInterval = 0;
    }

    public WebSocketSessionHandler(final ScheduledExecutorService scheduledExecutorService, final String pingMessage,
            final int pingInterval) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.pingMessage = pingMessage;
        this.pingInterval = pingInterval;
    }

    @OnWebSocketConnect
    public synchronized void onWebSocketConnected(final Session session) {
        if (session != null) {
            webSocketSession = session;
            if (pingInterval != 0) {
                startPingProcess();
            }
            LOG.info("Web-socket session has been initialized: {}", session);
        } else {
            LOG.warn("Created web-socket session is null.");
        }
    }

    private void startPingProcess() {
        if (pingProcess == null || pingProcess.isDone() || pingProcess.isCancelled()) {
            pingProcess = scheduledExecutorService.scheduleWithFixedDelay(() -> sendPingMessage(pingMessage),
                    pingInterval, pingInterval, TimeUnit.MILLISECONDS);
            LOG.info("{}: PING process has been started with setup delay {}.", getUri(), pingInterval);
        } else {
            LOG.warn("{}: PING process cannot be started because the previous process hasn't been stopped yet.",
                    getUri());
        }
    }

    @OnWebSocketClose
    public synchronized void onWebSocketClosed(final int statusCode, final String reason) {
        if (webSocketSession != null) {
            LOG.info("{}: Web-socket session has been closed with status code {} and reason: {}.", getUri(),
                    statusCode, reason);
        } else {
            LOG.warn("Trying to close web-socket session which initialization phase hasn't been registered yet "
                    + "with status code {} and reason: {}.", statusCode, reason);
        }
        if (pingInterval != 0) {
            stopPingProcess();
        }
    }

    @OnWebSocketError
    public synchronized void onWebSocketError(final Throwable error) {
        if (webSocketSession != null) {
            if (error != null) {
                LOG.error("{}: An error occurred on web-socket session.", getUri(), error);
            }
        } else {
            LOG.error("An error occurred on web-socket session which initialisation phase hasn't been "
                    + "registered yet.", error);
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

    @OnWebSocketMessage
    public synchronized void onWebSocketMessage(final String message) {
        if (webSocketSession != null) {
            if (webSocketSession.isOpen()) {
                if (message != null) {
                    if (!message.isEmpty()) {
                        LOG.info("{}: Received web-socket message:\n{}.", getUri(), message);
                    } else {
                        LOG.info("{}: Received empty message.", getUri());
                    }
                } else {
                    LOG.warn("{}: Received null message.", getUri());
                }
            } else {
                LOG.warn("{}: Received web-socket message on closed web-socket session:\n{}", getUri(), message);
            }
        } else {
            LOG.warn("Received web-socket message on web-socket session which initialisation phase hasn't been "
                    + "registered yet:\n{}", message);
        }
    }

    @OnWebSocketFrame
    public synchronized void onWebSocketFrame(final Frame frame) {
        if (webSocketSession != null) {
            if (webSocketSession.isOpen()) {
                if (frame != null) {
                    if (frame instanceof PingFrame) {
                        LOG.info("{}: Received PING frame with message (PONG respond is automatically generated):\n{}",
                                getUri(), ((PingFrame) frame).getPayloadAsUTF8());
                    } else if (frame instanceof PongFrame) {
                        LOG.info("{}: Received PONG frame with message:\n{}", getUri(),
                                ((PongFrame) frame).getPayloadAsUTF8());
                    }
                } else {
                    LOG.warn("{}: Received null frame.", getUri());
                }
            } else {
                LOG.warn("{}: Received web-socket frame on closed web-socket session:\n{}", getUri(), frame);
            }
        } else {
            LOG.warn("Received web-socket frame on web-socket session which initialisation phase hasn't been "
                    + "registered yet:\n{}", frame);
        }
    }

    private synchronized void sendPingMessage(final String pingMessage) {
        if (webSocketSession != null) {
            if (webSocketSession.isOpen()) {
                try {
                    webSocketSession.getRemote().sendPing(ByteBuffer.wrap(
                            pingMessage.getBytes(Charset.defaultCharset())));
                } catch (final IOException e) {
                    LOG.error("{}: Cannot send PING frame with message {} to remote endpoint.", getUri(),
                            pingMessage, e);
                }
            } else {
                LOG.warn("{}: PING frame with message {} cannot be sent to remote endpoint - "
                        + "web-socket session is closed.", getUri(), pingMessage);
            }
        } else {
            LOG.warn("PING frame with message {} cannot be sent to remote endpoint - "
                    + "web-socket session hasn't been initialised yet", pingMessage);
        }
    }

    private String getUri() {
        return webSocketSession.getUpgradeRequest().getRequestURI().toString();
    }
}