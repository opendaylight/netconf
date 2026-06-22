/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNull;
import org.json.JSONObject;
import org.json.JSONParserConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.netconf.client.mdsal.DeviceActionFactoryImpl;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultBaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.impl.DefaultSchemaResourceManager;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactoryImpl;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.restconf.it.AbstractTest;
import org.opendaylight.restconf.it.server.NullAAAEncryptionService;
import org.opendaylight.restconf.openapi.OpenApiResourceProvider;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.dagger.yang.parser.DaggerDefaultYangParserComponent;
import org.opendaylight.yangtools.yang.model.spi.source.YangTextToIRSourceTransformer;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.opendaylight.yangtools.yang.source.ir.dagger.YangIRSourceModule;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class AbstractOpenApiTest extends AbstractTest {
    private static final JSONParserConfiguration JSON_PARSER_CONFIGURATION = new JSONParserConfiguration()
        .withStrictMode();
    private static final @NonNull YangParserFactory PARSER_FACTORY =
        DaggerDefaultYangParserComponent.create().parserFactory();
    private static final @NonNull YangTextToIRSourceTransformer TEXT_TO_IR = YangIRSourceModule.provideTextToIR();

    private static final String TOPOLOGY_URI =
        "/rests/data/network-topology:network-topology/topology=topology-netconf";
    private static final String DEVICE_NODE_URI = TOPOLOGY_URI + "/node=device-sim";
    private static final String DEVICE_STATUS_URI =
        DEVICE_NODE_URI + "/netconf-node-topology:netconf-node?fields=connection-status";

    protected static final String DEVICE_USERNAME = "device-username";
    protected static final String DEVICE_PASSWORD = "device-password";
    protected static final String RESTS = "rests";
    protected static final String API_V3_PATH = "/openapi/api/v3";
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final String TOASTER = "toaster";
    protected static final String TOASTER_REV = "2009-11-20";
    /**
     * Model toaster@2009-11-19 is used for test correct generating of openapi with models with same name and another
     * revision date. We want to test that the same model is not duplicated and loaded just the newest version.
     */
    protected static final String TOASTER_OLD_REV = "2009-11-19";

    @TempDir
    private File tmpDir;


    @BeforeEach
    protected void beforeEach() throws Exception {
        super.beforeEach();

        // Separate context for OpenApi with only toaster model
        final var openApiSchemaContext = YangParserTestUtils.parseYangResourceDirectory("/toaster/");
        final var openApiSchemaService = new FixedDOMSchemaService(openApiSchemaContext);

        // OpenApi
        final var openApiResourceProvider = new OpenApiResourceProvider(openApiSchemaService, domMountPointService,
            new OpenApiResourceProvider.Configuration() {
                @Override
                public String api$_$root$_$path() {
                    return RESTS;
                }

                @Override
                public Class<OpenApiResourceProvider.Configuration> annotationType() {
                    return OpenApiResourceProvider.Configuration.class;
                }
            });
        endpoint.registerWebResource(openApiResourceProvider);
    }



    protected List<RpcImplementation> rpcImplementations(final DOMDataBroker domDataBroker) {
        return List.of();
    }

//    @BeforeEach
//    protected void beforeEach() throws Exception {
//        // transport configuration
//        port = randomBindablePort();
//        host = localAddress + ":" + port;
//        final var serverTransport = HTTPServerOverTcp.of(localAddress, port);
//        final var serverStackGrouping = new HttpServerListenStackGrouping() {
//            @Override
//            public Class<HttpServerListenStackGrouping> implementedInterface() {
//                return HttpServerListenStackGrouping.class;
//            }
//
//            @Override
//            public HttpOverTcp getTransport() {
//                return serverTransport;
//            }
//        };
//        clientStackGrouping = new HttpClientStackConfiguration(
//            ConfigUtils.clientTransportTcp(localAddress, port, USERNAME, PASSWORD));
//
//        // AAA services
//        final var securityManager = new DefaultWebSecurityManager(new AuthenticatingRealm() {
//            @Override
//            protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
//                    throws AuthenticationException {
//                final var principal = (String) token.getPrincipal();
//                final var credentials = new String((char[]) token.getCredentials());
//                if (USERNAME.equals(principal) && PASSWORD.equals(credentials)) {
//                    return new SimpleAuthenticationInfo(principal, credentials, "user");
//                }
//                return null;
//            }
//        });
//        final var principalService = new AAAShiroPrincipalService(securityManager);
//
//        // MDSAL services
//        setup();
//        final var domDataBroker = getDomBroker();
//        final var schemaContext = getRuntimeContext().modelContext();
//        final var schemaService = new FixedDOMSchemaService(schemaContext);
//        final var dataBindProvider = new MdsalDatabindProvider(schemaService);
//        domRpcRouter = new DOMRpcRouter(schemaService);
//        domMountPointService = new DOMMountPointServiceImpl();
// final var adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));
//        rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext,
//            new RouterDOMRpcProviderService(domRpcRouter));
//        domNotificationRouter = new DOMNotificationRouter(32);
//        final ClusterSingletonServiceProvider cssProvider = service -> {
//            service.instantiateServiceInstance();
//            return service::closeServiceInstance;
//        };
//        streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker,
//            new RouterDOMNotificationService(domNotificationRouter), schemaService,
//            uri -> uri.resolve("streams"), dataBindProvider, cssProvider);
//        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker,
//            new RouterDOMRpcService(domRpcRouter), new RouterDOMActionService(domRpcRouter), domMountPointService,
//            List.of());
//
//        // Netty endpoint
//        final var configuration = createEndpointConfiguration(serverStackGrouping);
//        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
//            configuration);
//
//        // Separate context for OpenApi with only toaster model
//        final var openApiSchemaContext = YangParserTestUtils.parseYangResourceDirectory("/toaster/");
//        final var openApiSchemaService = new FixedDOMSchemaService(openApiSchemaContext);
//
//        // OpenApi
//        final var openApiResourceProvider = new OpenApiResourceProvider(openApiSchemaService, domMountPointService,
//            new OpenApiResourceProvider.Configuration() {
//                @Override
//                public String api$_$root$_$path() {
//                    return RESTS;
//                }
//
//                @Override
//                public Class<OpenApiResourceProvider.Configuration> annotationType() {
//                    return OpenApiResourceProvider.Configuration.class;
//                }
//            });
//        endpoint.registerWebResource(openApiResourceProvider);
//    }

    protected void mountDeviceJson(final int devicePort) throws Exception {
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
            """.formatted(localAddress(), devicePort, DEVICE_USERNAME, DEVICE_PASSWORD);
        final var response = invokeRequest(HttpMethod.POST, TOPOLOGY_URI, APPLICATION_JSON, input);
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // wait till connected
        await().atMost(Duration.ofSeconds(50)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = invokeRequest(HttpMethod.GET, DEVICE_STATUS_URI);
        assertEquals(HttpResponseStatus.OK, response.status());
        final var json = new JSONObject(response.content().toString(StandardCharsets.UTF_8), JSON_PARSER_CONFIGURATION);
        //{
        //  "netconf-node-topology:netconf-node": {
        //    "connection-status": "connected"
        //  }
        //}
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
    }

    protected NetconfTopologyImpl setupTopology() {
        final var dataBroker = getDataBroker();
        final var netconfTimer = new DefaultNetconfTimer();
        final var encryptionService = new NullAAAEncryptionService();
        final var netconfClientConfBuilderFactory = new NetconfClientConfigurationBuilderFactoryImpl(encryptionService,
            id -> null, sslContextFactoryProvider());
        final var netconfClientFactory = new NetconfClientFactoryImpl(netconfTimer, sshTransportStackFactory());
        final var topologySchemaAssembler = new NetconfTopologySchemaAssembler(4);
        final var schemaSourceMgr =
            new DefaultSchemaResourceManager(PARSER_FACTORY, TEXT_TO_IR, tmpDir.getAbsolutePath(), "schema");
        final var baseSchemaProvider = new DefaultBaseNetconfSchemaProvider(PARSER_FACTORY);

        return new NetconfTopologyImpl(netconfClientFactory, netconfTimer, topologySchemaAssembler,
            schemaSourceMgr, dataBroker, domMountPointService, encryptionService, netconfClientConfBuilderFactory,
            rpcProviderService(), baseSchemaProvider, new DeviceActionFactoryImpl());
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

    /**
     * Finds a servers node in schema and replaces port value inside with new port value. Used in tests to replace port
     * in JSON schema file with random port that was used in transport configuration.
     *
     * @param jsonString JSON schema body with random port
     * @param port port to replace the random one
     * @param scheme http or https scheme
     * @return a schema with correct port
     */
    protected static String fillPort(final String jsonString, final int port, final String scheme)
            throws JsonProcessingException {
        final var json = (ObjectNode) MAPPER.readTree(jsonString);
        json.putArray("servers").add(MAPPER.readTree("{\"url\": \"" + scheme + "://127.0.0.1:" + port + "/\"}"));
        return MAPPER.writeValueAsString(json);
    }

    protected static String fillPort(final String jsonString, final int port) throws JsonProcessingException {
        return fillPort(jsonString, port, "http");
    }
}
