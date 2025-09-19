/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.SSH;
import static org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol.TCP;
import static org.xmlunit.assertj.XmlAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.listen.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.w3c.dom.Document;

public class TestToolTest {
    private static final long RESPONSE_TIMEOUT_MS = 30_000;
    private static final int RANDOM_PORT = 0;
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";
    private static final AuthProvider AUTH_PROVIDER = (user, passw) -> USERNAME.equals(user) && PASSWORD.equals(passw);
    private static final Path CUSTOM_RPC_CONFIG = Path.of("src", "test", "resources", "customrpc.xml");

    private static final String RFC7950_4_2_9_REQUEST = """
        <rpc message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <activate-software-image xmlns="http://example.com/system">
               <image-name>example-fw-2.3</image-name>
            </activate-software-image>
        </rpc>""";
    private static final String RFC7950_4_2_9_RESPONSE = """
        <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <status xmlns="http://example.com/system">
                The image example-fw-2.3 is being installed.
            </status>
        </rpc-reply>""";
    private static final String RFC7950_7_15_3_REQUEST = """
        <rpc message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <action xmlns="urn:ietf:params:xml:ns:yang:1">
                <server xmlns="urn:example:server-farm">
                    <name>apache-1</name>
                    <reset>
                        <reset-at>2014-07-29T13:42:00Z</reset-at>
                    </reset>
                </server>
            </action>
        </rpc>""";
    private static final String RFC7950_7_15_3_RESPONSE = """
        <rpc-reply message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
            <reset-finished-at xmlns="urn:example:server-farm">
                2014-07-29T13:42:12Z
            </reset-finished-at>
        </rpc-reply>""";
    private static final String GET_SCHEMAS_REQUEST = """
        <rpc xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="m-0">
            <get>
                <filter xmlns:ns0="urn:ietf:params:xml:ns:netconf:base:1.0" ns0:type="subtree">
                    <netconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring">
                        <schemas/>
                    </netconf-state>
                </filter>
            </get>
        </rpc>""";
    private static final Map<String, String> PREFIX_2_URI = ImmutableMap.of(
        "base10", "urn:ietf:params:xml:ns:netconf:base:1.0",
        "ncmon", "urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring"
    );

    private static DefaultNetconfTimer timer;
    private static NetconfClientFactory clientFactory;
    private static NetconfDeviceSimulator tcpDeviceSimulator;
    private static NetconfDeviceSimulator sshDeviceSimulator;
    private static int tcpDevicePort;
    private static int sshDevicePort;

    @BeforeAll
    static void beforeAll() {
        timer = new DefaultNetconfTimer();
        clientFactory = new NetconfClientFactoryImpl(timer);
        tcpDeviceSimulator = new NetconfDeviceSimulator(getSimulatorConfig(TCP));
        tcpDevicePort = startSimulator(tcpDeviceSimulator);
        sshDeviceSimulator = new NetconfDeviceSimulator(getSimulatorConfig(SSH));
        sshDevicePort = startSimulator(sshDeviceSimulator);
    }

    @AfterAll
    public static void afterAll() throws Exception {
        stopSimulator(tcpDeviceSimulator);
        tcpDeviceSimulator = null;
        stopSimulator(sshDeviceSimulator);
        sshDeviceSimulator = null;
        clientFactory.close();
        timer.close();
    }

    @ParameterizedTest(name = "Custom RPC -- RFC7950 {0}")
    @MethodSource("customRpcArgs")
    void customRpc(final String ignoredTestDesc, final NetconfClientProtocol protocol, final String requestXml,
            final String responseXml) throws Exception {
        final var docResponse = sendRequest(protocol, requestXml);
        assertThat(docResponse).and(responseXml).ignoreWhitespace().areIdentical();
    }

