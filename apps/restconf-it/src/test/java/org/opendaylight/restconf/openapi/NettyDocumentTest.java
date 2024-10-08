/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.TestUtils.freePort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.mdsal.DeviceActionFactoryImpl;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactoryImpl;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.openapi.TestUtils.TestClientStackGrouping;
import org.opendaylight.restconf.openapi.TestUtils.TestEncryptionService;
import org.opendaylight.restconf.openapi.TestUtils.TestRequestCallback;
import org.opendaylight.restconf.openapi.TestUtils.TestTransportListener;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.restconf.openapi.netty.OpenApiRequestDispatcher;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.PrincipalService;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@ExtendWith(MockitoExtension.class)
public class NettyDocumentTest extends AbstractDataBrokerTest {
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
    private static final String DEVICE_USERNAME = "device-username";
    private static final String DEVICE_PASSWORD = "device-password";
    private static final String BASE_PATH = "/openapi";
    private static final String API_V3_PATH = BASE_PATH + "/api/v3";
    private static final String BASE_URL = "http://127.0.0.1:8184" + BASE_PATH;
    private static final URI OPENAPI_BASE_URI = URI.create(BASE_URL);
    private static final URI RESTCONF_SERVER_URI = URI.create("http://127.0.0.1:8182");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
        .node(QName.create("", "nodes"))
        .node(QName.create("", "node"))
        .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REV = "2009-11-20";
    /**
     * Model toaster@2009-11-19 is used for test correct generating of openapi with models with same name and another
     * revision date. We want to test that the same model is not duplicated and loaded just the newest version.
     */
    private static final String TOASTER_OLD_REV = "2009-11-19";

    private static final YangModuleInfo TOASTER_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091120.YangModuleInfoImpl.getInstance();
    private static final YangModuleInfo TOASTER_OLD_YANG_MODEL =
        org.opendaylight.yang.svc.v1.http.netconfcentral.org.ns.toaster.rev091119.YangModuleInfoImpl.getInstance();
    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private HttpClientStackGrouping clientStackGrouping;
    private OpenApiService openApiService;
    private OpenApiRequestDispatcher dispatcher;
    private PrincipalService principalService;
    private NettyEndpoint endpoint;
    private String host;
    @TempDir
    private File tmpDir;
    private NetconfTopologyImpl topologyService;
    private int devicePort;
    private NetconfDeviceSimulator deviceSimulator;

    @Mock
    private FutureCallback<FullHttpResponse> callback;
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

