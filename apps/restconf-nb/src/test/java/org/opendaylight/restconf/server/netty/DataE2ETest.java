/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.jaxrs.JaxRsLocationProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

class DataE2ETest extends AbstractDataBrokerTest {
    private NettyEndpoint endpoint;
    private ServerSocket socket;
    private String serverAddress;
    private BootstrapFactory bootstrapFactory;
    private HTTPClient client;

    @BeforeEach
    void setUp() throws Exception {
        final var domDataBroker = createDataBrokerTestCustomizer().getDOMDataBroker();

        serverAddress = InetAddress.getLoopbackAddress().getHostAddress();
        socket = new ServerSocket(0);

        final var securityManager = new DefaultSecurityManager();
        final var principalService = new AAAShiroPrincipalService(securityManager);
        final var serverTransport = ConfigUtils.serverTransportTcp(serverAddress, 8181);
        final var serverStackGrouping = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return serverTransport;
            }
        };
        final var configuration = new NettyEndpointConfiguration(
            ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            new URI("rests"), "restconf-server-e2e", 0,
            NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping
        );

        final var dataBindProvider = new MdsalDatabindProvider(new FixedDOMSchemaService(getRuntimeContext()
            .modelContext()));
        final var rpcService = new RouterDOMRpcService(new DOMRpcRouter());
        final var actionService = new RouterDOMActionService(new DOMRpcRouter());
        final var mountPointService = new DOMMountPointServiceImpl();

        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, rpcService, actionService,
            mountPointService);
        final var registry = new MdsalRestconfStreamRegistry(new JaxRsLocationProvider(), domDataBroker);
        endpoint = new NettyEndpoint(server, principalService, registry, configuration);

        bootstrapFactory = new BootstrapFactory("restconf-client-e2e", 0);
        final var clientransport = ConfigUtils.clientTransportTcp(serverAddress, 8181);
        final var clientStackGrouping = new HttpClientStackGrouping() {
            @Override
            public @NonNull Class<? extends DataContainer> implementedInterface() {
                return HttpClientStackGrouping.class;
            }

            @Override
            public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.Transport getTransport() {
                return clientransport;
            }
        };

        client =  HTTPClient.connect(mock(TransportChannelListener.class), bootstrapFactory.newBootstrap(),
            clientStackGrouping, true).get(2, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        bootstrapFactory.close();
        socket.close();
        endpoint.deactivate();
        client.shutdown();
    }

    @Test
    void userAuthenticationTest() {
        // TODO
    }

    @Test
    void crudOperationsTest() {
        // TODO
    }

    @Test
    void errorHandlingTest() {
        // TODO
    }
}
