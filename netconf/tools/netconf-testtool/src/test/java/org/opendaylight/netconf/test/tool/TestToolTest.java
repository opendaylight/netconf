/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import static org.junit.Assert.assertNotNull;
import static org.xmlunit.assertj.XmlAssert.assertThat;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.NeverReconnectStrategy;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.w3c.dom.Document;

@SuppressWarnings("SameParameterValue")
public class TestToolTest {

    private static final long RECEIVE_TIMEOUT_MS = 5_000;
    private static final int RANDOM_PORT = 0;

    private static final String[] ARGS = new String[]{
        "--starting-port", Integer.toString(RANDOM_PORT),
        "--schemas-dir", "src/test/resources/test-schemas/",
        "--rpc-config", "src/test/resources/customrpc.xml"
    };
    private static final User ADMIN_USER = new User("admin", "admin");
    private static final Configuration SSH_SIMULATOR_CONFIG = getSimulatorConfig(ARGS, NetconfClientProtocol.SSH,
        ADMIN_USER);
    private static final Configuration TCP_SIMULATOR_CONFIG = getSimulatorConfig(ARGS, NetconfClientProtocol.SSH,
        ADMIN_USER);

    private static NioEventLoopGroup nettyGroup;
    private static NetconfClientDispatcherImpl dispatcher;


    @Rule
    public LogPropertyCatcher logPropertyCatcher =
        new LogPropertyCatcher(Pattern.compile("(start\\(\\) listen on auto-allocated port="
            + "|Simulated TCP device started on (/0:0:0:0:0:0:0:0|/0.0.0.0):)(\\d+)"));

    private static final String XML_REQUEST_RFC7950_SECTION_4_2_9 = "<rpc message-id=\"101\"\n"
        + "          xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
        + "       <activate-software-image xmlns=\"http://example.com/system\">\n"
        + "         <image-name>example-fw-2.3</image-name>\n"
        + "       </activate-software-image>\n"
        + "     </rpc>";
    private static final String EXPECTED_XML_RESPONSE_RFC7950_SECTION_4_2_9 = "<rpc-reply message-id=\"101\"\n"
        + "                xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
        + "       <status xmlns=\"http://example.com/system\">\n"
        + "         The image example-fw-2.3 is being installed.\n"
        + "       </status>\n"
        + "     </rpc-reply>";
    private static final String XML_REQUEST_RFC7950_SECTION_7_15_3 = "<rpc message-id=\"101\"\n"
        + "          xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
        + "       <action xmlns=\"urn:ietf:params:xml:ns:yang:1\">\n"
        + "         <server xmlns=\"urn:example:server-farm\">\n"
        + "           <name>apache-1</name>\n"
        + "           <reset>\n"
        + "             <reset-at>2014-07-29T13:42:00Z</reset-at>\n"
        + "           </reset>\n"
        + "         </server>\n"
        + "       </action>\n"
        + "     </rpc>";
    private static final String EXPECTED_XML_RESPONSE_RFC7950_SECTION_7_15_3 = "<rpc-reply message-id=\"101\"\n"
        + "           xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
        + "  <reset-finished-at xmlns=\"urn:example:server-farm\">\n"
        + "    2014-07-29T13:42:12Z\n"
        + "  </reset-finished-at>\n"
        + "</rpc-reply>";

    @BeforeClass
    public static void setUpClass() {
        HashedWheelTimer hashedWheelTimer = new HashedWheelTimer();
        nettyGroup = new NioEventLoopGroup(1, new DefaultThreadFactory(NetconfClientDispatcher.class));
        dispatcher = new NetconfClientDispatcherImpl(nettyGroup, nettyGroup, hashedWheelTimer);
    }

    @AfterClass
    public static void cleanUpClass()
        throws InterruptedException {
        nettyGroup.shutdownGracefully().sync();
    }

