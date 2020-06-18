/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.base;

import java.net.InetSocketAddress;
import org.eclipse.jetty.http.HttpVersion;

/**
 * Configuration for Jetty server connector.
 */
public class ServerConnectorConfig {

    private static final HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_2;
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;

    private final HttpVersion httpVersion;
    private final InetSocketAddress inetSocketAddress;

    /**
     * Initialize with default config values.
     * Default host name is 'localhost'.
     * Default port is 8888.
     * Default http version is http/2.
     */
    public ServerConnectorConfig() {
        this(DEFAULT_PORT);
    }

    /**
     * Initialize with specified port and default ip address and http version.
     *
     * @param port port number
     */
    public ServerConnectorConfig(final int port) {
        this(new InetSocketAddress(DEFAULT_HOST, port), DEFAULT_HTTP_VERSION);
    }

    /**
     * Initialize with specified port and host name nad default http version.
     *
     * @param port port number
     * @param hostname host name
     */
    public ServerConnectorConfig(final int port, final String hostname) {
        this(new InetSocketAddress(hostname, port), DEFAULT_HTTP_VERSION);
    }

    /**
     * Initialize with specified port, host name and http version.
     *
     * @param inetSocketAddress port and host name
     * @param httpVersion http version
     */
    public ServerConnectorConfig(final InetSocketAddress inetSocketAddress, final HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        this.inetSocketAddress = inetSocketAddress;
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }
}
