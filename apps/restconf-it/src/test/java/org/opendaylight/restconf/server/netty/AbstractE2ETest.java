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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.sal.remote.impl.CreateDataChangeEventSubscriptionRpc;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

abstract class AbstractE2ETest extends AbstractDataBrokerTest {
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();
    private static final String JSON_ERROR_FORMAT = """
        {
           "errors": {
             "error": [
               {
                 "error-type": "%s",
                 "error-tag": "%s"
               }
             ]
           }
         }""";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";

    protected static final String APPLICATION_JSON = "application/json";
    protected static final String APPLICATION_XML = "application/xml";

    protected static BootstrapFactory bootstrapFactory;
    protected HttpClientStackGrouping clientStackGrouping;

    private NettyEndpoint endpoint;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // transport configuration
        final var localAddress = InetAddress.getLoopbackAddress().getHostAddress();
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
        final var rpcService = new RouterDOMRpcService(new DOMRpcRouter(schemaService));
        final var actionService = new RouterDOMActionService(new DOMRpcRouter(schemaService));
        final var mountPointService = new DOMMountPointServiceImpl();
        final var streamRegistry = new MdsalRestconfStreamRegistry(uri -> uri.resolve("streams"), domDataBroker);
        final var rpcImplementations = List.<RpcImplementation>of(
            // rpcImplementations
            new CreateDataChangeEventSubscriptionRpc(streamRegistry, dataBindProvider, domDataBroker)
        );
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, rpcService, actionService,
            mountPointService, rpcImplementations);

        // Netty endpoint
        final var serverBaseUri = URI.create("http://" + localAddress + ":" + port + "/rests");
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            serverBaseUri, "restconf-netty-e2e", 8,
            NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping);
        endpoint = new NettyEndpoint(server, principalService, streamRegistry, configuration);
    }

    private static int freePort() {
        // find free port
        try {
            final var socket = new ServerSocket(0);
            final var localPort = socket.getLocalPort();
            socket.close();
            return localPort;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @AfterEach
    void afterEach() {
        endpoint.deactivate();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
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
        await().atMost(Duration.ofSeconds(2)).until(() -> channelListener.initialized);
        final var callback = new RequestCallback();
        client.invoke(request, callback);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(() -> callback.completed);
        client.shutdown().get(2, TimeUnit.SECONDS);
        assertNotNull(callback.response);
        return callback.response;
    }

    private static FullHttpRequest buildRequest(final HttpMethod method, final String uri, final String mediaType,
            final String content) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        request.headers().set(HttpHeaderNames.ACCEPT, mediaType);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
        if (method != HttpMethod.GET) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        return request;
    }

    private static final class RequestCallback implements FutureCallback<FullHttpResponse> {
        private volatile boolean completed;

        private volatile FullHttpResponse response;

        @Override
        public void onSuccess(final FullHttpResponse result) {
            // detach response object from channel, so message content is not lost after client is disconnected
            final var content = Unpooled.wrappedBuffer(ByteBufUtil.getBytes(result.content()));
            final var copy = new DefaultFullHttpResponse(result.protocolVersion(), result.status(), content);
            copy.headers().set(result.headers());
            this.response = copy;
            this.completed = true;
        }

        @Override
        public void onFailure(final @NonNull Throwable throwable) {
            this.completed = true;
            throw new IllegalStateException(throwable);
        }
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

    protected static void assertErrorResponseJson(final FullHttpResponse response, final ErrorType errorType,
            final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        final String errorJson = JSON_ERROR_FORMAT.formatted(errorType.elementBody(), expectedErrorTag.elementBody());
        assertContentJson(response, errorJson);
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

    static final class TestTransportListener implements TransportChannelListener {
        private final Consumer<Channel> initializer;

        private volatile boolean initialized;

        TestTransportListener() {
            this.initializer = ignored -> { };
        }

        TestTransportListener(final Consumer<Channel> initializer) {
            this.initializer = initializer;
        }

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            initializer.accept(channel.channel());
            initialized = true;
        }

        @Override
        public void onTransportChannelFailed(final @NonNull Throwable cause) {
            throw new IllegalStateException("HTTP connection failure", cause);
        }
    }
}
