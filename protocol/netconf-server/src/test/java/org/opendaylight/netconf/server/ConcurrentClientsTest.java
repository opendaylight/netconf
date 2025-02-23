/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientFactoryImpl;
import org.opendaylight.netconf.client.NetconfMessageUtil;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessageProvider;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev240814.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev241010.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@ExtendWith(MockitoExtension.class)
class ConcurrentClientsTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentClientsTest.class);

    private static final int CONCURRENCY = 32;
    private static final long TIMEOUT = 5000L;
    private static final Set<String> CAPS_EXI = Set.of(CapabilityURN.BASE, CapabilityURN.EXI);
    private static final Set<String> CAPS_1_1 = Set.of(CapabilityURN.BASE, CapabilityURN.BASE_1_1);
    private static final Capabilities EMPTY_CAPABILITIES = new CapabilitiesBuilder().setCapability(Set.of()).build();
    private static final SessionIdProvider ID_PROVIDER = new DefaultSessionIdProvider();

    private static ExecutorService clientExecutor;
    private static InetAddress serverAddress;
    private static int serverPort;
    private static TcpServerGrouping serverParams;
    private static TcpClientGrouping clientParams;
    private static DefaultNetconfTimer timer;

    private static NetconfMessage getConfigMessage;
    private static NetconfMessage clientHelloMessage;

    private BootstrapFactory serverBootstrapFactory;
    private NetconfClientFactory clientFactory;
    private TCPServer server;

    @Mock
    private NetconfMonitoringService monitoringService;
    @Mock
    private SessionListener serverSessionListener;
    private TestingNetconfOperation testingNetconfOperation;

    @BeforeAll
    static void beforeAll() throws Exception {
        timer = new DefaultNetconfTimer();
        clientExecutor = Executors.newFixedThreadPool(CONCURRENCY, new ThreadFactory() {
            int index = 1;

            @Override
            public Thread newThread(final Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("client-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });

        // create temp socket to get available port for test
        serverAddress = InetAddress.getLoopbackAddress();
        try (var socket = new ServerSocket(0)) {
            serverPort = socket.getLocalPort();
        }

        final var address = IetfInetUtil.ipAddressFor(serverAddress);
        final var port = new PortNumber(Uint16.valueOf(serverPort));
        serverParams = new TcpServerParametersBuilder()
            .setLocalBind(BindingMap.of(new LocalBindBuilder().setLocalAddress(address).setLocalPort(port).build()))
            .build();
        clientParams =
            new TcpClientParametersBuilder().setRemoteAddress(new Host(address)).setRemotePort(port).build();

        getConfigMessage = requireNonNull(XmlFileLoader.xmlFileToNetconfMessage("netconfMessages/getConfig.xml"));
        clientHelloMessage = requireNonNull(XmlFileLoader.xmlFileToNetconfMessage("netconfMessages/client_hello.xml"));
    }

    @AfterAll
    static void afterAll() {
        clientExecutor.shutdownNow();
        timer.close();
    }

    void startServer(final int threads, final Set<String> serverCapabilities) throws Exception {
        testingNetconfOperation = new TestingNetconfOperation();
        final var factoriesListener = new AggregatedNetconfOperationServiceFactory();
        factoriesListener.onAddNetconfOperationServiceFactory(
            new TestingOperationServiceFactory(testingNetconfOperation));

        doNothing().when(serverSessionListener).onSessionUp(any(NetconfServerSession.class));
        doNothing().when(serverSessionListener).onSessionDown(any(NetconfServerSession.class));
        doNothing().when(serverSessionListener).onSessionEvent(any(SessionEvent.class));
        lenient().doReturn((Registration) () -> {
        }).when(monitoringService)
            .registerCapabilitiesListener(any(NetconfMonitoringService.CapabilitiesListener.class));
        doReturn(serverSessionListener).when(monitoringService).getSessionListener();
        doReturn(EMPTY_CAPABILITIES).when(monitoringService).getCapabilities();

        serverBootstrapFactory = new BootstrapFactory("server", threads);
        server = TCPServer.listen(new ServerTransportInitializer(NetconfServerSessionNegotiatorFactory.builder()
            .setTimer(timer)
            .setAggregatedOpService(factoriesListener)
            .setIdProvider(ID_PROVIDER)
            .setConnectionTimeoutMillis(TIMEOUT)
            .setMonitoringService(monitoringService)
            .setBaseCapabilities(serverCapabilities)
            .build()), serverBootstrapFactory.newServerBootstrap(), serverParams)
            .get(TIMEOUT, MILLISECONDS);
    }

    @AfterEach
    void afterEach() throws Exception {
        server.shutdown().get(TIMEOUT, MILLISECONDS);
        serverBootstrapFactory.close();
        if (clientFactory != null) {
            clientFactory.close();
        }
    }

    @ParameterizedTest
    @MethodSource
    @Timeout(CONCURRENCY * 1000)
    void testConcurrentClients(final int threads, final Class<? extends Runnable> clientClass,
            final Set<String> serverCaps) throws Exception {

        startServer(threads, serverCaps);
        clientFactory = clientClass == NetconfClientRunnable.class
            ? new NetconfClientFactoryImpl(timer, new SSHTransportStackFactory("client", threads)) : null;

        final var futures = new ArrayList<Future<?>>(CONCURRENCY);
        for (int i = 0; i < CONCURRENCY; i++) {
            final var runnableClient = clientClass == NetconfClientRunnable.class
                ? new NetconfClientRunnable(clientFactory) : new BlockingClientRunnable();
            futures.add(clientExecutor.submit(runnableClient));
        }
        for (var future : futures) {
            future.get(TIMEOUT, MILLISECONDS);
        }
        assertEquals(CONCURRENCY, testingNetconfOperation.counter.get());
    }

    private static List<Arguments> testConcurrentClients() {
        return List.of(
            // (threads, runnable client class, server capabilities)
            Arguments.of(4, NetconfClientRunnable.class, DEFAULT_BASE_CAPABILITIES),
            Arguments.of(1, NetconfClientRunnable.class, DEFAULT_BASE_CAPABILITIES),
            // empty set of capabilities = only base 1.0 netconf capability
            Arguments.of(4, NetconfClientRunnable.class, Set.of()),
            Arguments.of(4, NetconfClientRunnable.class, CAPS_EXI),
            Arguments.of(4, NetconfClientRunnable.class, CAPS_1_1),
            Arguments.of(4, BlockingClientRunnable.class, CAPS_EXI),
            Arguments.of(1, BlockingClientRunnable.class, CAPS_EXI)
        );
    }

    /**
     * Responds to all operations except start-exi and counts all requests.
     */
    private static final class TestingNetconfOperation implements NetconfOperation {
        private final AtomicLong counter = new AtomicLong();

        @Override
        public HandlingPriority canHandle(final Document message) {
            return XmlUtil.toString(message).contains(NetconfStartExiMessageProvider.START_EXI)
                ? null : HandlingPriority.HANDLE_WITH_MAX_PRIORITY;
        }

        @Override
        public Document handle(final Document requestMessage,
                final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
            LOG.info("Handling netconf message from test {}", XmlUtil.toString(requestMessage));
            counter.getAndIncrement();
            try {
                return XmlUtil.readXmlToDocument("<test/>");
            } catch (IOException | SAXException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Hardcoded operation service factory.
     */
    private record TestingOperationServiceFactory(NetconfOperation... operations)
            implements NetconfOperationServiceFactory {

        @Override
        public Set<Capability> getCapabilities() {
            return Set.of();
        }

        @Override
        public Registration registerCapabilityListener(final CapabilityListener listener) {
            // No-op
            return () -> { };
        }

        @Override
        public NetconfOperationService createService(final SessionIdType sessionId) {
            return new NetconfOperationService() {

                @Override
                public Set<NetconfOperation> getNetconfOperations() {
                    return Set.of(operations);
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /**
     * Pure socket based blocking client.
     */
    private record BlockingClientRunnable() implements Runnable {
        @Override
        public void run() {
            try (var clientSocket = new Socket(serverAddress, serverPort)) {
                final var outToServer = new DataOutputStream(clientSocket.getOutputStream());
                final var inFromServer = new InputStreamReader(clientSocket.getInputStream());

                var sb = new StringBuilder();
                while (!sb.toString().endsWith("]]>]]>")) {
                    sb.append((char) inFromServer.read());
                }
                LOG.info(sb.toString());

                outToServer.write(clientHelloMessage.toString().getBytes(StandardCharsets.UTF_8));
                outToServer.write("]]>]]>".getBytes());
                outToServer.flush();
                // Thread.sleep(100);
                outToServer.write(getConfigMessage.toString().getBytes(StandardCharsets.UTF_8));
                outToServer.write("]]>]]>".getBytes());
                outToServer.flush();
                // Thread.sleep(100);
                sb = new StringBuilder();
                while (!sb.toString().endsWith("]]>]]>")) {
                    sb.append((char) inFromServer.read());
                }
                LOG.info(sb.toString());
            } catch (IOException e) {
                throw new IllegalStateException(Thread.currentThread().getName(), e);
            }
        }
    }

    /**
     * NetconfClientFactory based runnable.
     */
    private record NetconfClientRunnable(NetconfClientFactory factory) implements Runnable {

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public void run() {
            final var sessionListener = new SimpleNetconfClientSessionListener();
            final var clientConfig = NetconfClientConfigurationBuilder.create()
                .withTcpParameters(clientParams).withSessionListener(sessionListener).build();
            try (var session = factory.createClient(clientConfig).get()) {
                final var sessionId = session.sessionId();
                LOG.info("Client with session id {}: hello exchanged", sessionId);
                final var result = sessionListener.sendRequest(getConfigMessage).get();
                LOG.info("Client with session id {}: got result {}", sessionId.getValue(), result);

                checkState(!NetconfMessageUtil.isErrorMessage(result),
                    "Received error response: " + XmlUtil.toString(result.getDocument()) + " to request: "
                        + XmlUtil.toString(getConfigMessage.getDocument()));

                LOG.info("Client with session id {}: ended", sessionId.getValue());
            } catch (final Exception e) {
                throw new IllegalStateException(Thread.currentThread().getName(), e);
            }
        }
    }
}
