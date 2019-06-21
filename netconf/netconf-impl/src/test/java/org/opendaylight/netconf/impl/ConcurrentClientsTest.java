/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.monitoring.SessionEvent;
import org.opendaylight.netconf.api.monitoring.SessionListener;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.SimpleNetconfClientSessionListener;
import org.opendaylight.netconf.client.TestingNetconfClient;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.nettyutil.NeverReconnectStrategy;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

@RunWith(Parameterized.class)
public class ConcurrentClientsTest {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentClientsTest.class);

    private static ExecutorService clientExecutor;

    private static final int CONCURRENCY = 32;
    private static final InetSocketAddress NETCONF_ADDRESS = new InetSocketAddress("127.0.0.1", 8303);

    private final int nettyThreads;
    private final Class<? extends Runnable> clientRunnable;
    private final Set<String> serverCaps;

    public ConcurrentClientsTest(int nettyThreads, Class<? extends Runnable> clientRunnable, Set<String> serverCaps) {
        this.nettyThreads = nettyThreads;
        this.clientRunnable = clientRunnable;
        this.serverCaps = serverCaps;
    }

    @Parameterized.Parameters()
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{4, TestingNetconfClientRunnable.class,
                NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES},
            {1, TestingNetconfClientRunnable.class, NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES},
            // empty set of capabilities = only base 1.0 netconf capability
            {4, TestingNetconfClientRunnable.class, Collections.emptySet()},
            {4, TestingNetconfClientRunnable.class, getOnlyExiServerCaps()},
            {4, TestingNetconfClientRunnable.class, getOnlyChunkServerCaps()},
            {4, BlockingClientRunnable.class, getOnlyExiServerCaps()},
            {1, BlockingClientRunnable.class, getOnlyExiServerCaps()},
        });
    }

    private EventLoopGroup nettyGroup;
    private NetconfClientDispatcher netconfClientDispatcher;

    HashedWheelTimer hashedWheelTimer;
    private TestingNetconfOperation testingNetconfOperation;

    public static NetconfMonitoringService createMockedMonitoringService() {
        NetconfMonitoringService monitoring = mock(NetconfMonitoringService.class);
        final SessionListener sessionListener = mock(SessionListener.class);
        doNothing().when(sessionListener).onSessionUp(any(NetconfServerSession.class));
        doNothing().when(sessionListener).onSessionDown(any(NetconfServerSession.class));
        doNothing().when(sessionListener).onSessionEvent(any(SessionEvent.class));
        doReturn((AutoCloseable) () -> {

        }).when(monitoring).registerCapabilitiesListener(any(NetconfMonitoringService.CapabilitiesListener.class));
        doReturn(sessionListener).when(monitoring).getSessionListener();
        doReturn(new CapabilitiesBuilder().setCapability(Collections.emptyList()).build()).when(monitoring)
                .getCapabilities();
        return monitoring;
    }

    @BeforeClass
    public static void setUpClientExecutor() {
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
    }

    @Before
    public void setUp() throws Exception {
        hashedWheelTimer = new HashedWheelTimer();
        nettyGroup = new NioEventLoopGroup(nettyThreads);
        netconfClientDispatcher = new NetconfClientDispatcherImpl(nettyGroup, nettyGroup, hashedWheelTimer);

        AggregatedNetconfOperationServiceFactory factoriesListener = new AggregatedNetconfOperationServiceFactory();

        testingNetconfOperation = new TestingNetconfOperation();
        factoriesListener.onAddNetconfOperationServiceFactory(
                new TestingOperationServiceFactory(testingNetconfOperation));

        SessionIdProvider idProvider = new SessionIdProvider();

        NetconfServerSessionNegotiatorFactory serverNegotiatorFactory = new
                NetconfServerSessionNegotiatorFactoryBuilder()
                .setTimer(hashedWheelTimer)
                .setAggregatedOpService(factoriesListener)
                .setIdProvider(idProvider)
                .setConnectionTimeoutMillis(5000)
                .setMonitoringService(createMockedMonitoringService())
                .setBaseCapabilities(serverCaps)
                .build();

        ServerChannelInitializer serverChannelInitializer =
                new ServerChannelInitializer(serverNegotiatorFactory);
        final NetconfServerDispatcherImpl dispatch =
                new NetconfServerDispatcherImpl(serverChannelInitializer, nettyGroup, nettyGroup);

        ChannelFuture server = dispatch.createServer(NETCONF_ADDRESS);
        server.await();
    }

    @After
    public void tearDown() {
        hashedWheelTimer.stop();
        try {
            nettyGroup.shutdownGracefully().get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Ignoring exception while cleaning up after test", e);
        }
    }

    @AfterClass
    public static void tearDownClientExecutor() {
        clientExecutor.shutdownNow();
    }

    @Test(timeout = CONCURRENCY * 1000)
    public void testConcurrentClients() throws Exception {

        List<Future<?>> futures = Lists.newArrayListWithCapacity(CONCURRENCY);

        for (int i = 0; i < CONCURRENCY; i++) {
            futures.add(clientExecutor.submit(getInstanceOfClientRunnable()));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                LOG.error("Thread for testing client failed", e);
                throw e;
            }
        }

        assertEquals(CONCURRENCY, testingNetconfOperation.getMessageCount());
    }

    public static Set<String> getOnlyExiServerCaps() {
        return Sets.newHashSet(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_EXI_1_0
        );
    }

    public static Set<String> getOnlyChunkServerCaps() {
        return Sets.newHashSet(
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
        );
    }

    public Runnable getInstanceOfClientRunnable() throws Exception {
        return clientRunnable.getConstructor(ConcurrentClientsTest.class).newInstance(this);
    }

    /**
     * Responds to all operations except start-exi and counts all requests.
     */
    private static class TestingNetconfOperation implements NetconfOperation {

        private final AtomicLong counter = new AtomicLong();

        @Override
        public HandlingPriority canHandle(Document message) {
            return XmlUtil.toString(message).contains(NetconfStartExiMessage.START_EXI)
                    ? HandlingPriority.CANNOT_HANDLE :
                    HandlingPriority.HANDLE_WITH_MAX_PRIORITY;
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public Document handle(Document requestMessage, NetconfOperationChainedExecution subsequentOperation)
                throws DocumentedException {
            try {
                LOG.info("Handling netconf message from test {}", XmlUtil.toString(requestMessage));
                counter.getAndIncrement();
                return XmlUtil.readXmlToDocument("<test/>");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public long getMessageCount() {
            return counter.get();
        }
    }

    /**
     * Hardcoded operation service factory.
     */
    private static class TestingOperationServiceFactory implements NetconfOperationServiceFactory {
        private final NetconfOperation[] operations;

        TestingOperationServiceFactory(final NetconfOperation... operations) {
            this.operations = operations;
        }

        @Override
        public Set<Capability> getCapabilities() {
            return Collections.emptySet();
        }

        @Override
        public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
            return () -> {
            };
        }

        @Override
        public NetconfOperationService createService(String netconfSessionIdForReporting) {
            return new NetconfOperationService() {

                @Override
                public Set<NetconfOperation> getNetconfOperations() {
                    return Sets.newHashSet(operations);
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
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final class BlockingClientRunnable implements Runnable {

        @Override
        public void run() {
            try {
                run2();
            } catch (Exception e) {
                throw new IllegalStateException(Thread.currentThread().getName(), e);
            }
        }

        private void run2() throws Exception {
            InputStream clientHello = checkNotNull(XmlFileLoader
                    .getResourceAsStream("netconfMessages/client_hello.xml"));
            final InputStream getConfig =
                    checkNotNull(XmlFileLoader.getResourceAsStream("netconfMessages/getConfig.xml"));

            Socket clientSocket = new Socket(NETCONF_ADDRESS.getHostString(), NETCONF_ADDRESS.getPort());
            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
            InputStreamReader inFromServer = new InputStreamReader(clientSocket.getInputStream());

            StringBuilder sb = new StringBuilder();
            while (!sb.toString().endsWith("]]>]]>")) {
                sb.append((char) inFromServer.read());
            }
            LOG.info(sb.toString());

            outToServer.write(ByteStreams.toByteArray(clientHello));
            outToServer.write("]]>]]>".getBytes());
            outToServer.flush();
            // Thread.sleep(100);
            outToServer.write(ByteStreams.toByteArray(getConfig));
            outToServer.write("]]>]]>".getBytes());
            outToServer.flush();
            Thread.sleep(100);
            sb = new StringBuilder();
            while (!sb.toString().endsWith("]]>]]>")) {
                sb.append((char) inFromServer.read());
            }
            LOG.info(sb.toString());
            clientSocket.close();
        }
    }

    /**
     * TestingNetconfClient based runnable.
     */
    public final class TestingNetconfClientRunnable implements Runnable {

        @SuppressWarnings("checkstyle:IllegalCatch")
        @Override
        public void run() {
            try {
                final TestingNetconfClient netconfClient =
                        new TestingNetconfClient(Thread.currentThread().getName(), netconfClientDispatcher,
                                getClientConfig());
                long sessionId = netconfClient.getSessionId();
                LOG.info("Client with session id {}: hello exchanged", sessionId);

                final NetconfMessage getMessage = XmlFileLoader
                        .xmlFileToNetconfMessage("netconfMessages/getConfig.xml");
                NetconfMessage result = netconfClient.sendRequest(getMessage).get();
                LOG.info("Client with session id {}: got result {}", sessionId, result);

                Preconditions.checkState(NetconfMessageUtil.isErrorMessage(result) == false,
                        "Received error response: " + XmlUtil.toString(result.getDocument()) + " to request: "
                                + XmlUtil.toString(getMessage.getDocument()));

                netconfClient.close();
                LOG.info("Client with session id {}: ended", sessionId);
            } catch (final Exception e) {
                throw new IllegalStateException(Thread.currentThread().getName(), e);
            }
        }

        private NetconfClientConfiguration getClientConfig() {
            final NetconfClientConfigurationBuilder b = NetconfClientConfigurationBuilder.create();
            b.withAddress(NETCONF_ADDRESS);
            b.withAdditionalHeader(new NetconfHelloMessageAdditionalHeader("uname", "10.10.10.1", "830", "tcp",
                    "client"));
            b.withSessionListener(new SimpleNetconfClientSessionListener());
            b.withReconnectStrategy(new NeverReconnectStrategy(GlobalEventExecutor.INSTANCE,
                    NetconfClientConfigurationBuilder.DEFAULT_CONNECTION_TIMEOUT_MILLIS));
            return b.build();
        }
    }
}