    // TODO most of this is duplicate from NETCONF-1367. Simplify upon merge.
    protected void initializeClass(final String yangPath) throws Exception {
        // transport configuration
        final var port = freePort();
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
        clientStackGrouping = new TestClientStackGrouping(
            ConfigUtils.clientTransportTcp(localAddress, port, USERNAME, PASSWORD));

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
        principalService = new AAAShiroPrincipalService(securityManager);

        setup();
        final var domDataBroker = getDomBroker();
        final var context = YangParserTestUtils.parseYangResourceDirectory(yangPath);
        final var schemaService = new FixedDOMSchemaService(context);
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
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000), URI.create("rests"),
            "restconf-netty-e2e", 8, NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping);
        endpoint = new NettyEndpoint(server, principalService, streamRegistry, configuration);


        final var dataBroker = getDataBroker();
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getIdentifier()).thenReturn(INSTANCE_ID);
        final var netconfTimer = new DefaultNetconfTimer();
        final var encryptionService = new TestEncryptionService();
        final var netconfClientConfBuilderFactory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService,
            id -> null, sslContextFactoryProvider());
        final var netconfClientFactory = new NetconfClientFactoryImpl(netconfTimer, sshTransportStackFactory);
        final var topologySchemaAssembler = new NetconfTopologySchemaAssembler(1, 4, 1L, TimeUnit.MINUTES);
        final var yangParserFactory = new DefaultYangParserFactory();
        final var schemaSourceMgr =
            new DefaultSchemaResourceManager(yangParserFactory, tmpDir.getAbsolutePath(), "schema");
        final var baseSchemaProvider = new DefaultBaseNetconfSchemaProvider(yangParserFactory);
        final var adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));
        final var rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext,
            domRpcRouter.rpcProviderService());

        topologyService = new NetconfTopologyImpl(netconfClientFactory, netconfTimer, topologySchemaAssembler,
            schemaSourceMgr, dataBroker, domMountPointService, encryptionService, netconfClientConfBuilderFactory,
            rpcProviderService, baseSchemaProvider, new DeviceActionFactoryImpl());

        // OpenApi
        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, domMountPointService,
            "rests");
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService, "rests");
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(mountPoint);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);

        // TODO swap usage of OpenApiRequestDispatcher to simple invoke of GET request for OpenApi resource
        dispatcher = new OpenApiRequestDispatcher(principalService, openApiService,
            OPENAPI_BASE_URI, RESTCONF_SERVER_URI);
    }

    @BeforeAll
    static void beforeAll() {
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
        bootstrapFactory = new BootstrapFactory("restconf-netty-e2e", 8);
        sshTransportStackFactory = new SSHTransportStackFactory("netconf-netty-e2e", 8);
    }

    @BeforeEach
    void beforeEach() throws Exception {
        initializeClass("/netty-documents/");
    }

    @AfterEach
    void afterEach() {
        if (deviceSimulator != null) {
            deviceSimulator.close();
            deviceSimulator = null;
        }
        if (topologyService != null) {
            topologyService.close();
            topologyService = null;
        }
        endpoint.deactivate();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
        sshTransportStackFactory.close();
    }

    @Test
    void controllerAllDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("netty-documents/controller-all.json");
        final var response = dispatch(API_V3_PATH + "/single");
        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    void getDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        final var expectedJson = getExpectedDoc("netty-documents/" + jsonPath);
        var uri = API_V3_PATH + "/" + TOASTER + "?revision=" + revision;
        final var response = dispatch(uri);
        // final var response = invokeRequest(HttpMethod.GET, uri);
        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "controller-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "controller-toaster-old.json")
        );
    }

    @Test
    @Disabled
    void getMountDocTest() throws Exception {
        startDeviceSimulator(true);
        mountDeviceJson();
        final var expectedJson = getExpectedDoc("netty-documents/device-all.json");
        final var response = dispatch(API_V3_PATH + "/mounts/1");
        // final var response = invokeRequest(HttpMethod.GET, uri);
        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Tests the swagger document that is result of the call to the '/mounts/1/toaster@revision' endpoint.
     */
    @ParameterizedTest
    @MethodSource
    @Disabled
    void getMountDocByModuleTest(final String revision, final String jsonPath) throws Exception {
        startDeviceSimulator(true);
        mountDeviceJson();
        final var expectedJson = getExpectedDoc("netty-documents/" + jsonPath);
        var uri = API_V3_PATH + "/mounts/1/" + TOASTER + "?revision=" + revision;
        final var response = invokeRequest(HttpMethod.GET, uri);
        final var resultDoc = response.content().toString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(expectedJson, resultDoc, JSONCompareMode.NON_EXTENSIBLE);
    }

    private static Stream<Arguments> getMountDocByModuleTest() {
        // moduleName, revision, jsonPath
        return Stream.of(
            Arguments.of(TOASTER_REV, "device-toaster.json"),
            Arguments.of(TOASTER_OLD_REV, "device-toaster-old.json")
        );
    }

    @Test
    @Disabled
    void getMountsTest() throws Exception {
        startDeviceSimulator(true);
        mountDeviceJson();
        assertContentJson(API_V3_PATH + "/mounts", """
            [
                {
                    "instance": "/network-topology:network-topology/topology=topology-netconf/node=17830-sim-device/",
                    "id": 1
                }
            ]""");
    }


    @Test
    @Disabled
    void uiAvailableTest() throws Exception {
        startDeviceSimulator(true);
        mountDeviceJson();
        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/ui", null, TEXT_HTML, null);
        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("""
                <!-- HTML for static distribution bundle build -->
                <!DOCTYPE html>
                <html lang="en">
                                
                <head>
                    <meta charset="UTF-8">
                    <title>RestConf Documentation</title>
                    <link rel="stylesheet" type="text/css" href="swagger-ui/swagger-ui.css" />
                    <link rel="stylesheet" type="text/css" href="swagger-ui/index.css" />
                    <link rel="stylesheet" type="text/css" href="styles.css" />
                    <link rel="icon" type="image/png" href="swagger-ui/favicon-32x32.png" sizes="32x32" />
                    <link rel="icon" type="image/png" href="swagger-ui/favicon-16x16.png" sizes="16x16" />
                </head>
                                
                <body>
                    <div id="swagger-ui"></div>
                    <script src="swagger-ui/swagger-ui-bundle.js" charset="UTF-8"> </script>
                    <script src="swagger-ui/swagger-ui-standalone-preset.js" charset="UTF-8"> </script>
                    <script src="./configuration.js"></script>
                    <script src="./swagger-initializer.js" charset="UTF-8"> </script>
                </body>
                                
                </html>""", response.content().toString(StandardCharsets.UTF_8));
    }

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            NettyDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

    // TODO remove usage of dispatch after we will be able use simple GET
    private FullHttpResponse dispatch(final String uri) {
        return dispatch(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
    }

    private FullHttpResponse dispatch(final FullHttpRequest request) {
        dispatcher.dispatch(request, callback);
        verify(callback, timeout(1000)).onSuccess(responseCaptor.capture());
        final var response = responseCaptor.getValue();
        assertNotNull(response);
        return response;
    }

    private void startDeviceSimulator(final boolean mdsal) throws Exception {
        // mdsal = true --> settable mode, mdsal datastore
        // mdsal = false --> simulated mode, data is taken from conf files
        devicePort = freePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .setMdSal(mdsal);
        if (mdsal) {
            configBuilder.setModels(Set.of(TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL));
        } else {
            configBuilder
                .setModels(Set.of(TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL))
                .setNotificationFile(new File(getClass().getResource("/device-sim-notifications.xml").toURI()));
        }
        deviceSimulator = new NetconfDeviceSimulator(configBuilder.build());
        deviceSimulator.start();
    }

    private void mountDeviceJson() throws Exception {
        // validate topology node is defined
        assertContentJson(TOPOLOGY_URI,
            """
                {
                    "network-topology:topology": [{
                        "topology-id":"topology-netconf"
                    }]
                }""");
        final var input = """
            {
               "network-topology:node": [{
                   "node-id": "device-sim",
                   "netconf-node-topology:netconf-node": {
                       "host": "%s",
                       "port": %d,
                       "login-password-unencrypted": {
                           "username": "%s",
                           "password": "%s"
                       },
                       "tcp-only": false
                   }
               }]
            }
            """.formatted(localAddress, devicePort, DEVICE_USERNAME, DEVICE_PASSWORD);
        final var response = invokeRequest(HttpMethod.POST, TOPOLOGY_URI, APPLICATION_JSON, input);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // wait till connected
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, DEVICE_STATUS_URI);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8));
        //{
        //  "netconf-node-topology:netconf-node": {
        //    "connection-status": "connected"
        //  }
        //}
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
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
        final var channelListener = new TestTransportListener();
        final var client = HTTPClient.connect(channelListener, bootstrapFactory.newBootstrap(),
            clientConf, false).get(2, TimeUnit.SECONDS);
        // await for connection
        await().atMost(Duration.ofSeconds(2)).until(channelListener::initialized);
        final var callback1 = new TestRequestCallback();
        client.invoke(request, callback1);
        // await for response
        await().atMost(Duration.ofSeconds(2)).until(callback1::completed);
        client.shutdown().get(2, TimeUnit.SECONDS);
        final var response = callback1.response();
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
        if (method != HttpMethod.GET) {
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

    private static SslContextFactoryProvider sslContextFactoryProvider() {
        // no TLS used in test -- provide default non-empty
        return specification -> (SslContextFactory) allowedKeys -> {
            try {
                return SslContextBuilder.forClient().build();
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
