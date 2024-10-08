/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.restconf.server.netty.MountPointE2ETest;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class NettyDocumentTest extends MountPointE2ETest {
    private static final String BASE_PATH = "/openapi";
    private static final String API_V3_PATH = BASE_PATH + "/api/v3";
    private static final ObjectMapper MAPPER = new ObjectMapper();
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

    private OpenApiService openApiService;

    protected void initializeClass(final String yangPath){
//        final var context = YangParserTestUtils.parseYangResourceDirectory(yangPath);
//        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(schemaService, domMountPointService,
//            "rests");
//        final var openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService, "rests");
    }

    @BeforeEach
    void beforeEach() {
        initializeClass("/netty-documents/");
    }

    @Test
    void controllerAllDocTest() throws Exception {
        final var expectedJson = getExpectedDoc("netty-documents/controller-all.json");
        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/single");
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
        final var response = invokeRequest(HttpMethod.GET, uri);
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
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
        mountDeviceJson();
        final var expectedJson = getExpectedDoc("netty-documents/device-all.json");
        final var response = invokeRequest(HttpMethod.GET, API_V3_PATH + "/mounts/1");
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
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
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
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
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
        startDeviceSimulator(true, TOASTER_YANG_MODEL, TOASTER_OLD_YANG_MODEL);
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
}
