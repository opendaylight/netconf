/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.util.Timer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshSessionContext;
import org.opendaylight.netconf.callhome.server.ssh.CallHomeSshSessionContextManager;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsAuthProvider;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsSessionContext;
import org.opendaylight.netconf.callhome.server.tls.CallHomeTlsSessionContextManager;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeHandler;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240118.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Service is responsible for call-home to topology integration.
 *
 * <p>
 * To manage remote device as a topology node the topology component (based on
 * {@link org.opendaylight.netconf.topology.spi.AbstractNetconfTopology AbstractNetconfTopology}) creates an instance
 * of {@link org.opendaylight.netconf.topology.spi.NetconfNodeHandler NetconfNodeHandler} based on provided
 * {@link Node}.
 *
 * <p>
 * The mentioned NetconfNodeHandler initializes connection to remote device via sequence of following actions (see
 * {@link org.opendaylight.netconf.topology.spi.AbstractNetconfTopology#ensureNode(Node) ensureNode(Node)} and
 * {@link NetconfNodeHandler#lockedConnect() connect()}):
 *
 * <ul>
 *     <li>Builds an instance of {@link org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator
 *     NetconfDeviceCommunicator} implementation of {@link NetconfClientSessionListener} which is used to check the
 *     NETCONF session state and communicate with device using NETCONF protocol </li>
 *     <li>Builds Netconf client configuration using provided {@link NetconfClientConfigurationBuilderFactory}</li>
 *     <li>Builds Netconf client using configuration composed and triggers connection</li>
 * </ul>
 *
 * <p>
 * This service uses custom implementations of {@link NetconfClientConfigurationBuilderFactory} and
 * {@link NetconfClientFactory} in order to capture the instance of {@link NetconfClientSessionListener} from topology
 * component which is required to establish NETCONF layer. See {@link #createClientConfigurationBuilderFactory()}
 * and {@link #createClientFactory()}.
 *
 * <p>
 * Following sequence of actions is performed when incoming connection is mapped to topology node:
 *
 * <ul>
 *     <li>When incoming connection is identified the {@link CallHomeSshSessionContext} instance expected to be created.
 *     The createContext() method is invoked within protocol associated
 *     {@link org.opendaylight.netconf.callhome.server.CallHomeSessionContextManager CallHomeSessionContextManager} --
 *     see {@link #createSshSessionContextManager()} and
 *     {@link #createTlsSessionContextManager(CallHomeTlsAuthProvider, CallHomeStatusRecorder)}</li>
 *     <li>Due to both {@link NetconfClientSessionListener} and {@link SettableFuture} are required to build session
 *     context the {@link CallHomeTopology#enableNode(Node)} (Node)} is called using synthetic {@link Node} instance
 *     composed via {@link #asNode(String, SocketAddress, Protocol)}. This triggers Netconf client construct/connect
 *     logic (as explained above) resulting captured object placed into {@link #netconfLayerMapping}.</li>
 *     <li>Accepted instance of {@link NetconfClientSessionListener} is used to establish Netconf layer --
 *     see {@link org.opendaylight.netconf.callhome.server.CallHomeTransportChannelListener
 *     CallHomeTransportChannelListener} </li>
 *     <li>Accepted instance of {@link SettableFuture} (representing connection to remote device) is used to
 *     signal connection state to topology component</li>
 * </ul>
 */
@Component(service = CallHomeMountService.class, immediate = true)
@Singleton
public final class CallHomeMountService implements AutoCloseable {
    private static final Protocol SSH_PROTOCOL = new ProtocolBuilder().setName(Protocol.Name.SSH).build();
    private static final Protocol TLS_PROTOCOL = new ProtocolBuilder().setName(Protocol.Name.TLS).build();

    private final Map<String, NetconfLayer> netconfLayerMapping = new ConcurrentHashMap<>();
    private final CallHomeTopology topology;

    @Activate
    @Inject
    public CallHomeMountService(
            final @Reference(target = "(type=global-timer)") Timer timer,
            final @Reference(target = "(type=global-netconf-processing-executor)") ThreadPool processingThreadPool,
            final @Reference SchemaResourceManager schemaRepositoryProvider,
            final @Reference BaseNetconfSchemas baseSchemas,
            final @Reference DataBroker dataBroker,
            final @Reference DOMMountPointService mountService,
            final @Reference DeviceActionFactory deviceActionFactory) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, timer,
            processingThreadPool.getExecutor(), schemaRepositoryProvider, baseSchemas,
            dataBroker, mountService, deviceActionFactory);
    }

    public CallHomeMountService(final String topologyId, final Timer timer, final Executor executor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final DeviceActionFactory deviceActionFactory) {

        final var clientConfBuilderFactory = createClientConfigurationBuilderFactory();
        final var clientFactory = createClientFactory();
        topology = new CallHomeTopology(topologyId, clientFactory, timer, executor,
            schemaRepositoryProvider, dataBroker, mountService, clientConfBuilderFactory,
            baseSchemas, deviceActionFactory);
    }

    @VisibleForTesting
    CallHomeMountService(final CallHomeTopology topology) {
        this.topology = topology;
    }

    @VisibleForTesting
    static NetconfClientConfigurationBuilderFactory createClientConfigurationBuilderFactory() {
        // use minimal configuration, only id and session listener are used
        return (nodeId, node) -> NetconfClientConfigurationBuilder.create()
            .withName(nodeId.getValue())
            // below parameters are only required to pass configuration validation
            // actual values play no role
            .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
            .withTcpParameters(new TcpClientParametersBuilder().build());
    }

    @VisibleForTesting
    NetconfClientFactory createClientFactory() {
        return new NetconfClientFactory() {
            @Override
            public ListenableFuture<NetconfClientSession> createClient(
                    final NetconfClientConfiguration clientConfiguration) throws UnsupportedConfigurationException {
                final var future = SettableFuture.<NetconfClientSession>create();
                final var pending = new NetconfLayer(clientConfiguration.getName(),
                    clientConfiguration.getSessionListener(), future);
                netconfLayerMapping.put(pending.id, pending);
                return future;
            }

            @Override
            public void close() throws Exception {
                // do nothing
            }
        };
    }

    private static Node asNode(final String id, final SocketAddress socketAddress, final Protocol protocol) {
        final var nodeAddress = socketAddress instanceof InetSocketAddress inetSocketAddress
            ? inetSocketAddress : new InetSocketAddress("0.0.0.0", 0);
        // construct synthetic Node object with minimal required parameters
        return new NodeBuilder()
            .setNodeId(new NodeId(id))
            .addAugmentation(new NetconfNodeBuilder()
                .setHost(new Host(IetfInetUtil.ipAddressFor(nodeAddress.getAddress())))
                .setPort(new PortNumber(Uint16.valueOf(nodeAddress.getPort())))
                .setTcpOnly(false)
                .setProtocol(protocol)
                // below parameters are required for NetconfNodeHandler
                .setSchemaless(false)
                .setReconnectOnChangedSchema(false)
                .setConnectionTimeoutMillis(Uint32.valueOf(20000))
                .setDefaultRequestTimeoutMillis(Uint32.valueOf(60000))
                .setMaxConnectionAttempts(Uint32.ZERO)
                .setBetweenAttemptsTimeoutMillis(Uint16.valueOf(2000))
                .setSleepFactor(Decimal64.valueOf("1.5"))
                .setKeepaliveDelay(Uint32.valueOf(120))
                .setConcurrentRpcLimit(Uint16.ZERO)
                .setActorResponseWaitTime(Uint16.valueOf(5))
                .setLockDatastore(true)
                .build())
            .build();
    }

    public CallHomeSshSessionContextManager createSshSessionContextManager() {
        return new CallHomeSshSessionContextManager() {
            @Override
            public CallHomeSshSessionContext createContext(final String id, final ClientSession clientSession) {
                topology.enableNode(asNode(id, clientSession.getRemoteAddress(), SSH_PROTOCOL));
                final var netconfLayer = netconfLayerMapping.remove(id);
                return netconfLayer == null ? null : new CallHomeSshSessionContext(id, clientSession.getRemoteAddress(),
                    clientSession, netconfLayer.sessionListener, netconfLayer.netconfSessionFuture);
            }

            @Override
            public void remove(String id) {
                super.remove(id);
                topology.disableNode(new NodeId(id));
            }
        };
    }

    public CallHomeTlsSessionContextManager createTlsSessionContextManager(final CallHomeTlsAuthProvider authProvider,
            final CallHomeStatusRecorder statusRecorder) {
        return new CallHomeTlsSessionContextManager(authProvider, statusRecorder) {
            @Override
            public CallHomeTlsSessionContext createContext(final String id, final Channel channel) {
                topology.enableNode(asNode(id, channel.remoteAddress(), TLS_PROTOCOL));
                final var netconfLayer = netconfLayerMapping.remove(id);
                return netconfLayer == null ? null : new CallHomeTlsSessionContext(id, channel,
                    netconfLayer.sessionListener, netconfLayer.netconfSessionFuture());
            }

            @Override
            public void remove(String id) {
                super.remove(id);
                topology.disableNode(new NodeId(id));
            }
        };
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        netconfLayerMapping.forEach((key, value) -> value.netconfSessionFuture.cancel(true));
        netconfLayerMapping.clear();
    }

    private record NetconfLayer(String id, NetconfClientSessionListener sessionListener,
        SettableFuture<NetconfClientSession> netconfSessionFuture) {
    }
}
