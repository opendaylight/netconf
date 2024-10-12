/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tls.SslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.TlsServerGrouping;

class HttpSslHandlerFactory extends SslHandlerFactory {

    private static final ApplicationProtocolConfig APN = new ApplicationProtocolConfig(
        ApplicationProtocolConfig.Protocol.ALPN,
        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
        ApplicationProtocolNames.HTTP_2,
        ApplicationProtocolNames.HTTP_1_1);

    private final SslContext sslContext;

    HttpSslHandlerFactory(final @NonNull TlsServerGrouping params) throws UnsupportedConfigurationException {
        sslContext = createSslContext(params, APN);
    }

    HttpSslHandlerFactory(final @NonNull TlsClientGrouping params, final boolean http2)
            throws UnsupportedConfigurationException {
        sslContext = http2 ? createSslContext(params, APN) : createSslContext(params);
    }

    @Override
    protected @Nullable SslContext getSslContext(SocketAddress remoteAddress) {
        return sslContext;
    }
}
