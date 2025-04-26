/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.subscription;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.server.TestEventStreamListener;
import org.opendaylight.restconf.it.server.TestRequestCallback;
import org.opendaylight.restconf.it.server.TestTransportChannelListener;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.SubscriptionResourceProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.subscription.DeleteSubscriptionRpc;
import org.opendaylight.restconf.subscription.EstablishSubscriptionRpc;
import org.opendaylight.restconf.subscription.KillSubscriptionRpc;
import org.opendaylight.restconf.subscription.ModifySubscriptionRpc;
import org.opendaylight.restconf.subscription.impl.IetfSubscriptionFeatureProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.runtime.api.DefaultBindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.spi.ModuleInfoSnapshotBuilder;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractNotificationSubscriptionTest extends AbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationSubscriptionTest.class);
    private static final YangParserFactory PARSER_FACTORY = ServiceLoader.load(YangParserFactory.class)
        .findFirst().orElseThrow(() -> new ExceptionInInitializerError("No YangParserFactory found"));
    private static final BindingRuntimeGenerator GENERATOR = ServiceLoader.load(BindingRuntimeGenerator.class)
        .findFirst().orElseThrow(() -> new ExceptionInInitializerError("No BindingRuntimeGenerator found"));
    private static final LoadingCache<Set<YangModuleInfo>, BindingRuntimeContext> RUNTIME_CONTEXT_CACHE = CacheBuilder
        .newBuilder().weakValues().build(new CacheLoader<>() {
            public BindingRuntimeContext load(final Set<YangModuleInfo> key) throws Exception {
                final var snapshot = new ModuleInfoSnapshotBuilder(PARSER_FACTORY)
                    .add(key)
                    .addModuleFeatures(IetfSubscribedNotificationsData.class,
                        new IetfSubscriptionFeatureProvider().supportedFeatures())
                    .build();
                return new DefaultBindingRuntimeContext(GENERATOR.generateTypeMapping(snapshot.modelContext()),
                    snapshot);
            }
        });
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final String RESTCONF = "restconf";

    static final String MODIFY_SUBSCRIPTION_URI =
        "/restconf/operations/ietf-subscribed-notifications:modify-subscription";
    static final String ESTABLISH_SUBSCRIPTION_URI =
        "/restconf/operations/ietf-subscribed-notifications:establish-subscription";

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private HttpClientStackGrouping clientStackGrouping;
    private String host;
    private SimpleNettyEndpoint endpoint;
    private EventStreamService clientStreamService;
    private HTTPClient subscriptionStreamClient;
    private EventStreamService.StreamControl streamControl;
    private DOMNotificationRouter domNotificationRouter;
    private DOMRpcRouter domRpcRouter;
    private MdsalRestconfStreamRegistry streamRegistry;
    private Registration reg;

    @Override
    protected BindingRuntimeContext getRuntimeContext() {
        return RUNTIME_CONTEXT_CACHE.getUnchecked(getModuleInfos());
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
        domRpcRouter = new DOMRpcRouter(schemaService);

        final var domMountPointService = new DOMMountPointServiceImpl();

        // setup notifications service
        domNotificationRouter = new DOMNotificationRouter(32);
        final var subscriptionStateService =
            new SubscriptionStateService(domNotificationRouter.notificationPublishService());
        streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker, domNotificationRouter.notificationService(),
            schemaService, uri -> uri.resolve("streams"));

        final var rpcImplementations = List.of(
            // register subscribed notifications RPCs to be tested
            new EstablishSubscriptionRpc(streamRegistry, subscriptionStateService),
            new ModifySubscriptionRpc(streamRegistry, subscriptionStateService),
            new DeleteSubscriptionRpc(streamRegistry, subscriptionStateService),
            new KillSubscriptionRpc(streamRegistry, subscriptionStateService));
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, domRpcRouter.rpcService(),
            domRpcRouter.actionService(), domMountPointService, rpcImplementations);

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(
            ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), RESTCONF,
            MessageEncoding.JSON, serverStackGrouping);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
            configuration);

        // Register subscription web resource
        reg = endpoint.registerWebResource(new SubscriptionResourceProvider(streamRegistry));
    }

    @AfterEach
    void afterEach() throws Exception {
        if (clientStreamService != null) {
            clientStreamService = null;
        }
        if (subscriptionStreamClient != null) {
            subscriptionStreamClient = null;
        }
        if (streamControl != null) {
            streamControl = null;
        }
        reg.close();
        endpoint.close();
        streamRegistry.close();
        domRpcRouter.close();
        domNotificationRouter.close();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        sshTransportStackFactory.close();
    }

    /**
     * Invoke test request without a body.
     *
     * <p>Invoke test request without a body. This is suitable for GET and DELETE requests.
     * @param method {@link HttpMethod} to be used
     * @param uri location where the request is invoked
     * @param acceptType type of the response we expect
     * @return {@link FullHttpResponse} of the request invocation
     * @throws Exception in case of failure during request
     */
    FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String acceptType)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, null, null, acceptType));
    }

    /**
     * Invoke test request with a body.
     *
     * <p>Invoke test request with a body. This is suitable for POST/PUT requests and RPC invocations.
     * @param method {@link HttpMethod} to be used
     * @param uri location where the request is invoked
     * @param contentType type of the content we send
     * @param content the body content itself
     * @param acceptType type of the response we expect
     * @return {@link FullHttpResponse} of the request invocation
     * @throws Exception in case of failure during request
     */
    FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String contentType,
            final String content, final String acceptType) throws Exception {
        return invokeRequest(buildRequest(method, uri, contentType, content, acceptType));
    }

    private FullHttpResponse invokeRequest(final FullHttpRequest request) throws Exception {
        final var channelListener = new TestTransportChannelListener(ignored -> {
            // no-op
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
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

    FullHttpResponse invokeTwoRequests(final FullHttpRequest request1, final FullHttpRequest request2)
            throws Exception {
        final var channelListener = new TestTransportChannelListener(ignored -> {
            // no-op
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(channelListener::initialized);
        final var callback = new TestRequestCallback();
        client.invoke(request1, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        final var response1 = callback.response();
        assertNotNull(response1);
        assertEquals(HttpResponseStatus.OK, response1.status());

        client.invoke(request2, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        final var response2 = callback.response();
        assertNotNull(response2);
        client.shutdown().get(2, TimeUnit.SECONDS);
        return response2;
    }

    FullHttpRequest buildRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content, final String acceptType) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        request.headers()
            .set(HttpHeaderNames.HOST, host)
            .set(HttpHeaderNames.ACCEPT, acceptType)
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

    FullHttpResponse invokeRequestKeepClient(final HTTPClient streamHttpClient, final HttpMethod method,
            final String uri, final String contentType, final String content, final String acceptType) {
        final var callback = new TestRequestCallback();
        streamHttpClient.invoke(buildRequest(method, uri, contentType, content, acceptType), callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        final var response = callback.response();
        assertNotNull(response);
        return response;
    }

    HTTPClient startStreamClient() throws Exception {
        final var transportListener = new TestTransportChannelListener(channel ->
            clientStreamService = SseUtils.enableClientSse(channel));
        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(transportListener::initialized);
        assertNotNull(clientStreamService);
        return streamClient;
    }

    TestEventStreamListener startSubscriptionStream(final String subscriptionId) throws Exception {
        subscriptionStreamClient = startStreamClient();
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

    final DOMNotificationPublishService publishService() {
        return domNotificationRouter.notificationPublishService();
    }

    /**
     * Utility method to establish a subscription.
     */
    FullHttpResponse establishFilteredSubscription(final String filter, final HTTPClient streamHttpClient) {
        final var input = String.format("""
             <establish-subscription xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications">
               <stream>"NETCONF"</stream>
               <encoding>encode-json</encoding>
               <stream-subtree-filter>%s</stream-subtree-filter>
             </establish-subscription>
             """, filter);

        return invokeRequestKeepClient(streamHttpClient, HttpMethod.POST, ESTABLISH_SUBSCRIPTION_URI,
            MediaTypes.APPLICATION_YANG_DATA_XML, input, MediaTypes.APPLICATION_YANG_DATA_JSON);
    }

    /**
     * Utility method to extract subscription ID from response.
     */
    static long extractSubscriptionId(final FullHttpResponse response) {
        final var jsonContent = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        return jsonContent.getJSONObject("ietf-subscribed-notifications:output").getLong("id");
    }
}
