/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that is used for creation of new web-sockets based on HTTP/HTTPS upgrade request.
 *
 * @param executorService       Executor for creation of threads for controlling of web-socket sessions.
 * @param maximumFragmentLength Maximum web-socket fragment length in number of Unicode code units (characters)
 *                              (exceeded message length leads to fragmentation of messages).
 * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
 */
@Deprecated(since = "7.0.0", forRemoval = true)
final class WebSocketFactory implements WebSocketCreator {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketFactory.class);

    private final RestconfStream.Registry streamRegistry;
    private final PingExecutor pingExecutor;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;
    private final String streamsPath;

    WebSocketFactory(final String restconf, final RestconfStream.Registry streamRegistry,
            final PingExecutor pingExecutor, final int maximumFragmentLength, final int heartbeatInterval) {
        this.streamRegistry = requireNonNull(streamRegistry);
        this.pingExecutor = requireNonNull(pingExecutor);
        this.maximumFragmentLength = maximumFragmentLength;
        this.heartbeatInterval = heartbeatInterval;
        streamsPath = '/' + requireNonNull(restconf) + '/' + URLConstants.STREAMS_SUBPATH + '/';
    }

    /**
     * Creation of the new web-socket based on input HTTP/HTTPS upgrade request. Web-socket is created only if the
     * data listener for input URI can be found (results in status code
     * {@value HttpServletResponse#SC_SWITCHING_PROTOCOLS}); otherwise status code
     * {@value HttpServletResponse#SC_NOT_FOUND} is set in upgrade response.
     *
     * @param req the request details
     * @param resp the response details
     * @return Created web-socket instance or {@code null} if the web-socket cannot be created.
     */
    @Override
    public Object createWebSocket(final ServletUpgradeRequest req, final ServletUpgradeResponse resp) {
        final var path = req.getRequestURI().getPath();
        if (!path.startsWith(streamsPath)) {
            LOG.debug("Request path '{}' does not start with '{}'", path, streamsPath);
            return notFound(resp);
        }

        final var stripped = path.substring(streamsPath.length());
        final int slash = stripped.indexOf('/');
        if (slash < 0) {
            LOG.debug("Request path '{}' does not contain encoding", path);
            return notFound(resp);
        }
        if (slash == 0) {
            LOG.debug("Request path '{}' contains empty encoding", path);
            return notFound(resp);
        }
        final var streamName = stripped.substring(slash + 1);
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            return notFound(resp);
        }

        LOG.debug("Listener for stream with name {} has been found, web-socket session handler will be created",
            streamName);
        resp.setSuccess(true);
        resp.setStatusCode(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
        // note: every web-socket manages PING process individually because this approach scales better than
        //       sending PING frames at once over all web-socket sessions
        return new WebSocketSender(pingExecutor, stream, new EncodingName(stripped.substring(0, slash)),
            null, maximumFragmentLength, heartbeatInterval);
    }

    private static Object notFound(final ServletUpgradeResponse resp) {
        resp.setSuccess(false);
        resp.setStatusCode(HttpServletResponse.SC_NOT_FOUND);
        return null;
    }
}