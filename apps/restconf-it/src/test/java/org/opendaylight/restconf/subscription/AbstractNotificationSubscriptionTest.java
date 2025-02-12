/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
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
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMPublishNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.notifications.SubscriptionResourceProvider;
import org.opendaylight.restconf.notifications.mdsal.MdsalNotificationService;
import org.opendaylight.restconf.notifications.mdsal.RestconfSubscriptionsStreamRegistry;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.netty.TestEventStreamListener;
import org.opendaylight.restconf.server.netty.TestRequestCallback;
import org.opendaylight.restconf.server.netty.TestTransportChannelListener;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.subscription.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.runtime.api.DefaultBindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractNotificationSubscriptionTest extends AbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationSubscriptionTest.class);
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final String RESTCONF = "restconf";
    private static final String APPLICATION_JSON = "application/json";

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private HttpClientStackGrouping clientStackGrouping;
    private String host;
    private SimpleNettyEndpoint endpoint;
    private ContextListener contextListener;

    volatile EventStreamService clientStreamService;
    volatile EventStreamService.StreamControl streamControl;
    DOMNotificationPublishService publishService;

    @Override
    protected BindingRuntimeContext getRuntimeContext() throws Exception {
        final var snapshot = new ModuleInfoSnapshotBuilder(ServiceLoader.load(YangParserFactory.class).findFirst()
            .orElseThrow(() -> new ExceptionInInitializerError("No YangParserFactory found")))
            .add(getModuleInfos()).addModuleFeatures(IetfSubscribedNotificationsData.class,
                new IetfSubscriptionFeatureProvider().supportedFeatures()).build();

        return new DefaultBindingRuntimeContext(ServiceLoader.load(BindingRuntimeGenerator.class).findFirst()
            .orElseThrow(() -> new ExceptionInInitializerError("No BindingRuntimeGenerator found"))
            .generateTypeMapping(snapshot.modelContext()), snapshot);
    }

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-subscription", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-subscription", 8);
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
        clientStackGrouping = new HttpClientStackConfiguration(
            ConfigUtils.clientTransportTcp(localAddress, port, USERNAME, PASSWORD));

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

        // setup notifications service
        final var mdsalNotificationService = new MdsalNotificationService(domDataBroker);
        final var router = new DOMNotificationRouter(32);
        publishService = new RouterDOMPublishNotificationService(router);
        final var subscriptionStateService = new SubscriptionStateService(publishService);
        final var stateMachine = new SubscriptionStateMachine();
        final var streamRegistry = new MdsalRestconfStreamRegistry(uri -> uri.resolve("streams"), domDataBroker);

        // setup registry of streams we can subscribe to
        final var subscribedRegistry = new RestconfSubscriptionsStreamRegistry(domDataBroker);

        final var rpcImplementations = List.of(
            // rpcImplementations
            new EstablishSubscriptionRpc(mdsalNotificationService, subscriptionStateService, stateMachine,
                subscribedRegistry),
            new ModifySubscriptionRpc(mdsalNotificationService, subscriptionStateService, stateMachine,
                subscribedRegistry),
            new DeleteSubscriptionRpc(mdsalNotificationService, subscriptionStateService, stateMachine),
            new KillSubscriptionRpc(mdsalNotificationService, subscriptionStateService, stateMachine)
        );
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, domRpcService, domActionService,
            domMountPointService, rpcImplementations);

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(
            ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), RESTCONF,
            MessageEncoding.JSON, serverStackGrouping);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
            configuration);

        // setup context listener to enable default NETCONF stream
        final var notificationService = new RouterDOMNotificationService(new DOMNotificationRouter(Integer.MAX_VALUE));
        contextListener = new ContextListener(notificationService, schemaService, subscribedRegistry);

        // Register subscription web resource
        final var provider = new SubscriptionResourceProvider(stateMachine, mdsalNotificationService,
            subscribedRegistry);
        endpoint.registerWebResource(provider);
    }

    @AfterEach
    void afterEach() throws Exception {
        contextListener.close();
        endpoint.close();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        sshTransportStackFactory.close();
    }

    FullHttpResponse invokeRequest(final HttpMethod method, final String uri) throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null, null));
    }

    FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, null));
    }

    FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
            final String acceptType, final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, acceptType));
    }

    FullHttpResponse invokeRequest(final FullHttpRequest request) throws Exception {
        return invokeRequest(request, clientStackGrouping);
    }

    FullHttpResponse invokeRequest(final FullHttpRequest request, final HttpClientStackGrouping clientConf)
            throws Exception {
        final var channelListener = new TestTransportChannelListener(ignored -> {
            // no-op
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientConf, false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(channelListener::initialized);
        final var callback = new TestRequestCallback();
        client.invoke(request, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        client.shutdown().get(2, TimeUnit.SECONDS);
        final var response = callback.response();
        assertNotNull(response);
        return response;
    }

    private FullHttpRequest buildRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content, final String acceptType) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        request.headers()
            .set(HttpHeaderNames.HOST, host)
            .set(HttpHeaderNames.ACCEPT, acceptType != null ? acceptType : mediaType)
            .set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
        if (method != HttpMethod.GET) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        return request;
    }

    /**
     * Find a local port which has a good chance of not failing {@code bind()} due to a conflict.
     *
     * @return a local port
     */
    private static int randomBindablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    protected HTTPClient startStreamClient() throws Exception {
        final var transportListener = new TestTransportChannelListener(channel ->
            clientStreamService = SseUtils.enableClientSse(channel));
        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(transportListener::initialized);
        assertNotNull(clientStreamService);
        return streamClient;
    }

    protected TestEventStreamListener startSubscriptionStream(final String subscriptionId) {
        final var eventListener = new TestEventStreamListener();
        clientStreamService.startEventStream("localhost", "/subscriptions/" + subscriptionId, eventListener,
            new EventStreamService.StartCallback() {
                @Override
                public void onStreamStarted(final EventStreamService.StreamControl control) {
                    streamControl = control;
                }

                @Override
                public void onStartFailure(final Exception cause) {
                    LOG.error("Stream was not started", cause);
                }
            });
        await().atMost(Duration.ofSeconds(2)).until(eventListener::started);
        assertNotNull(streamControl);
        return eventListener;
    }
}
