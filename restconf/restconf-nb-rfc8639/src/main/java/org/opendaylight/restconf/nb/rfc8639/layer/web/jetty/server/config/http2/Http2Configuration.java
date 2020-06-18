/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.http2;

import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.NotificationsHolder;
import org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.base.ServerConnectorConfig;

/**
 * Configuration for http/2 Jetty server connector.
 */
public class Http2Configuration {

    private final HTTP2ServerConnectionFactory http2ServerConnectionFactory;

    public Http2Configuration(final ServerConnectorConfig config, final NotificationsHolder notificationsHolder,
                              final DOMSchemaService domSchemaService,
                              final DOMMountPointService domMountPointService) {
        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSecurePort(config.getInetSocketAddress().getPort());
        httpConfiguration.setSecureScheme("https");
        httpConfiguration.setSendXPoweredBy(true);
        httpConfiguration.setSendServerVersion(true);

        final HttpConfiguration httpsConfig = new HttpConfiguration(httpConfiguration);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        http2ServerConnectionFactory = new NotificationStreamServerConnectionFactory(httpsConfig, notificationsHolder,
                domSchemaService, domMountPointService);
    }

    public HTTP2ServerConnectionFactory getHttp2ServerConnectionFactory() {
        return http2ServerConnectionFactory;
    }
}
