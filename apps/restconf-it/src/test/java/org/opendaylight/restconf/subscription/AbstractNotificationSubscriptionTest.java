/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

public class AbstractNotificationSubscriptionTest extends AbstractDataBrokerTest {
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final String RESTCONF = "restconf";

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private String host;
    private SimpleNettyEndpoint endpoint;

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-openapi", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-openapi", 8);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // transport configuration
        final var port = randomBindablePort();
        host = localAddress + ":" + port;
        final var serverTransport = ConfigUtils.serverTransportTcp(localAddress, port);
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

        // AAA services
        final var securityManager = new DefaultWebSecurityManager(new AuthenticatingRealm() {
            @Override
            protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
                    throws AuthenticationException {
                final var principal = (String) token.getPrincipal();
                final var credentials = new String((char[]) token.getCredentials());
                if (USERNAME.equals(principal) && PASSWORD.equals(credentials)) {
                    return new SimpleAuthenticationInfo(principal, credentials, "user");
                }
                return null;
            }
        });
        final var principalService = new AAAShiroPrincipalService(securityManager);

        // MDSAL services
        setup();
        final var domDataBroker = getDomBroker();
        final var schemaContext = getRuntimeContext().modelContext();
        final var schemaService = new FixedDOMSchemaService(schemaContext);
        final var dataBindProvider = new MdsalDatabindProvider(schemaService);
        final var domRpcRouter = new DOMRpcRouter(schemaService);
        final var domRpcService = new RouterDOMRpcService(domRpcRouter);
        final var domActionService = new RouterDOMActionService(domRpcRouter);
        final var domMountPointService = new DOMMountPointServiceImpl();
        final var streamRegistry = new MdsalRestconfStreamRegistry(uri -> uri.resolve("streams"), domDataBroker);
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, domRpcService, domActionService,
            domMountPointService, List.of());

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), RESTCONF,
            MessageEncoding.JSON, serverStackGrouping);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
            configuration);
    }

    @AfterEach
    void afterEach() throws Exception {
        endpoint.close();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        sshTransportStackFactory.close();
    }

    /**
     * Find a local port which has a good chance of not failing {@code bind()} due to a conflict.
     *
     * @return a local port
     */
    protected static final int randomBindablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
