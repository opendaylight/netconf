/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.restconf.client.RestconfClient;
import org.opendaylight.restconf.client.RestconfConnection;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.IetfRestconfClientData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.restconf.client.app.grouping.initiate.RestconfServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.restconf.client.app.grouping.initiate.restconf.server.endpoints.Endpoint;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.client.rev240208.restconf.client.initiate.stack.grouping.transport.Https;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link RestconfClient}.
 */
@Component
@NonNullByDefault
public final class DefaultRestconfClient implements RestconfClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestconfClient.class);

    private final BindingRuntimeContext runtimeContext;

    @Activate
    public DefaultRestconfClient(@Reference final YangParserFactory factory,
            @Reference final BindingRuntimeGenerator generator) throws YangParserException {
        runtimeContext = BindingRuntimeHelpers.createRuntimeContext(factory, generator, IetfRestconfClientData.class);
        LOG.debug("RESTCONF client services activated");
    }

    @Deactivate
    @SuppressWarnings("static-method")
    void deactivate() {
        LOG.debug("RESTCONF client services deactivated");
    }

    @Override
    public ListenableFuture<RestconfConnection> connect(final RestconfServer server)
            throws UnsupportedConfigurationException {
        // FIXME: proper implementation: we have a reconnection strategy, in terms of servers and in terms of connection
        //        persistence and use the other connect() method to establish connections
        throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<RestconfConnection> connect(final String serverName, final Endpoint endpoint)
            throws UnsupportedConfigurationException {
        final var transport = endpoint.getTransport();
        if (transport instanceof Https https) {
            final var decap = https.nonnullHttps();
//            *   container tcp-client-parameters {
//                *     uses tcpc:tcp-client-grouping {
//                *       refine remote-port {
//                *         default 443;
//                *       }
//                *     }
//                *   }
//                *   container tls-client-parameters {
//                *     uses tlsc:tls-client-grouping;
//                *   }
//                *   container http-client-parameters {
//                *     uses httpc:http-client-grouping;
//                *   }
//                *   container restconf-client-parameters {
//                *     uses rcc:restconf-client-grouping;
//                *   }

            final var future = SettableFuture.<RestconfConnection>create();
            HTTPClient.connect(new TransportChannelListener() {
                @Override
                public void onTransportChannelEstablished(final TransportChannel channel) {
                    // FIXME: proper implementation
                    future.set(new DefaultRestconfConnection(runtimeContext));
                }

                @Override
                public void onTransportChannelFailed(final Throwable cause) {
                    future.setException(cause);
                }
            }, null, (HttpClientStackGrouping) decap, false);
            return future;
        }
        throw new IllegalArgumentException("Unsupported transport " + transport);
    }
}