    @Test
    public void customRpcOverSsh()
        throws Exception {
        Document docResponse = invokeRpc(SSH_SIMULATOR_CONFIG, XML_REQUEST_RFC7950_SECTION_7_15_3);
        assertThat(docResponse)
            .and(EXPECTED_XML_RESPONSE_RFC7950_SECTION_7_15_3)
            .ignoreWhitespace()
            .areIdentical();
    }

    @Test
    public void customRpcOverTcp()
        throws Exception {
        Document docResponse = invokeRpc(TCP_SIMULATOR_CONFIG, XML_REQUEST_RFC7950_SECTION_4_2_9);
        assertThat(docResponse)
            .and(EXPECTED_XML_RESPONSE_RFC7950_SECTION_4_2_9)
            .ignoreWhitespace()
            .areIdentical();
    }

    private Document invokeRpc(Configuration simulatorConfig, String xmlRequest)
        throws Exception {
        int localPort = launchSimulator(simulatorConfig);
        SimpleNetconfClientSessionListener sessionListener = new SimpleNetconfClientSessionListener();
        NetconfClientConfiguration clientConfig = getClientConfig("localhost", localPort,
            simulatorConfig, sessionListener);
        Document docRequest = XmlUtil.readXmlToDocument(xmlRequest);
        NetconfMessage request = new NetconfMessage(docRequest);

        // WHEN
        NetconfMessage response;
        try (NetconfClientSession ignored = dispatcher.createClient(clientConfig).get()) {
            response = sessionListener.sendRequest(request)
                .get(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        // THEN
        assertNotNull(response);
        return response.getDocument();
    }

    private static final ConcurrentHashMap<Configuration, Integer> CACHED_SIMULATORS = new ConcurrentHashMap<>();

    /**
     * Retrieves a previously launched simulator or launches a new one using the given configuration.
     *
     * @param configuration The simulator configuration.
     * @return The TCP port number to access the launched simulator.
     */
    private int launchSimulator(Configuration configuration) {
        return CACHED_SIMULATORS.computeIfAbsent(configuration, cfg -> {
            NetconfDeviceSimulator simulator = new NetconfDeviceSimulator(cfg);
            simulator.start();
            return logPropertyCatcher.getLastValue()
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalArgumentException("Unable to capture auto-allocated port from log"));
        });
    }

    private static Configuration getSimulatorConfig(String[] args, NetconfClientProtocol protocol, User user) {
        TesttoolParameters params = TesttoolParameters.parseArgs(args, TesttoolParameters.getParser());
        return new ConfigurationBuilder()
            .from(params)
            .setSsh(protocol == NetconfClientProtocol.SSH)
            .setAuthProvider(new InMemoryAuthenticationProvider(user))
            .build();
    }

    @SuppressWarnings("deprecation")
    private static NetconfClientConfiguration getClientConfig(String host, int port,
                                                              Configuration simulatorConfig,
                                                              NetconfClientSessionListener sessionListener) {
        User user = ((InMemoryAuthenticationProvider) simulatorConfig.getAuthProvider()).user;
        return NetconfClientConfigurationBuilder.create()
            .withAddress(new InetSocketAddress(host, port))
            .withSessionListener(sessionListener)
            .withReconnectStrategy(new NeverReconnectStrategy(GlobalEventExecutor.INSTANCE,
                NetconfClientConfigurationBuilder.DEFAULT_CONNECTION_TIMEOUT_MILLIS))
            .withProtocol(simulatorConfig.isSsh() ? NetconfClientProtocol.SSH : NetconfClientProtocol.TCP)
            .withAuthHandler(new LoginPasswordHandler(user.username, user.password))
            .build();
    }

    private static final class User {
        private final String username;
        private final String password;

        private User(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static final class InMemoryAuthenticationProvider implements AuthProvider {

        private final User user;

        private InMemoryAuthenticationProvider(User user) {
            this.user = user;
        }

        @Override
        public boolean authenticated(String username, String password) {
            return user.username.equals(username) && user.password.equals(password);
        }
    }
}
