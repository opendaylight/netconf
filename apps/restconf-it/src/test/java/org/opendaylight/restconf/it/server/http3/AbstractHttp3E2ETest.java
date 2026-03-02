/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server.http3;


import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient.Http3Response;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.opendaylight.mdsal.binding.api.ActionSpec;
import org.opendaylight.mdsal.binding.dom.adapter.BindingAdapterFactory;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMRpcProviderServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionProviderService;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcProviderService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.odl.device.notification.SubscribeDeviceNotificationRpc;
import org.opendaylight.netconf.sal.remote.impl.CreateDataChangeEventSubscriptionRpc;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HttpClientStackConfiguration;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.it.openapi.http3.Http3NettyTestClient;
import org.opendaylight.restconf.it.server.AbstractE2ETest;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.example.action.rev240919.Root;
import org.opendaylight.yang.gen.v1.example.action.rev240919.root.ExampleAction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.data.codec.impl.di.DefaultBindingDOMCodecServices;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttp3E2ETest extends AbstractE2ETest {
    protected Http3NettyTestClient client;

    @BeforeAll
    void perClass() throws Exception {
        client = new Http3NettyTestClient(USERNAME, PASSWORD);
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        // transport configuration
        final var port = randomBindablePort();
        host = localAddress + ":" + port;

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        X500Name dnName = new X500Name("CN=localhost");
        BigInteger certSerialNumber = new BigInteger(64, new SecureRandom());
        ZonedDateTime now = ZonedDateTime.now();
        Date notBefore = Date.from(now.toInstant());
        Date notAfter = Date.from(now.plus(1, ChronoUnit.YEARS).toInstant());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            dnName, certSerialNumber, notBefore, notAfter, dnName, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        X509Certificate certificate = new JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signer));

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
        clientStackGrouping = new HttpClientStackConfiguration(
            ConfigUtils.clientTransportTls(localAddress, port, certificate, USERNAME, PASSWORD));
        invalidClientStackGrouping = new HttpClientStackConfiguration(
            ConfigUtils.clientTransportTls(localAddress, port, certificate, USERNAME, "wrong-password"));

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
        final var adapterFactory = new BindingAdapterFactory(adapterContext);
        actionProviderService = adapterFactory.createActionProviderService(
            new RouterDOMActionProviderService(domRpcRouter));
        // action implementations
        actionProviderService.registerImplementation(
            ActionSpec.builder(Root.class).build(ExampleAction.class), new ExampleActionImpl());
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
        final var rpcImplementations = List.<RpcImplementation>of(
            // rpcImplementations
            new CreateDataChangeEventSubscriptionRpc(streamRegistry, dataBindProvider, domDataBroker),
            new SubscribeDeviceNotificationRpc(streamRegistry, domMountPointService)
        );
        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker,
            new RouterDOMRpcService(domRpcRouter), new RouterDOMActionService(domRpcRouter), domMountPointService,
            rpcImplementations);

        // Netty endpoint
        final var configuration = new NettyEndpointConfiguration(
            ERROR_TAG_MAPPING, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            "rests", MessageEncoding.JSON, serverStackGrouping, CHUNK_SIZE, FRAME_SIZE,
            null, localAddress, port, certificate, privateKey,
            HTTP3_ALT_SVC_MAX_AGE_SECONDS, HTTP3_INITIAL_MAX_DATA, HTTP3_INITIAL_MAX_STREAM_DATA_BIDIRECTIONAL_REMOTE,
            HTTP3_INITIAL_MAX_STREAMS_BIDIRECTIONAL);
        endpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory, configuration);
    }


    protected URI createUri(final String path) throws URISyntaxException {
        return new URI("https://" + host + path);
    }

    protected static void assertErrorResponseJson(final Http3Response response, final ErrorType expectedErrorType,
        final ErrorTag expectedErrorTag) {
        assertEquals(ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code(), response.status().code());
        final var json = new JSONObject(response.body(), JSON_PARSER_CONFIGURATION);
        final var error = json.getJSONObject("errors").getJSONArray("error").getJSONObject(0);
        assertNotNull(error);
        assertEquals(expectedErrorType.elementBody(), error.getString("error-type"));
        assertEquals(expectedErrorTag.elementBody(), error.getString("error-tag"));
        assertNotNull(error.getString("error-message"));
    }

    protected static void assertContentJson(final Http3Response response, final String expectedContent) {
        final var content = response.body();
        JSONAssert.assertEquals(expectedContent, content, JSONCompareMode.LENIENT);
    }

    protected static void assertOptionsResponse(final Http3Response response, final Set<String> methods) {
        assertEquals(HttpResponseStatus.OK, response.status());
        assertHeaderValue(response, HttpHeaderNames.ALLOW, methods);
    }

    protected static void assertHeaderValue(final Http3Response response, final CharSequence headerName,
        final Set<String> expectedValues) {
        final var headerValue = response.headers().get(headerName);
        assertNotNull(headerValue);
        assertEquals(expectedValues, COMMA_SPLITTER.splitToStream(headerValue).collect(toSet()));
    }

    protected static void assertContentXml(final Http3Response response, final String expectedContent) {
        final var content = response.body();
        assertThat(content, isSimilarTo(expectedContent).ignoreComments().ignoreWhitespace()
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName)));
    }
}
