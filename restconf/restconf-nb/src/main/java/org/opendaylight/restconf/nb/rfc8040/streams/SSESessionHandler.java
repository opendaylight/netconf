/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE session handler that is responsible for controlling of session, managing subscription to data-change-event or
 * notification listener, and sending of data over established SSE session.
 */
public final class SSESessionHandler implements StreamSessionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SSESessionHandler.class);
    private static final CharMatcher CR_OR_LF = CharMatcher.anyOf("\r\n");

    private final ScheduledExecutorService executorService;
    // FIXME: this really should include subscription details like formatter etc.
    private final RestconfStream<?> listener;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;
    private final SseEventSink sink;
    private final Sse sse;

    private ScheduledFuture<?> pingProcess;
    private Subscriber<?> subscriber;

    /**
     * Creation of the new server-sent events session handler.
     *
     * @param executorService Executor that is used for periodical sending of SSE ping messages to keep session up even
     *            if the notifications doesn't flow from server to clients or clients don't implement ping-pong
     *            service.
     * @param listener YANG notification or data-change event listener to which client on this SSE session subscribes
     *            to.
     * @param maximumFragmentLength Maximum fragment length in number of Unicode code units (characters). If this
     *            parameter is set to 0, the maximum fragment length is disabled and messages up to 64 KB can be sent
     *            (exceeded notification length ends in error). If the parameter is set to non-zero positive value,
     *            messages longer than this parameter are fragmented into multiple SSE messages sent in one
     *            transaction.
     * @param heartbeatInterval Interval in milliseconds of sending of ping control frames to remote endpoint to keep
     *            session up. Ping control frames are disabled if this parameter is set to 0.
     */
    public SSESessionHandler(final ScheduledExecutorService executorService, final SseEventSink sink, final Sse sse,
            final RestconfStream<?> listener, final int maximumFragmentLength, final int heartbeatInterval) {
        this.executorService = executorService;
        this.sse = sse;
        this.sink = sink;
        this.listener = listener;
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Initialization of SSE connection. SSE session handler is registered at data-change-event / YANG notification
     * listener and the heartbeat ping process is started if it is enabled.
     */
    public synchronized boolean init() {
        final var local = listener.addSubscriber(this);
        if (local == null) {
            return false;
        }

        subscriber = local;
        if (heartbeatInterval != 0) {
            pingProcess = executorService.scheduleWithFixedDelay(this::sendPingMessage, heartbeatInterval,
                heartbeatInterval, TimeUnit.MILLISECONDS);
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
            listener.removeSubscriber(local);
            stopPingProcess();
        }
    }

    @Override
    public synchronized boolean isConnected() {
        return !sink.isClosed();
    }

    /**
     * Sending of string message to outbound Server-Sent Events channel {@link SseEventSink}. SSE is automatically split
     * to fragments with new line character. If the maximum fragment length is set to non-zero positive value and input
     * message exceeds this value, message is manually fragmented to multiple message fragments which are send
     * individually. Previous fragmentation is removed.
     *
     * @param message Message data to be send over web-socket session.
     */
    @Override
    public synchronized void sendDataMessage(final String message) {
        if (Strings.isNullOrEmpty(message)) {
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

    private synchronized void sendPingMessage() {
        if (!sink.isClosed()) {
            LOG.debug("sending PING");
            sink.send(sse.newEventBuilder().comment("ping").build());
        } else {
            close();
        }
    }

    private void stopPingProcess() {
        if (pingProcess != null && !pingProcess.isDone() && !pingProcess.isCancelled()) {
            pingProcess.cancel(true);
        }
    }

    // TODO:return some type of identification of connection
    @Override
    public String toString() {
        return sink.toString();
    }
}
