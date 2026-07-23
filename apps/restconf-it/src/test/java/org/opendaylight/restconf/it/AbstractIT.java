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
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerOverQuic;
import org.opendaylight.netconf.transport.http.HTTPServerOverTcp;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.http.HttpServerStackConfiguration;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.client.ClientSession;
import org.opendaylight.restconf.client.impl.ClientHttp1Session;
import org.opendaylight.restconf.client.impl.ClientHttp2Session;
import org.opendaylight.restconf.client.impl.ClientHttp3Session;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.Tls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.HttpServerListenStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.client.rev260717.http3.client.grouping.quic.under.http.Quic;
import org.opendaylight.yangtools.binding.data.codec.dynamic.dagger.BindingDataCodecFactoryModule;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
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
    private static final Uint64 QUIC_INITIAL_MAX_DATA = Uint64.valueOf(4L * 1024 * 1024);
    private static final Uint64 QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE = Uint64.valueOf(256L * 1024);
    private static final Uint32 QUIC_INITIAL_MAX_STREAMS_BIDI = Uint32.valueOf(100);

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;
    private static PrivateKey quicPrivateKey;
    private static X509Certificate quicCertificate;

    private DOMMountPointService domMountPointService;
    private RpcProviderService rpcProviderService;
    private String host;
    private HttpClientStackGrouping clientStackGrouping;
    private HttpClientStackGrouping quicClientStackGrouping;
    private DOMRpcRouter domRpcRouter;
    private SimpleNettyEndpoint endpoint;
    private DOMNotificationRouter domNotificationRouter;
    private MdsalRestconfStreamRegistry streamRegistry;
    private ConstantAdapterContext adapterContext;

    private int port;

    private volatile EventStreamService clientStreamService;

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
    static void beforeAll() throws Exception {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-e2e", 8);

        // Self-signed certificate for the HTTP/3 (QUIC) listener, shared across all tests in the class.
        final var quicKeyGen = KeyPairGenerator.getInstance("RSA");
        quicKeyGen.initialize(2048);
        final var quicKeyPair = quicKeyGen.generateKeyPair();
        quicPrivateKey = quicKeyPair.getPrivate();
        final var quicX500Name = new X500Name("CN=TestCertificate");
        final var quicNow = Instant.now();
        final var quicCertBuilder = new JcaX509v3CertificateBuilder(quicX500Name,
            BigInteger.valueOf(quicNow.toEpochMilli()), Date.from(quicNow), Date.from(quicNow.plus(Duration.ofDays(1))),
            quicX500Name, quicKeyPair.getPublic());
        quicCertificate = new JcaX509CertificateConverter().getCertificate(
            quicCertBuilder.build(new JcaContentSignerBuilder("SHA256withRSA").build(quicPrivateKey)));
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

        quicClientStackGrouping = quicClientStackGrouping(USERNAME, PASSWORD);

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
        adapterContext = new ConstantAdapterContext(
            BindingDataCodecFactoryModule.provideBindingDOMCodecFactory().createBindingDOMCodec(getRuntimeContext()));

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
        clientStreamService = null;
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
        return invokeRequest(buildRequest(method, uri, mediaType, null, content));
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType, final @Nullable String acceptType, final @Nullable String content)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, acceptType, content));
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull ProtocolVersion version) throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null, null), version);
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull ProtocolVersion version, final @NonNull String mediaType) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null, null), version);
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull ProtocolVersion version, final @NonNull String mediaType, final @Nullable String content)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null, content), version);
    }

    protected FullHttpResponse invokeRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull ProtocolVersion version, final @NonNull String mediaType,
            final @Nullable String acceptType, final @Nullable String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, acceptType, content), version);
    }

    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request) throws Exception {
        return invokeRequest(request, clientStackGrouping, false);
    }

    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request,
            final @NonNull HttpClientStackGrouping clientConf) throws Exception {
        return invokeRequest(request, clientConf, false);
    }

    /**
     * Invokes a request using the given {@link ProtocolVersion}, resolving the matching client transport
     * configuration ({@link #clientStackGrouping()} for HTTP/1.1 and HTTP/2, {@link #quicClientStackGrouping()}
     * for HTTP/3) and {@code http2} flag.
     *
     * @param request the request to send
     * @param version the HTTP protocol version to use
     * @return the received response
     */
    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request,
            final @NonNull ProtocolVersion version) throws Exception {
        return invokeRequest(request,
            version == ProtocolVersion.HTTP_3 ? quicClientStackGrouping : clientStackGrouping,
            version == ProtocolVersion.HTTP_2);
    }

    /**
     * Invokes a request against the given client transport configuration, using HTTP/1.1, HTTP/2 or HTTP/3
     * depending on the configured transport: a {@code quic} transport always uses HTTP/3 ({@code http2} is then
     * ignored), while a {@code tcp}/{@code tls} transport uses HTTP/2 or HTTP/1.1 per {@code http2}.
     *
     * @param request the request to send
     * @param clientConf the client transport configuration
     * @param http2 whether to use HTTP/2 over the tcp/tls transport; ignored for the quic transport
     * @return the received response
     */
    protected FullHttpResponse invokeRequest(final @NonNull FullHttpRequest request,
            final @NonNull HttpClientStackGrouping clientConf, final boolean http2) throws Exception {
        final var clientSession = newClientSession(clientConf, http2);
        final var channelListener = new TestTransportChannelListener(transportChannel -> {
            transportChannel.channel().pipeline().addLast("restconf-session", clientSession);
        });
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientConf, http2).get(5, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(5)).until(channelListener::initialized);
        final var callback = new TestRequestCallback();
        clientSession.invoke(request, callback);
        // await for response
        await().atMost(Duration.ofSeconds(5)).until(callback::completed);
        client.shutdown().get(5, TimeUnit.SECONDS);
        final var response = callback.response();
        assertNotNull(response);
        return response;
    }

    private static ClientSession newClientSession(final HttpClientStackGrouping clientConf, final boolean http2) {
        final var transport = clientConf.getTransport();
        if (transport instanceof Quic) {
            return new ClientHttp3Session();
        }
        if (http2) {
            return new ClientHttp2Session(transport instanceof Tls ? HTTPScheme.HTTPS : HTTPScheme.HTTP);
        }
        return new ClientHttp1Session();
    }

    /**
     * Constructs a {@link FullHttpRequest} with the specified parameters and safely populated HTTP headers.
     *
     * @param method the HTTP method
     * @param uri the target request URI
     * @param mediaType the expected media type, used as a fallback for the Accept header and for Content-Type
     * @param acceptType the explicit Accept header value override
     * @param content the optional body payload content
     * @return a fully constructed HTTP request
     */
    protected @NonNull FullHttpRequest buildRequest(final @NonNull HttpMethod method, final @NonNull String uri,
            final @NonNull String mediaType, final @Nullable String acceptType, final @Nullable String content) {
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

    protected void assertContentJson(final String getRequestUri, final String expectedContent,
            final @NonNull ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, getRequestUri, version);
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

    protected void assertContentXml(final String getRequestUri, final String expectedContent,
            final @NonNull ProtocolVersion version) throws Exception {
        final var response = invokeRequest(HttpMethod.GET, getRequestUri, version, APPLICATION_XML);
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
            ALT_SVC_HEADER, HTTP3_ALT_SVC_MAX_AGE_SECONDS,
            new HttpServerStackConfiguration(HTTPServerOverQuic.of(localAddress, port, quicCertificate,
                quicPrivateKey, QUIC_INITIAL_MAX_DATA, QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE,
                QUIC_INITIAL_MAX_STREAMS_BIDI)));
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
     * {@return the QUIC (HTTP/3) client transport configuration}, targeting the QUIC listener that
     * {@link #createEndpointConfiguration(HttpServerListenStackGrouping)} bootstraps alongside the plain TCP one
     */
    protected final HttpClientStackGrouping quicClientStackGrouping() {
        return quicClientStackGrouping;
    }

    /**
     * Builds a QUIC (HTTP/3) client transport configuration for the given credentials, targeting the same QUIC
     * listener as {@link #quicClientStackGrouping()}. Useful for tests that need a client configured with, e.g.,
     * intentionally wrong credentials (mirroring {@code invalidClientStackGrouping} for the tcp/tls transports).
     *
     * @param username username
     * @param password password
     * @return a QUIC client transport configuration
     */
    protected final HttpClientStackGrouping quicClientStackGrouping(final String username, final String password) {
        return new HttpClientStackConfiguration(ConfigUtils.clientTransportQuic(localAddress, port, quicCertificate,
            QUIC_INITIAL_MAX_DATA, QUIC_INITIAL_MAX_STREAM_DATA_BIDI_REMOTE, QUIC_INITIAL_MAX_STREAMS_BIDI,
            username, password));
    }

    /**
     * {@return the {@link EventStreamService} enabled on the client connection established by the most recent
     * {@link #startStreamClient(ProtocolVersion)} call}
     */
    protected final EventStreamService clientStreamService() {
        return clientStreamService;
    }

    /**
     * Establishes a client connection with SSE enabled, using the given {@link ProtocolVersion}.
     *
     * @param version the HTTP protocol version to use
     * @return the connected {@link HTTPClient}
     */
    protected HTTPClient startStreamClient(final ProtocolVersion version) throws Exception {
        final var transportListener = new TestTransportChannelListener(channel -> {
            final ChannelHandler session = switch (version) {
                case HTTP_1_1 -> new ClientHttp1Session();
                case HTTP_2 -> new ClientHttp2Session(HTTPScheme.HTTP);
                case HTTP_3 -> new ClientHttp3Session();
            };
            channel.channel().pipeline().addLast("restconf-session", session);
            clientStreamService = SseUtils.enableClientSse(channel);
        });
        final var clientConf = version == ProtocolVersion.HTTP_3
            ? quicClientStackGrouping(USERNAME, PASSWORD) : clientStackGrouping();
        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory().newBootstrap(),
            clientConf, version == ProtocolVersion.HTTP_2).get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(5)).until(transportListener::initialized);
        assertNotNull(clientStreamService);
        return streamClient;
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
