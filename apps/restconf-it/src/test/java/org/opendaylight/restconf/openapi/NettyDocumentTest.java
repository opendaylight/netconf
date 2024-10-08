/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.TestUtils.freePort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.openapi.TestUtils.TestClientStackGrouping;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@ExtendWith(MockitoExtension.class)
public class NettyDocumentTest extends AbstractDataBrokerTest {
    private static final ErrorTagMapping ERROR_TAG_MAPPING = ErrorTagMapping.RFC8040;
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$w0Rd";
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

    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";
    private static final String DEVICE_MOUNT_URI = DEVICE_NODE_URI + "/yang-ext:mount";
    private static final String DEVICE_DATA_ROOT_URI = DEVICE_MOUNT_URI + "/device-sim:data-root";

    private static String localAddress;
    private static BootstrapFactory bootstrapFactory;
    private static SSHTransportStackFactory sshTransportStackFactory;

    private HttpClientStackGrouping clientStackGrouping;
    private OpenApiService openApiService;
    private OpenApiRequestDispatcher dispatcher;
    private PrincipalService principalService;
    private NettyEndpoint endpoint;
    private String host;

    @Mock
    private FutureCallback<FullHttpResponse> callback;
    @Captor
    private ArgumentCaptor<FullHttpResponse> responseCaptor;

    // TODO this is duplicate from NETCONF-1367. Simplify upon merge.
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

        // TODO mount point
        final var mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getIdentifier()).thenReturn(INSTANCE_ID);

        // OpenApi
        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, domMountPointService,
            "rests");
        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService, "rests");
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(mountPoint);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
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
        final var expectedJson = getExpectedDoc("netty-documents/device-all.json");
        final var response = dispatch(API_V3_PATH + "/mounts/1");
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
        final var expectedJson = getExpectedDoc("netty-documents/" + jsonPath);
        var uri = API_V3_PATH + "/mounts/1/" + TOASTER + "?revision=" + revision;
        final var response = dispatch(uri);
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

    protected static String getExpectedDoc(final String jsonPath) throws Exception {
        return MAPPER.writeValueAsString(MAPPER.readTree(
            NettyDocumentTest.class.getClassLoader().getResourceAsStream(jsonPath)));
    }

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
}
