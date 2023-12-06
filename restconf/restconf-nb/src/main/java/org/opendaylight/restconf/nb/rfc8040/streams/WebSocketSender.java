/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-socket session handler that is responsible for controlling of session, managing subscription
 * to data-change-event or notification listener, and sending of data over established web-socket session.
 */
@WebSocket
@Deprecated(since = "7.0.0", forRemoval = true)
final class WebSocketSender implements Sender {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketSender.class);
    private static final byte[] PING_PAYLOAD = "ping".getBytes(Charset.defaultCharset());

    private final PingExecutor pingExecutor;
    private final RestconfStream<?> stream;
    private final EncodingName encodingName;
    private final EventStreamGetParams params;
    private final int maximumFragmentLength;
    private final long heartbeatInterval;

    private Session session;
    private Registration subscriber;
    private Registration pingProcess;

    /**
     * Creation of the new web-socket session handler.
     *
     * @param pingExecutor          Executor that is used for periodical sending of web-socket ping messages to keep
     *                              session up even if the notifications doesn't flow from server to clients or clients
     *                              don't implement ping-pong service.
     * @param stream                YANG notification or data-change event listener to which client on this web-socket
     *                              session subscribes to.
     * @param maximumFragmentLength Maximum fragment length in number of Unicode code units (characters).
     *                              If this parameter is set to 0, the maximum fragment length is disabled and messages
     *                              up to 64 KB can be sent in TCP segment (exceeded notification length ends in error).
     *                              If the parameter is set to non-zero positive value, messages longer than this
     *                              parameter are fragmented into multiple web-socket messages sent in one transaction.
     * @param heartbeatInterval     Interval in milliseconds of sending of ping control frames to remote endpoint
     *                              to keep session up. Ping control frames are disabled if this parameter is set to 0.
     */
    WebSocketSender(final PingExecutor pingExecutor, final RestconfStream<?> stream, final EncodingName encodingName,
            final @Nullable EventStreamGetParams params, final int maximumFragmentLength,
            final long heartbeatInterval) {
        this.pingExecutor = requireNonNull(pingExecutor);
        this.stream = requireNonNull(stream);
        this.encodingName = requireNonNull(encodingName);
        // FIXME: NETCONF-1102: require params
        this.params = params;
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Handling of the web-socket connected event (web-socket session between local server and remote endpoint has been
     * established). Web-socket session handler is registered at data-change-event / YANG notification listener and
     * the heartbeat ping process is started if it is enabled.
     *
     * @param webSocketSession Created web-socket session.
     * @see OnWebSocketConnect More information about invocation of this method and parameters.
     */
    @OnWebSocketConnect
    public synchronized void onWebSocketConnected(final Session webSocketSession) {
        if (session == null || !session.isOpen()) {
            session = webSocketSession;
            try {
                subscriber = stream.addSubscriber(this, encodingName, params);
            } catch (IllegalArgumentException | XPathExpressionException | UnsupportedEncodingException e) {
                LOG.info("Closing web-socket session {}", webSocketSession, e);
                webSocketSession.close(404, "Unsupported encoding " + encodingName);
                session = null;
                return;
            }

            LOG.debug("A new web-socket session {} has been successfully registered.", webSocketSession);
            if (heartbeatInterval != 0) {
                // sending of PING frame can be long if there is an error on web-socket - from this reason
                // the fixed-rate should not be used
                pingProcess = pingExecutor.startPingProcess(this::sendPing, heartbeatInterval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Handling of web-socket session closed event (timeout, error, or both parties closed session). Removal
     * of subscription at listener and stopping of the ping process.
     *
     * @param statusCode Web-socket status code.
     * @param reason     Reason, why the web-socket is closed (for example, reached timeout).
     * @see OnWebSocketClose More information about invocation of this method and parameters.
     */
    @OnWebSocketClose
    public synchronized void onWebSocketClosed(final int statusCode, final String reason) {
        // note: there is not guarantee that Session.isOpen() returns true - it is better to not check it here
        // using 'session != null && session.isOpen()'
        if (session != null) {
            LOG.debug("Web-socket session has been closed with status code {} and reason message: {}.", statusCode,
                reason);
            if (subscriber != null) {
                subscriber.close();
                subscriber = null;
            }
            stopPingProcess();
        }
    }

    /**
     * Handling of error in web-socket implementation. Subscription at listener is removed, open session is closed
     * and ping process is stopped.
     *
     * @param error Error details.
     * @see OnWebSocketError More information about invocation of this method and parameters.
     */
    @OnWebSocketError
    public synchronized void onWebSocketError(final Throwable error) {
        if (error instanceof CloseException && error.getCause() instanceof TimeoutException timeout) {
            // A timeout is expected, do not log the complete stack trace
            LOG.info("Web-socket closed by timeout: {}", timeout.getMessage());
        } else {
            LOG.warn("An error occurred on web-socket: ", error);
        }
        if (session != null) {
            LOG.info("Trying to close web-socket session {} gracefully after error.", session);
            if (subscriber != null) {
                subscriber.close();
                subscriber = null;
            }
            if (session.isOpen()) {
                session.close();
            }
            stopPingProcess();
        }
    }

    private void stopPingProcess() {
        if (pingProcess != null) {
            pingProcess.close();
            pingProcess = null;
        }
    }

    @Override
    public synchronized void endOfStream() {
        if (session != null && session.isOpen()) {
            session.close();
        }
        stopPingProcess();
    }

    /**
     * Sensing of string message to remote endpoint of {@link org.eclipse.jetty.websocket.api.Session}. If the maximum
     * fragment length is set to non-zero positive value and input message exceeds this value, message is fragmented
     * to multiple message fragments which are send individually but still in one web-socket transaction.
     *
     * @param message Message data to be send over web-socket session.
     */
    @Override
    public synchronized void sendDataMessage(final String message) {
        if (Strings.isNullOrEmpty(message)) {
            // FIXME: should this be tolerated?
            return;
        }

        if (session != null && session.isOpen()) {
            final var remoteEndpoint = session.getRemote();
            if (maximumFragmentLength == 0 || message.length() <= maximumFragmentLength) {
                sendDataMessage(message, remoteEndpoint);
            } else {
                sendFragmentedMessage(splitMessageToFragments(message, maximumFragmentLength), remoteEndpoint);
            }
        } else {
            LOG.trace("Message with body '{}' is not sent because underlay web-socket session is not open.", message);
        }
    }

    private void sendDataMessage(final String message, final RemoteEndpoint remoteEndpoint) {
        try {
            remoteEndpoint.sendString(message);
            LOG.trace("Message with body '{}' has been successfully sent to remote endpoint {}.", message,
                remoteEndpoint);
        } catch (IOException e) {
            LOG.warn("Cannot send message over web-socket session {}.", session, e);
        }
    }

    private void sendFragmentedMessage(final List<String> orderedFragments, final RemoteEndpoint remoteEndpoint) {
        for (int i = 0; i < orderedFragments.size(); i++) {
            final String fragment = orderedFragments.get(i);
            final boolean last = i == orderedFragments.size() - 1;

            try {
                remoteEndpoint.sendPartialString(fragment, last);
            } catch (IOException e) {
                LOG.warn("Cannot send message fragment number {} over web-socket session {}. All other fragments of "
                    + " the message are dropped too.", i, session, e);
                return;
            }
            LOG.trace("Message fragment number {} with body '{}' has been successfully sent to remote endpoint {}.", i,
                fragment, remoteEndpoint);
        }
    }

    private synchronized void sendPing() {
        try {
            Objects.requireNonNull(session).getRemote().sendPing(ByteBuffer.wrap(PING_PAYLOAD));
        } catch (IOException e) {
            LOG.warn("Cannot send ping message over web-socket session {}.", session, e);
        }
    }

    private static List<String> splitMessageToFragments(final String inputMessage, final int maximumFragmentLength) {
        final var parts = new ArrayList<String>();
        int length = inputMessage.length();
        for (int i = 0; i < length; i += maximumFragmentLength) {
            parts.add(inputMessage.substring(i, Math.min(length, i + maximumFragmentLength)));
        }
        return parts;
    }
}