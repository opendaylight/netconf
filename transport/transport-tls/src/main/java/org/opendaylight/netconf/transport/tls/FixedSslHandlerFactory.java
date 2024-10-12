/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import io.netty.handler.ssl.SslContext;
import java.net.SocketAddress;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.TlsClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.TlsServerGrouping;

public final class FixedSslHandlerFactory extends SslHandlerFactory {
    private final SslContext sslContext;

    public FixedSslHandlerFactory(final SslContext sslContext) {
        this.sslContext = requireNonNull(sslContext);
    }

    public FixedSslHandlerFactory(final TlsClientGrouping clientParams) throws UnsupportedConfigurationException {
        this(createSslContext(clientParams));
    }

    public FixedSslHandlerFactory(final TlsServerGrouping serverParams) throws UnsupportedConfigurationException {
        this(createSslContext(serverParams));
    }

    @Override
    protected SslContext getSslContext(final SocketAddress remoteAddress) {
        return sslContext;
    }
}
