/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.websockets;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;
import javax.servlet.annotation.WebServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants;

/**
 * Web-socket servlet listening on ws or wss schemas for created data-change-event or notification streams.
 */
@WebServlet(name = "WebSocket servlet", urlPatterns = {
        '/' + RestconfStreamsConstants.CREATE_DATA_SUBSCRIPTION + "/*",
        '/' + RestconfStreamsConstants.CREATE_NOTIFICATION_STREAM + "/*"})
@SuppressFBWarnings({"SE_NO_SERIALVERSIONID", "SE_BAD_FIELD"})
public class WebSocketInitializer extends WebSocketServlet {

    private static final int MAX_FRAGMENT_LENGTH = 2 ^ 14;

    private final ScheduledExecutorService executorService;
    private final int maximumFragmentLength;
    private final int idleTimeout;
    private final int heartbeatInterval;

    /**
     * Creation of the web-socket initializer.
     *
     * @param scheduledThreadPool   ODL thread pool used for fetching of scheduled executors.
     * @param maximumFragmentLength Maximum web-socket fragment length in bytes (exceeded message length leads
     *                              to fragmentation of messages).
     * @param idleTimeout           Maximum idle time of web-socket session before the session is closed (milliseconds).
     * @param heartbeatInterval     Interval in milliseconds between sending of ping control frames.
     */
    public WebSocketInitializer(@Nonnull final ScheduledThreadPool scheduledThreadPool,
            final int maximumFragmentLength, final int idleTimeout, final int heartbeatInterval) {
        Preconditions.checkArgument(idleTimeout > 0, "Idle timeout must be specified by positive value.");
        Preconditions.checkArgument(maximumFragmentLength >= 0 && maximumFragmentLength < MAX_FRAGMENT_LENGTH,
                "Maximum fragment length must be disabled (0) or specified by positive value "
                        + "less than 64 KB.");
        Preconditions.checkArgument(heartbeatInterval >= 0, "Heartbeat ping interval must be "
                + "disabled (0) or specified by positive value.");

        this.executorService = Preconditions.checkNotNull(scheduledThreadPool).getExecutor();
        this.maximumFragmentLength = maximumFragmentLength;
        this.idleTimeout = idleTimeout;
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * Configuration of the web-socket factory - idle timeout and specified factory object.
     *
     * @param factory Configurable web-socket factory.
     */
    @Override
    public void configure(final WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(idleTimeout);
        factory.setCreator(new WebSocketFactory(executorService, maximumFragmentLength, heartbeatInterval));
    }
}