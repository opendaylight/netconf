/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.BaseListenerInterface;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that is used for creation of new web-sockets based on HTTP/HTTPS upgrade request.
 */
class WebSocketFactory implements WebSocketCreator {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketFactory.class);

    private final ScheduledExecutorService executorService;
    private final ListenersBroker listenersBroker = ListenersBroker.getInstance();
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    /**
     * Creation of the web-socket factory.
     *
     * @param executorService       Executor for creation of threads for controlling of web-socket sessions.
     * @param maximumFragmentLength Maximum web-socket fragment length in number of Unicode code units (characters)
     *                              (exceeded message length leads to fragmentation of messages).
     * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
     */
    WebSocketFactory(final ScheduledExecutorService executorService, final int maximumFragmentLength,
            final int heartbeatInterval) {
        this.executorService = executorService;
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Creation of the new web-socket based on input HTTP/HTTPS upgrade request. Web-socket is created only if the
     * data listener for input URI can be found (results in status code 101); otherwise status code 404 is set
     * in upgrade response.
     *
     * @param servletUpgradeRequest  Upgrade request.
     * @param servletUpgradeResponse Upgrade response.
     * @return Created web-socket instance or {@code null} if the web-socket cannot be created.
     */
    @Override
    public Object createWebSocket(final ServletUpgradeRequest servletUpgradeRequest,
            final ServletUpgradeResponse servletUpgradeResponse) {
        final String requestUri = servletUpgradeRequest.getRequestURI().getRawPath();
        final String streamName = ListenersBroker.createStreamNameFromUri(requestUri);

        final Optional<BaseListenerInterface> listener = listenersBroker.getListenerFor(streamName);
        if (listener.isPresent()) {
            LOG.debug("Listener for stream with name {} has been found, web-socket session handler will be created.",
                    streamName);
            servletUpgradeResponse.setSuccess(true);
            servletUpgradeResponse.setStatusCode(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
            // note: every web-socket manages PING process individually because this approach scales better than sending
            // of PING frames at once over all web-socket sessions
            return new WebSocketSessionHandler(executorService, listener.get(), maximumFragmentLength,
                    heartbeatInterval);
        } else {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            servletUpgradeResponse.setSuccess(false);
            servletUpgradeResponse.setStatusCode(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
    }
}