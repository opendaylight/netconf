/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static java.util.stream.Collectors.toSet;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.opendaylight.mdsal.binding.api.ActionProviderService;
import org.opendaylight.mdsal.binding.api.ActionSpec;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.dom.adapter.BindingAdapterFactory;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.odl.device.notification.SubscribeDeviceNotificationRpc;
import org.opendaylight.netconf.sal.remote.impl.CreateDataChangeEventSubscriptionRpc;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.example.action.rev240919.Root;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleAction;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionInput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutput;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleActionOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

abstract class AbstractE2ETest extends AbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractE2ETest.class);
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final Map<String, String> NS_CONTEXT = Map.of("r", "urn:ietf:params:xml:ns:yang:ietf-restconf");

    protected static final String APPLICATION_JSON = "application/json";
    protected static final String APPLICATION_XML = "application/xml";

    protected static String localAddress;
    protected static BootstrapFactory bootstrapFactory;
    protected static SSHTransportStackFactory sshTransportStackFactory;
    protected HttpClientStackGrouping clientStackGrouping;
    protected HttpClientStackGrouping invalidClientStackGrouping;
    protected DOMMountPointService domMountPointService;
    protected RpcProviderService rpcProviderService;
    protected ActionProviderService actionProviderService;

    protected volatile EventStreamService clientStreamService;
    protected volatile EventStreamService.StreamControl streamControl;

    private SimpleNettyEndpoint endpoint;
    private String host;

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-e2e", 8);
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
        invalidClientStackGrouping = new HttpClientStackConfiguration(
            ConfigUtils.clientTransportTcp(localAddress, port, USERNAME, "wrong-password"));

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
        domMountPointService = new DOMMountPointServiceImpl();
        final var adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));
        final var adapterFactory = new BindingAdapterFactory(adapterContext);
        actionProviderService = adapterFactory.createActionProviderService(domRpcRouter.actionProviderService());
        // action implementations
        actionProviderService.registerImplementation(
            ActionSpec.builder(Root.class).build(ExampleAction.class), new ExampleActionImpl());
        rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext, domRpcRouter.rpcProviderService());
        final var streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker, null, schemaService,
            uri -> uri.resolve("streams"));
        final var rpcImplementations = List.<RpcImplementation>of(
            // rpcImplementations
            new CreateDataChangeEventSubscriptionRpc(streamRegistry, dataBindProvider, domDataBroker),
            new SubscribeDeviceNotificationRpc(streamRegistry, domMountPointService)
        );
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, domRpcRouter.rpcService(),
            domRpcRouter.actionService(), domMountPointService, rpcImplementations);

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            "rests", MessageEncoding.JSON, serverStackGrouping);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory, configuration);
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

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri) throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
        final String acceptType, final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content, acceptType));
    }

    protected FullHttpResponse invokeRequest(final FullHttpRequest request) throws Exception {
        return invokeRequest(request, clientStackGrouping);
    }

    protected FullHttpResponse invokeRequest(final FullHttpRequest request, final HttpClientStackGrouping clientConf)
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

    protected FullHttpRequest buildRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content, final String acceptType) {
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

    protected static void assertErrorResponseJson(final FullHttpResponse response, final ErrorType expectedErrorType,
            final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        // {
        //    errors": {
        //       "error": [{
        //             "error-type": "...",
        //             "error-tag": "..."
        //             "error-message": "..."
        //         }]
        //    }
        // }
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }

    protected static void assertErrorResponseXml(final FullHttpResponse response, final ErrorType expectedErrorType,
            final ErrorTag expectedErrorTag) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        assertThat(content, hasXPath("/r:errors/r:error/r:error-message",
            not(emptyOrNullString())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-type",
            equalTo(expectedErrorType.elementBody())).withNamespaceContext(NS_CONTEXT));
        assertThat(content, hasXPath("/r:errors/r:error/r:error-tag",
            equalTo(expectedErrorTag.elementBody())).withNamespaceContext(NS_CONTEXT));
    }

    // FIXME: Must be eliminated, usage must be replaced with assertErrorResponseJson once
    //  DataE2ETest#invokeActionWithBadInputsTest will return properly formatted response.
    protected static void assertSimpleErrorResponse(final FullHttpResponse response, final String expectedMessage,
        final HttpResponseStatus expectedStatus) {
        final var content = response.content().toString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, response.status());
        assertEquals(expectedMessage, content);
    }

    protected void assertOptions(final String uri, final Set<String> methods) throws Exception {
        assertOptionsResponse(invokeRequest(HttpMethod.OPTIONS, uri), methods);
    }

    protected static void assertOptionsResponse(final FullHttpResponse response, final Set<String> methods) {
        assertEquals(HttpResponseStatus.OK, response.status());
        assertHeaderValue(response, HttpHeaderNames.ALLOW, methods);
    }

    protected static void assertHeaderValue(final FullHttpResponse response, final CharSequence headerName,
            final Set<String> expectedValues) {
        final var headerValue = response.headers().get(headerName);
        assertNotNull(headerValue);
        assertEquals(expectedValues, COMMA_SPLITTER.splitToStream(headerValue).collect(toSet()));
    }

    protected void assertHead(final String uri) throws Exception {
        assertHead(uri, APPLICATION_JSON);
        assertHead(uri, APPLICATION_XML);
    }

    protected void assertHead(final String uri, final String mediaType) throws Exception {
        final var getResponse = invokeRequest(HttpMethod.GET, uri, mediaType);
        assertEquals(HttpResponseStatus.OK, getResponse.status());
        assertTrue(getResponse.content().readableBytes() > 0);

        // HEAD response contains same headers as GET but empty body
        final var headResponse = invokeRequest(HttpMethod.HEAD, uri, mediaType);
        assertEquals(HttpResponseStatus.OK, headResponse.status());
        getResponse.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        headResponse.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        assertEquals(getResponse.headers(), headResponse.headers());
        assertEquals(0, headResponse.content().readableBytes());
    }

    protected URI getStreamUrlJson(final String streamName) throws Exception {
        // get stream URL from restconf-state
        final var response = invokeRequest(HttpMethod.GET,
            "/rests/data/ietf-restconf-monitoring:restconf-state/streams/stream=" + streamName);
        assertEquals(HttpResponseStatus.OK, response.status());
        return extractStreamUrlJson(response.content().toString(StandardCharsets.UTF_8));
    }

    private static URI extractStreamUrlJson(final String content) {
        // {
        //      "ietf-restconf-monitoring:stream": [{
        //              "name": "urn:uuid:6413c077-5dfe-464c-b17f-20c5bbb456f4",
        //              "access": [
        //                  { "encoding": "json", "location": "..." },
        //                  { "encoding": "xml", "location": "..."}
        //              ],
        //              "description": "..."
        //      }]
        // }
        final var json = new JSONObject(content);
        final var stream = json.getJSONArray("ietf-restconf-monitoring:stream").getJSONObject(0);
        for (var access : stream.getJSONArray("access")) {
            final var accessObj = (JSONObject) access;
            if ("json".equals(accessObj.getString("encoding"))) {
                return URI.create(accessObj.getString("location"));
            }
        }
        return null;
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

    protected TestEventStreamListener startStream(final String uri) {
        final var eventListener = new TestEventStreamListener();
        clientStreamService.startEventStream("localhost", uri, eventListener,
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

    static final class ExampleActionImpl implements ExampleAction {
        @Override
        public ListenableFuture<RpcResult<ExampleActionOutput>> invoke(final DataObjectIdentifier<Root> path,
                final ExampleActionInput input) {
            return Futures.immediateFuture(RpcResultBuilder.success(
                new ExampleActionOutputBuilder().setResponse("Action was invoked").build()).build());
        }
    }
}
