/*
 * Copyright (c) 2018 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.sse;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE session handler that is responsible for controlling of session, managing subscription to data-change-event or
 * notification listener, and sending of data over established SSE session.
 */
public class SSESessionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SSESessionHandler.class);
    private static final String PING_PAYLOAD = "ping";

    private final ScheduledExecutorService executorService;
    private final BaseListenerInterface listener;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;
    private final EventOutput output;
    private ScheduledFuture<?> pingProcess;

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
    public SSESessionHandler(final ScheduledExecutorService executorService, final EventOutput output,
            final BaseListenerInterface listener, final int maximumFragmentLength, final int heartbeatInterval) {
        this.executorService = executorService;
        this.output = output;
        this.listener = listener;
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Initialization of SSE connection. SSE session handler is registered at data-change-event / YANG notification
     * listener and the heartbeat ping process is started if it is enabled.
     */
    public synchronized void init() {
        listener.addSubscriber(this);
        if (heartbeatInterval != 0) {
            pingProcess = executorService.scheduleWithFixedDelay(this::sendPingMessage, heartbeatInterval,
                    heartbeatInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Handling of SSE session close event. Removal of subscription at listener and stopping of the ping process.
     */
    public synchronized void close() {
        listener.removeSubscriber(this);
        stopPingProcess();
    }

    /**
     * Sending of string message to outbound Server-Sent Events channel
     * {@link org.glassfish.jersey.media.sse.EventOutput}. SSE is automatically split to fragments with new line
     * character. If the maximum fragment length is set to non-zero positive value and input message exceeds this
     * value, message is manually fragmented to multiple message fragments which are send individually. Previous
     * fragmentation is removed.
     *
     * @param message Message data to be send over web-socket session.
     */
    public synchronized void sendDataMessage(final String message) {
        if (Strings.isNullOrEmpty(message)) {
            // FIXME: should this be tolerated?
            return;
        }
        if (output.isClosed()) {
            close();
            return;
        }
        if (maximumFragmentLength != 0 && message.length() > maximumFragmentLength) {
            sendMessage(splitMessageToFragments(message));
        } else {
            sendMessage(message);
        }
    }

    private void sendMessage(final String message) {
        try {
            output.write(new OutboundEvent.Builder().data(String.class, message).build());
        } catch (IOException e) {
            LOG.warn("Connection from client {} is closed", this);
            LOG.debug("Connection from client is closed:", e);
        }
    }

    /**
     * Split message to fragments. SSE automatically fragment string with new line character.
     * For manual fragmentation we will remove all new line characters
     *
     * @param message Message data to be split.
     * @return
     */
    public String splitMessageToFragments(final String message) {
        String outputMessage = "";
        // Remove all end of line characters which create fragments
        String inputmessage = message.replaceAll("(\\r|\\n)", "");
        int length = inputmessage.length();
        for (int i = 0; i < length; i += maximumFragmentLength) {
            outputMessage = outputMessage + inputmessage.substring(i, Math.min(length, i + maximumFragmentLength))
                    + "\r\n";
        }
        return outputMessage;
    }

    //TODO:will be good send ping message as event?
    private synchronized void sendPingMessage() {
        LOG.debug("sending PING:{}", PING_PAYLOAD);
        sendDataMessage(PING_PAYLOAD);
    }

    private void stopPingProcess() {
        if (pingProcess != null && !pingProcess.isDone() && !pingProcess.isCancelled()) {
            pingProcess.cancel(true);
        }
    }

    public synchronized boolean isConnected() {
        return !output.isClosed();
    }

    // TODO:return some type of identification of connection
    public String toString() {
        return output.toString();
    }
}
