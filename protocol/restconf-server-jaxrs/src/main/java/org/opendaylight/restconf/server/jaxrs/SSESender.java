/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.restconf.server.spi.StreamEncoding;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE session handler that is responsible for controlling of session, managing subscription to data-change-event or
 * notification listener, and sending of data over established SSE session.
 */
final class SSESender implements Sender {
    private static final Logger LOG = LoggerFactory.getLogger(SSESender.class);
    private static final CharMatcher CR_OR_LF = CharMatcher.anyOf("\r\n");

    private final PingExecutor pingExecutor;
    private final RestconfStream<?> stream;
    private final StreamEncoding encoding;
    private final EventStreamGetParams params;
    private final SseEventSink sink;
    private final Sse sse;
    private final int maximumFragmentLength;
    private final long heartbeatMillis;

    private Registration pingProcess;
    private Registration subscriber;

    /**
     * Creation of the new server-sent events session handler.
     *
     * @param pingExecutor Executor that is used for periodical sending of SSE ping messages to keep session up even
     *            if the notifications doesn't flow from server to clients or clients don't implement ping-pong
     *            service.
     * @param stream YANG notification or data-change event listener to which client on this SSE session subscribes to.
     * @param maximumFragmentLength Maximum fragment length in number of Unicode code units (characters). If this
     *            parameter is set to 0, the maximum fragment length is disabled and messages up to 64 KB can be sent
     *            (exceeded notification length ends in error). If the parameter is set to non-zero positive value,
     *            messages longer than this parameter are fragmented into multiple SSE messages sent in one
     *            transaction.
     * @param heartbeatMillis Interval in milliseconds of sending of ping control frames to remote endpoint to keep
     *            session up. Ping control frames are disabled if this parameter is set to 0.
     */
    SSESender(final PingExecutor pingExecutor, final SseEventSink sink, final Sse sse, final RestconfStream<?> stream,
            final StreamEncoding encoding, final EventStreamGetParams params, final int maximumFragmentLength,
            final long heartbeatMillis) {
        this.pingExecutor = requireNonNull(pingExecutor);
        this.sse = requireNonNull(sse);
        this.sink = requireNonNull(sink);
        this.stream = requireNonNull(stream);
        this.encoding = requireNonNull(encoding);
        this.params = requireNonNull(params);
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatMillis = heartbeatMillis;
    }

    /**
     * Initialization of SSE connection. SSE session handler is registered at data-change-event / YANG notification
     * listener and the heartbeat ping process is started if it is enabled.
     *
     * @throws UnsupportedEncodingException if the subscriber cannot be instantiated
     * @throws XPathExpressionException if the subscriber cannot be instantiated
     * @throws IllegalArgumentException if the subscriber cannot be instantiated
     */
    public synchronized boolean init() throws UnsupportedEncodingException, XPathExpressionException {
        final var local = stream.addSubscriber(this, encoding, params);
        if (local == null) {
            return false;
        }

        subscriber = local;
        if (heartbeatMillis != 0) {
            pingProcess = pingExecutor.startPingProcess(this::sendPing, heartbeatMillis, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    /**
     * Handling of SSE session close event. Removal of subscription at listener and stopping of the ping process.
     */
    @VisibleForTesting
    synchronized void close() {
        final var local = subscriber;
        if (local != null) {
            local.close();
            stopPingProcess();
        }
    }

    /**
     * Sending of string message to outbound Server-Sent Events channel {@link SseEventSink}. SSE is automatically split
     * to fragments with new line character. If the maximum fragment length is set to non-zero positive value and input
     * message exceeds this value, message is manually fragmented to multiple message fragments which are send
     * individually. Previous fragmentation is removed.
     *
     * @param message Message data to be sent over SSE session.
     */
    @Override
    public synchronized void sendDataMessage(final String message) {
        if (message.isEmpty()) {
            // FIXME: should this be tolerated?
            return;
        }
        if (!sink.isClosed()) {
            final String toSend = maximumFragmentLength != 0 && message.length() > maximumFragmentLength
                ? splitMessageToFragments(message) : message;
            sink.send(sse.newEvent(toSend));
        } else {
            close();
        }
    }

    @Override
    public synchronized void endOfStream() {
        stopPingProcess();
        sink.close();
    }

    /**
     * Split message to fragments. SSE automatically fragment string with new line character.
     * For manual fragmentation we will remove all new line characters
     *
     * @param message Message data to be split.
     * @return splitted message
     */
    private String splitMessageToFragments(final String message) {
        StringBuilder outputMessage = new StringBuilder();
        String inputmessage = CR_OR_LF.removeFrom(message);
        int length = inputmessage.length();
        for (int i = 0; i < length; i += maximumFragmentLength) {
            outputMessage.append(inputmessage, i, Math.min(length, i + maximumFragmentLength)).append("\r\n");
        }
        return outputMessage.toString();
    }

    private synchronized void sendPing() {
        if (!sink.isClosed()) {
            LOG.debug("sending PING");
            sink.send(sse.newEventBuilder().comment("ping").build());
        } else {
            close();
        }
    }

    private void stopPingProcess() {
        if (pingProcess != null) {
            pingProcess.close();
            pingProcess = null;
        }
    }

    // TODO:return some type of identification of connection
    @Override
    public String toString() {
        return sink.toString();
    }
}
