/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.http2;

import java.util.concurrent.Executor;
import org.eclipse.jetty.http2.FlowControlStrategy;
import org.eclipse.jetty.http2.HTTP2Connection;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.generator.Generator;
import org.eclipse.jetty.http2.parser.RateControl;
import org.eclipse.jetty.http2.parser.ServerParser;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerSession;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NotificationStreamServerConnectionFactory extends HTTP2ServerConnectionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationStreamServerConnectionFactory.class);

    private final NotificationStreamConnectionListener connectionListener;
    private final NotificationsHolder notificationsHolder;
    private final DOMSchemaService domSchemaService;
    private final DOMMountPointService domMountPointService;

    NotificationStreamServerConnectionFactory(final HttpConfiguration httpConfiguration,
                                              final NotificationsHolder notificationsHolder,
                                              final DOMSchemaService domSchemaService,
                                              final DOMMountPointService domMountPointService) {
        super(httpConfiguration);
        this.connectionListener = new NotificationStreamConnectionListener();
        this.notificationsHolder = notificationsHolder;
        this.domSchemaService = domSchemaService;
        this.domMountPointService = domMountPointService;
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        LOG.debug("newConnection: {}", endPoint.getLocalAddress().toString());
        final ServerSessionListener listener = new NotificationStreamSessionListener(connector, endPoint,
                notificationsHolder, domSchemaService, domMountPointService);

        final Generator generator = new Generator(connector.getByteBufferPool(), getMaxDynamicTableSize(),
                getMaxHeaderBlockFragment());
        final FlowControlStrategy flowControl = getFlowControlStrategyFactory().newFlowControlStrategy();
        final HTTP2ServerSession session = new HTTP2ServerSession(connector.getScheduler(), endPoint, generator,
                listener, flowControl);
        session.setMaxLocalStreams(getMaxConcurrentStreams());
        session.setMaxRemoteStreams(getMaxConcurrentStreams());
        // For a single stream in a connection, there will be a race between
        // the stream idle timeout and the connection idle timeout. However,
        // the typical case is that the connection will be busier and the
        // stream idle timeout will expire earlier than the connection's.
        long streamIdleTimeout = getStreamIdleTimeout();
        if (streamIdleTimeout <= 0) {
            streamIdleTimeout = endPoint.getIdleTimeout();
        }
        session.setStreamIdleTimeout(streamIdleTimeout);
        session.setInitialSessionRecvWindow(getInitialSessionRecvWindow());

        final Executor executor = connector.getExecutor();

        final ServerParser parser = newServerParser(connector, session, RateControl.NO_RATE_CONTROL);

        final HTTP2Connection connection = new HTTP2ServerConnection(connector.getByteBufferPool(), executor,
                endPoint, getHttpConfiguration(), parser, session, getInputBufferSize(), listener);
        connection.addListener(connectionListener);
        return configure(connection, connector, endPoint);
    }
}
