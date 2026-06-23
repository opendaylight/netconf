/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import com.google.common.base.Splitter;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONParserConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcProviderService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HTTPServerOverTcp;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.client.impl.ClientHttp1Session;
import org.opendaylight.restconf.it.server.TestRequestCallback;
import org.opendaylight.restconf.it.server.TestTransportChannelListener;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.HttpServerListenStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.spi.source.YangTextToIRSourceTransformer;
import org.opendaylight.yangtools.yang.source.ir.dagger.YangIRSourceModule;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

/**
 * Abstract base class for Restconf Integration Tests providing a shared test infrastructure.
 *
 * <p>This class centralizes the setup and teardown lifecycles for the Netty server endpoint,
 * AAA authentication, core MDSAL services, and common client methods.
 *
 * <ul>
 * <li><b>Initialization:</b> If a child class overrides {@link #beforeEach()}, it must call
 * {@code super.beforeEach()} as the first statement to start the core infrastructure.</li>
 * <li><b>Post-Setup Customization:</b> Any additional specific bindings, web resource
 * registrations, or client instantiations should be performed immediately after the
 * {@code super.beforeEach()} invocation.</li>
 * <li><b>RPC Registration:</b> To register specific operations (e.g., subscription or
 * test-specific RPCs), child classes can override
 * {@link #rpcImplementations(DOMDataBroker, MdsalDatabindProvider)}.</li>
 * <li><b>Teardown:</b> Child classes must call {@code super.afterEach()} if they override
 * the teardown hook to guarantee clean socket release and registry closure.</li>
 * </ul>
 */
public abstract class AbstractIT extends AbstractDataBrokerTest {
    private static final JSONParserConfiguration JSON_PARSER_CONFIGURATION =
        new JSONParserConfiguration().withStrictMode();
    private static final Uint32 CHUNK_SIZE = Uint32.valueOf(256 * 1024);
    private static final Uint32 FRAME_SIZE = Uint32.valueOf(16 * 1024);
    private static final String ALT_SVC_HEADER = "h3=\":8443\"; ma=3600";
    private static final Uint32 HTTP3_ALT_SVC_MAX_AGE_SECONDS = Uint32.valueOf(3600);
    private static final Uint32 WRITE_BUFFER_LOW_WATER_MARK = Uint32.valueOf(32 * 1024);
    private static final Uint32 WRITE_BUFFER_HIGH_WATER_MARK = Uint32.valueOf(64 * 1024);

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private DOMMountPointService domMountPointService;
    private RpcProviderService rpcProviderService;
    private String host;
    private HttpClientStackGrouping clientStackGrouping;
    private DOMRpcRouter domRpcRouter;
    private SimpleNettyEndpoint endpoint;
    private DOMNotificationRouter domNotificationRouter;
    private MdsalRestconfStreamRegistry streamRegistry;
    private ConstantAdapterContext adapterContext;

    private int port;

    protected static final @NonNull YangTextToIRSourceTransformer TEXT_TO_IR = YangIRSourceModule.provideTextToIR();
    protected static final Map<String, String> NS_CONTEXT = Map.of("r", "urn:ietf:params:xml:ns:yang:ietf-restconf");
    protected static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    protected static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    protected static final String RESTS = "rests";
    protected static final String USERNAME = "username";
    protected static final String PASSWORD = "pa$$w0Rd";
    protected static final String APPLICATION_JSON = "application/json";
    protected static final String APPLICATION_XML = "application/xml";

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-e2e", 8);
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        // transport configuration
        port = randomBindablePort();
        host = localAddress + ":" + port;
        final var serverTransport = HTTPServerOverTcp.of(localAddress, port);
        final var serverStackGrouping = new HttpServerListenStackGrouping() {
            @Override
            public Class<HttpServerListenStackGrouping> implementedInterface() {
                return HttpServerListenStackGrouping.class;
            }

            @Override
            public HttpOverTcp getTransport() {
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
        domMountPointService = new DOMMountPointServiceImpl();
        adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));

        rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext,
            new RouterDOMRpcProviderService(domRpcRouter));
        domNotificationRouter = new DOMNotificationRouter(32);
        final ClusterSingletonServiceProvider cssProvider = service -> {
            service.instantiateServiceInstance();
            return service::closeServiceInstance;
        };

        streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker,
            new RouterDOMNotificationService(domNotificationRouter),
            schemaService, uri -> uri.resolve("streams"), dataBindProvider, cssProvider);

        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker,
            new RouterDOMRpcService(domRpcRouter), new RouterDOMActionService(domRpcRouter), domMountPointService,
            rpcImplementations(domDataBroker, dataBindProvider));

        // Netty endpoint
        final var configuration = createEndpointConfiguration(serverStackGrouping);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory, configuration);
    }

    @AfterEach
    protected void afterEach() throws Exception {
        endpoint.close();
        streamRegistry.close();
        domNotificationRouter.close();
        domRpcRouter.close();
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
    protected static int randomBindablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null, null));
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null, null));
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType, final @Nullable String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, null));
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType, final @Nullable String acceptType, final @Nullable String content)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, acceptType));
    }

    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request) throws Exception {
        return invokeRequest(request, clientStackGrouping);
    }

    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request,
            final @NonNull HttpClientStackGrouping clientConf) throws Exception {
        final var clientSession = new ClientHttp1Session();
        final var channelListener = new TestTransportChannelListener(transportChannel -> {
            transportChannel.channel().pipeline().addLast("restconf-session", clientSession);
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientConf, false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(channelListener::initialized);
        final var callback = new TestRequestCallback();
        clientSession.invoke(request, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback::completed);
        client.shutdown().get(2, TimeUnit.SECONDS);
        final var response = callback.response();
        assertNotNull(response);
        return response;
    }

    /**
     * Constructs a {@link FullHttpRequest} with the specified parameters and safely populated HTTP headers.
     *
     * @param method the HTTP method
     * @param uri the target request URI
     * @param mediaType the expected media type, used as a fallback for the Accept header and for Content-Type
     * @param content the optional body payload content
     * @param acceptType the explicit Accept header value override
     * @return a fully constructed HTTP request
     */
    protected @NonNull FullHttpRequest buildRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType, final @Nullable String content, final @Nullable String acceptType) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        request.headers()
            .set(HttpHeaderNames.HOST, host)
            .set(HttpHeaderNames.ACCEPT, acceptType != null ? acceptType : mediaType)
            .set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        return request;
    }

    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, getRequestUri);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertContentJson(response, expectedContent);
    }

    protected static void assertContentJson(final FullHttpResponse response, final String expectedContent) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    protected void assertContentXml(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, getRequestUri, APPLICATION_XML);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertContentXml(response, expectedContent);
    }

    protected static void assertContentXml(final FullHttpResponse response, final String expectedContent) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertThat(content, isSimilarTo(expectedContent).ignoreComments().ignoreWhitespace()
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName)));
    }

    protected NettyEndpointConfiguration createEndpointConfiguration(
            final HttpServerListenStackGrouping serverStackGrouping) {
        return new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), RESTS, MessageEncoding.JSON,
            serverStackGrouping, CHUNK_SIZE, FRAME_SIZE, WRITE_BUFFER_LOW_WATER_MARK, WRITE_BUFFER_HIGH_WATER_MARK,
            ALT_SVC_HEADER, HTTP3_ALT_SVC_MAX_AGE_SECONDS);
    }

    protected List<RpcImplementation> rpcImplementations(final DOMDataBroker domDataBroker,
            final MdsalDatabindProvider dataBindProvider) {
        return List.of();
    }

    /**
     * {@return the JSON parser configuration}
     */
    protected static JSONParserConfiguration jsonParserConfiguration() {
        return JSON_PARSER_CONFIGURATION;
    }

    /**
     * {@return the localAddress}
     */
    protected static String localAddress() {
        return localAddress;
    }

    /**
     * {@return the bootstrapFactory}
     */
    protected static BootstrapFactory bootstrapFactory() {
        return bootstrapFactory;
    }

    /**
     * {@return the sshTransportStackFactory}
     */
    protected static SSHTransportStackFactory sshTransportStackFactory() {
        return sshTransportStackFactory;
    }

    /**
     * {@return the domMountPointService}
     */
    protected final DOMMountPointService domMountPointService() {
        return domMountPointService;
    }

    /**
     * {@return the rpcProviderService}
     */
    protected final RpcProviderService rpcProviderService() {
        return rpcProviderService;
    }

    /**
     * {@return the host}
     */
    protected final String host() {
        return host;
    }

    /**
     * {@return the clientStackGrouping}
     */
    protected final HttpClientStackGrouping clientStackGrouping() {
        return clientStackGrouping;
    }

    /**
     * {@return the domRpcRouter}
     */
    protected final DOMRpcRouter domRpcRouter() {
        return domRpcRouter;
    }

    /**
     * {@return the endpoint}
     */
    protected final SimpleNettyEndpoint endpoint() {
        return endpoint;
    }

    /**
     * {@return the domNotificationRouter}
     */
    protected final DOMNotificationRouter domNotificationRouter() {
        return domNotificationRouter;
    }

    /**
     * {@return the streamRegistry}
     */
    protected final MdsalRestconfStreamRegistry streamRegistry() {
        return streamRegistry;
    }

    /**
     * {@return the adapterContext}
     */
    protected final ConstantAdapterContext adapterContext() {
        return adapterContext;
    }

    /**
     * {@return the port}
     */
    protected final int port() {
        return port;
    }
}
