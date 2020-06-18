/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.http11;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.base.ServerConnectorConfig;

/**
 * Configuration for http 1.1 Jetty server connector.
 */
public class Http11Configuration {

    private final HttpConnectionFactory httpConnectionFactory;

    public Http11Configuration(final ServerConnectorConfig config) {
        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(config.getInetSocketAddress().getPort());
        httpConfig.setSendXPoweredBy(true);
        httpConfig.setSendServerVersion(true);

        final HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        httpConnectionFactory = new HttpConnectionFactory(httpsConfig);
    }

    public HttpConnectionFactory getHttpConnectionFactory() {
        return httpConnectionFactory;
    }
}