    private static Stream<Arguments> customRpcArgs() {
        return Stream.of(
            // # test descriptor, protocol, request, expected response
            Arguments.of("#7.15.3 @TCP", TCP, RFC7950_7_15_3_REQUEST, RFC7950_7_15_3_RESPONSE),
            Arguments.of("#7.15.3 @SSH", SSH, RFC7950_7_15_3_REQUEST, RFC7950_7_15_3_RESPONSE),
            Arguments.of("#4.2.9 @TCP", TCP, RFC7950_4_2_9_REQUEST, RFC7950_4_2_9_RESPONSE),
            Arguments.of("#4.2.9 @SSH", SSH, RFC7950_4_2_9_REQUEST, RFC7950_4_2_9_RESPONSE)
        );
    }

    @ParameterizedTest(name = "Get Schemas @{0}")
    @MethodSource("getSchemasArgs")
    void getSchemas(final NetconfClientProtocol protocol) throws Exception {
        final var docResponse = sendRequest(protocol, GET_SCHEMAS_REQUEST);
        final var expectedYangResources = Configuration.DEFAULT_YANG_RESOURCES;
        assertEquals(5, expectedYangResources.size());
        assertThat(docResponse)
            .withNamespaceContext(PREFIX_2_URI)
            .valueByXPath("count(//base10:rpc-reply/base10:data/ncmon:netconf-state/ncmon:schemas/ncmon:schema)")
            .isEqualTo(expectedYangResources.size());
    }

    private static List<NetconfClientProtocol> getSchemasArgs() {
        return List.of(SSH, TCP);
    }

    private static Document sendRequest(final NetconfClientProtocol protocol, final String xml)
        throws Exception {
        final var sessionListener = new SimpleNetconfClientSessionListener();
        final int port = SSH == protocol ? sshDevicePort : tcpDevicePort;
        final var clientConfig = getClientConfig(port, protocol, sessionListener);
        final var request = new NetconfMessage(XmlUtil.readXmlToDocument(xml));
        NetconfMessage response;
        try (NetconfClientSession ignored = clientFactory.createClient(clientConfig)
            .get(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            response = sessionListener.sendRequest(request).get(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        assertNotNull(response);
        return response.getDocument();
    }

    /**
     * Runs a simulator.
     *
     * @param simulator simulator instance
     * @return The TCP port number to access the launched simulator.
     */
    private static int startSimulator(final NetconfDeviceSimulator simulator) {
        final var openDevices = simulator.start();
        if (openDevices != null && !openDevices.isEmpty()) {
            return openDevices.get(0);
        }
        throw new IllegalStateException("Could not start device simulator");
    }

    private static void stopSimulator(final NetconfDeviceSimulator simulator) {
        if (simulator != null) {
            simulator.close();
        }
    }

    @SuppressWarnings("deprecation")
    private static Configuration getSimulatorConfig(final NetconfClientProtocol protocol) {
        return new ConfigurationBuilder()
            .setStartingPort(RANDOM_PORT)
            .setDeviceCount(1)
            .setRpcConfigFile(CUSTOM_RPC_CONFIG.toFile())
            .setSsh(SSH == protocol)
            .setAuthProvider(AUTH_PROVIDER)
            .build();
    }

    private static NetconfClientConfiguration getClientConfig(final int port,
        final NetconfClientProtocol protocol, final NetconfClientSessionListener sessionListener) {
        return NetconfClientConfigurationBuilder.create()
            .withTcpParameters(
                new TcpClientParametersBuilder()
                    .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress())))
                    .setRemotePort(new PortNumber(Uint16.valueOf(port)))
                    .build())
            .withSshParameters(
                new SshClientParametersBuilder()
                    .setClientIdentity(new ClientIdentityBuilder()
                        .setUsername(USERNAME)
                        .setPassword(new PasswordBuilder().setPasswordType(
                            new CleartextPasswordBuilder().setCleartextPassword(PASSWORD).build()
                        ).build()).build()).build())
            .withSessionListener(sessionListener)
            .withProtocol(protocol)
            .build();
    }
}
