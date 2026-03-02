/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi.http3;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
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
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.openapi.AbstractOpenApiTest;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.restconf.openapi.netty.OpenApiResourceProvider;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AbstractOpenApiHttp3Test extends AbstractOpenApiTest {
    protected Http3NettyTestClient client;

    @BeforeAll
    void perClass() throws Exception {
        client = new Http3NettyTestClient(USERNAME, PASSWORD);
    }

    @AfterAll
    protected void afterClass() throws Exception {
        client.close();
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        // transport configuration
        port = randomBindablePort();
        host = localAddress + ":" + port;

        // Certificates
        final var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        final var keyPair = keyGen.generateKeyPair();
        final var privateKey = keyPair.getPrivate();

        final var x500Name = new X500Name("CN=TestCertificate");
        final var now = Instant.now();
        final var certBuilder =  new JcaX509v3CertificateBuilder(x500Name, BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(1))), x500Name, keyPair.getPublic());

        final var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        final var certificate = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        final var serverTransport = ConfigUtils.serverTransportTls(localAddress, port, certificate, privateKey);
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
        final var adapterContext = new ConstantAdapterContext(new DefaultBindingDOMCodecServices(getRuntimeContext()));
        rpcProviderService = new BindingDOMRpcProviderServiceAdapter(adapterContext,
            new RouterDOMRpcProviderService(domRpcRouter));
        domNotificationRouter = new DOMNotificationRouter(32);
        final ClusterSingletonServiceProvider cssProvider = service -> {
            service.instantiateServiceInstance();
            return service::closeServiceInstance;
        };
        streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker,
            new RouterDOMNotificationService(domNotificationRouter), schemaService,
            uri -> uri.resolve("streams"), dataBindProvider, cssProvider);
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker,
            new RouterDOMRpcService(domRpcRouter), new RouterDOMActionService(domRpcRouter), domMountPointService,
            List.of());

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(ERROR_TAG_MAPPING, PrettyPrintParam.FALSE,
            Uint16.ZERO, Uint32.valueOf(1000), RESTS, MessageEncoding.JSON, serverStackGrouping, CHUNK_SIZE,
            FRAME_SIZE, null, localAddress, port, certificate, privateKey, HTTP3_ALT_SVC_MAX_AGE_SECONDS,
            HTTP3_INITIAL_MAX_DATA, HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE,
            HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
            configuration);

        // Separate context for OpenApi with only toaster model
        final var openApiSchemaContext = YangParserTestUtils.parseYangResourceDirectory("/toaster/");
        final var openApiSchemaService = new FixedDOMSchemaService(openApiSchemaContext);

        // OpenApi
        final var mountPointOpenApiGeneratorRFC8040 = new MountPointOpenApiGeneratorRFC8040(openApiSchemaService,
            domMountPointService);
        // FIXME use constructor that has NettyEndpoint as parameter when we migrate to Netty in the future.
        final var openApiService = new OpenApiServiceImpl(mountPointOpenApiGeneratorRFC8040,
            new OpenApiGeneratorRFC8040(openApiSchemaService));
        final var openApiResourceProvider = new OpenApiResourceProvider(openApiService);
        endpoint.registerWebResource(openApiResourceProvider);
    }

    protected URI createApiUri(final String path) throws URISyntaxException {
        return new URI("https://" + host + API_V3_PATH + path);
    }

    @Override
    protected void assertContentJson(final String getRequestUri, final String expectedContent) throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(new URI("https://" + host + getRequestUri))
            .build());
        assertEquals(HttpResponseStatus.OK, response.status());
        final var content = response.content();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    @Override
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
            """.formatted(localAddress, devicePort, DEVICE_USERNAME, DEVICE_PASSWORD);
        final var response = client.send(HttpRequest.newBuilder()
            .uri(new URI("https://" + host + TOPOLOGY_URI))
            .POST(HttpRequest.BodyPublishers.ofString(input))
            .header(HttpHeaderNames.CONTENT_TYPE.toString(), APPLICATION_JSON)
            .build());
        assertEquals(HttpResponseStatus.CREATED, response.status());
        // wait till connected
        await().atMost(Duration.ofSeconds(50)).pollInterval(Duration.ofMillis(500))
            .until(this::deviceConnectedJson);
    }

    private boolean deviceConnectedJson() throws Exception {
        final var response = client.send(HttpRequest.newBuilder()
            .uri(new URI("https://" + host + DEVICE_STATUS_URI))
            .GET()
            .build());

        assertEquals(HttpResponseStatus.OK, response.status());
        final var json = new JSONObject(response.content(), JSON_PARSER_CONFIGURATION);
        //{
        //  "netconf-node-topology:netconf-node": {
        //    "connection-status": "connected"
        //  }
        //}
        final var status = json.getJSONObject("netconf-node-topology:netconf-node").getString("connection-status");
        return "connected".equals(status);
    }
}
