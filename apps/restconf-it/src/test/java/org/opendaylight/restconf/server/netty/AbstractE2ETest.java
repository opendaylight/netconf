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
import static org.junit.jupiter.api.Assertions.fail;
import static org.opendaylight.restconf.server.netty.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.netty.TestUtils.freePort;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

import com.google.common.base.Splitter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.InetAddress;
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
import org.eclipse.jdt.annotation.NonNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
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
import org.opendaylight.netconf.transport.http.SseUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.netty.TestUtils.TestEventListener;
import org.opendaylight.restconf.server.netty.TestUtils.TestRequestCallback;
import org.opendaylight.restconf.server.netty.TestUtils.TestTransportListener;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

abstract class AbstractE2ETest extends AbstractDataBrokerTest {
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
    protected DOMMountPointService domMountPointService;
    protected RpcProviderService rpcProviderService;

    protected volatile EventStreamService clientStreamService;
    protected volatile EventStreamService.StreamControl streamControl;

    private NettyEndpoint endpoint;

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-e2e", 8);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // transport configuration
        final var port = freePort();
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
        final var clientTransport = ConfigUtils.clientTransportTcp(localAddress, port, USERNAME, PASSWORD);
        clientStackGrouping = new HttpClientStackGrouping() {
            @Override
            public @NonNull Class<? extends DataContainer> implementedInterface() {
                return HttpClientStackGrouping.class;
            }

            @Override
            public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.Transport getTransport() {
                return clientTransport;
            }
        };

        // AAA services
        final var securityManager = new DefaultWebSecurityManager(new AuthenticatingRealm() {
            @Override
            protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
                throws AuthenticationException {
                final var principal = ((String) token.getPrincipal());
                final var credentials = new String(((char[]) token.getCredentials()));
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
        final var domActionService = new RouterDOMActionService(new DOMRpcRouter(schemaService));
        domMountPointService = new DOMMountPointServiceImpl();
        final var adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));
        rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext, domRpcRouter.rpcProviderService());
        final var streamRegistry = new MdsalRestconfStreamRegistry(uri -> uri.resolve("streams"), domDataBroker);
        final var rpcImplementations = List.<RpcImplementation>of(
            // rpcImplementations
            new CreateDataChangeEventSubscriptionRpc(streamRegistry, dataBindProvider, domDataBroker),
            new SubscribeDeviceNotificationRpc(streamRegistry, domMountPointService)
        );
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, domRpcService, domActionService,
            domMountPointService, rpcImplementations);

        // Netty endpoint
        final var serverBaseUri = URI.create("http://" + localAddress + ":" + port + "/rests");
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            serverBaseUri, "restconf-netty-e2e", 8,
            NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping);
        endpoint = new NettyEndpoint(server, principalService, streamRegistry, configuration);
    }

    @AfterEach
    void afterEach() {
        clientStreamService = null;
        streamControl = null;
        endpoint.deactivate();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        sshTransportStackFactory.close();
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri) throws Exception {
        return invokeRequest(buildRequest(method, uri, APPLICATION_JSON, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType)
            throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, null));
    }

    protected FullHttpResponse invokeRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) throws Exception {
        return invokeRequest(buildRequest(method, uri, mediaType, content));
    }

    private FullHttpResponse invokeRequest(final FullHttpRequest request) throws Exception {
        final var channelListener = new TestTransportListener();
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
        final var transportListener = new TestTransportListener(channel -> {
            clientStreamService = SseUtils.enableClientSse(channel);
        });
        final var streamClient = HTTPClient.connect(transportListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, false).get(2, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(2)).until(transportListener::initialized);
        assertNotNull(clientStreamService);
        return streamClient;
    }

    protected TestEventListener startStream(final String uri) {
        final var eventListener = new TestEventListener();
        clientStreamService.startEventStream(uri, eventListener,
            new EventStreamService.StartCallback() {
                @Override
                public void onStreamStarted(final EventStreamService.StreamControl control) {
                    streamControl = control;
                }

                @Override
                public void onStartFailure(final Exception cause) {
                    fail("Stream was not started", cause);
                }
            });
        await().atMost(Duration.ofSeconds(2)).until(eventListener::started);
        assertNotNull(streamControl);
        return eventListener;
    }
}