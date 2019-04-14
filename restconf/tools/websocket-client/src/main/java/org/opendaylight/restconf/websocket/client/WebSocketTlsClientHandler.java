/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.websocket.client;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * TLS-secured version of the {@link WebSocketClientHandler}. SSL context factory is appended to the {@link HttpClient}.
 */
class WebSocketTlsClientHandler extends WebSocketClientHandler {

    private final SslContextFactory sslContextFactory;

    /**
     * Creation of the secured version of the web-socket client handler.
     *
     * @param uri                      Full stream URI including schema.
     * @param pingInterval             Interval ath which the ping messages should be sent to remote server.
     * @param pingMessage              Text of the ping message.
     * @param scheduledExecutorService Ping service executor.
     * @param sslContextFactory        SSL context factory that holds SSL/TLS security settings.
     */
    WebSocketTlsClientHandler(final URI uri, final int pingInterval, final String pingMessage,
            final ScheduledExecutorService scheduledExecutorService, final SslContextFactory sslContextFactory) {
        super(uri, pingInterval, pingMessage, scheduledExecutorService);
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    WebSocketClient getWebSocketClient() {
        final HttpClient httpClient = new HttpClient(sslContextFactory);
        return new WebSocketClient(httpClient);
    }
}