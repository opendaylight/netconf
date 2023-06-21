/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serial;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web-socket servlet listening on ws or wss schemas for created data-change-event or notification streams.
 */
@Singleton
public final class WebSocketInitializer extends WebSocketServlet {
    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings(value = "SE_BAD_FIELD",
        justification = "Servlet/WebSocket bridge, we need this service for heartbeats")
    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;
    private final int idleTimeoutMillis;

    /**
     * Creation of the web-socket initializer.
     *
     * @param scheduledThreadPool    ODL thread pool used for fetching of scheduled executors.
     * @param configuration          Web-socket configuration holder.
     */
    @Inject
    public WebSocketInitializer(final ScheduledThreadPool scheduledThreadPool,
            final StreamsConfiguration configuration) {
        executorService = scheduledThreadPool.getExecutor();
        maximumFragmentLength = configuration.maximumFragmentLength();
        heartbeatInterval = configuration.heartbeatInterval();
        idleTimeoutMillis = configuration.idleTimeout();
    }

    /**
     * Configuration of the web-socket factory - idle timeout and specified factory object.
     *
     * @param factory Configurable web-socket factory.
     */
    @Override
    public void configure(final WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(idleTimeoutMillis);
        factory.setCreator(new WebSocketFactory(executorService, maximumFragmentLength, heartbeatInterval));
    }

    /**
     * Factory that is used for creation of new web-sockets based on HTTP/HTTPS upgrade request.
     */
    @VisibleForTesting
    static final class WebSocketFactory implements WebSocketCreator {
        private static final Logger LOG = LoggerFactory.getLogger(WebSocketFactory.class);

        private final ScheduledExecutorService executorService;
        // FIXME: inject this reference
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
            final var streamName = ListenersBroker.createStreamNameFromUri(
                servletUpgradeRequest.getRequestURI().getRawPath());

            final var listener = listenersBroker.listenerFor(streamName);
            if (listener == null) {
                LOG.debug("Listener for stream with name {} was not found.", streamName);
                servletUpgradeResponse.setSuccess(false);
                servletUpgradeResponse.setStatusCode(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }

            LOG.debug("Listener for stream with name {} has been found, web-socket session handler will be created",
                streamName);
            servletUpgradeResponse.setSuccess(true);
            servletUpgradeResponse.setStatusCode(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
            // note: every web-socket manages PING process individually because this approach scales better than
            //       sending of PING frames at once over all web-socket sessions
            return new WebSocketSessionHandler(executorService, listener, maximumFragmentLength, heartbeatInterval);
        }
    }
}
