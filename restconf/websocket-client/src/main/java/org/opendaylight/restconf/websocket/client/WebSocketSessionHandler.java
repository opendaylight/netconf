/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import java.util.concurrent.CountDownLatch;
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

/**
 * Web-socket session handler that is responsible for handling of incoming web-socket session events, frames
 * and messages.
 *
 * @see WebSocket more information about Jetty's web-socket implementation
 */
@WebSocket
public class WebSocketSessionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSessionHandler.class);

    private final CountDownLatch sessionCloseLatch = new CountDownLatch(1);
    Session webSocketSession = null;

    /**
     * Handling of the initialized web-socket session. Created web-socket session is saved.
     *
     * @param session Just created web-socket session.
     * @see OnWebSocketConnect more information about this event
     */
    @OnWebSocketConnect
    public synchronized void onWebSocketConnected(final Session session) {
        if (session != null) {
            webSocketSession = session;
            LOG.info("Web-socket session has been initialized: {}", session);
        } else {
            LOG.warn("Created web-socket session is null.");
        }
    }

    /**
     * Handling of the closed web-socket session. Related log messages are generated and the session latch
     * is unreleased.
     *
     * @param statusCode Status code of the closed session.
     * @param reason     Reason why the web-socket session has been closed.
     * @see OnWebSocketClose more information about this event
     */
    @OnWebSocketClose
    public synchronized void onWebSocketClosed(final int statusCode, final String reason) {
        if (webSocketSession != null) {
            LOG.info("{}: Web-socket session has been closed with status code {} and reason: {}.", getUri(),
                    statusCode, reason);
            sessionCloseLatch.countDown();
        } else {
            LOG.warn("Trying to close web-socket session which initialization phase hasn't been registered yet "
                    + "with status code {} and reason: {}.", statusCode, reason);
        }
    }

    /**
     * Handling of the error that occurred on the web-socket. Error is logged but the web-socket is not explicitly
     * closed on error because of the testing environment in which this tool should be used.
     *
     * @param error Error details.
     * @see OnWebSocketError more information about this event
     */
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
        sessionCloseLatch.countDown();
    }

    /**
     * Handling of incoming web-socket text message. If message is not null or empty the contents of the web-socket
     * message is logged.
     *
     * @param message Web-socket text message.
     * @see OnWebSocketMessage more information about this event
     */
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

    /**
     * Handling of incoming web-socket control frame. Only web-socket PING and PONG frames are processed and their
     * content is logged. Web-socket PONG frames are automatically generated as the response to received PING frame
     * by JETTY framework.
     *
     * @param frame Web-socket control frame.
     * @see OnWebSocketFrame more information about this event
     */
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

    String getUri() {
        return webSocketSession.getUpgradeRequest().getRequestURI().toString();
    }

    /**
     * Blocking of the current thread until the web-socket session is closed.
     */
    void awaitClose() {
        try {
            sessionCloseLatch.await();
        } catch (final InterruptedException e) {
            LOG.error("Web-socket session was closed by external interruption.", e);
        }
    }
}