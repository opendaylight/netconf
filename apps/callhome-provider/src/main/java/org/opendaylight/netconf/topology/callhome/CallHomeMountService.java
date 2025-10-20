/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeHandler;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.parameters.ProtocolBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.NetconfCallhomeServer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.AllowedDevices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev240129.netconf.callhome.server.allowed.devices.Device;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Service is responsible for call-home to topology integration.
 *
 * <p>To manage remote device as a topology node the topology component (based on
 * {@link AbstractNetconfTopology AbstractNetconfTopology}) creates an instance
 * of {@link NetconfNodeHandler NetconfNodeHandler} based on provided
 * {@link Node}.
 *
 * <p>The mentioned NetconfNodeHandler initializes connection to remote device via sequence of following actions (see
 * {@link AbstractNetconfTopology#ensureNode(Node) ensureNode(Node)} and
 * {@link NetconfNodeHandler#lockedConnect() connect()}):
 *
 * <ul>
 *     <li>Builds an instance of {@link NetconfDeviceCommunicator
 *     NetconfDeviceCommunicator} implementation of {@link NetconfClientSessionListener} which is used to check the
 *     NETCONF session state and communicate with device using NETCONF protocol </li>
 *     <li>Builds Netconf client configuration using provided {@link NetconfClientConfigurationBuilderFactory}</li>
 *     <li>Builds Netconf client using configuration composed and triggers connection</li>
 * </ul>
 *
 * <p>This service uses custom implementations of {@link NetconfClientConfigurationBuilderFactory} and
 * {@link NetconfClientFactory} in order to capture the instance of {@link NetconfClientSessionListener} from topology
 * component which is required to establish NETCONF layer. See {@link #createClientConfigurationBuilderFactory()}
 * and {@link #createClientFactory()}.
 *
 * <p>Following sequence of actions is performed when incoming connection is mapped to topology node:
 * <ul>
 *     <li>When incoming connection is identified the {@link CallHomeSshSessionContext} instance expected to be created.
 *     The createContext() method is invoked within protocol associated {@link CallHomeSessionContextManager} --
 *     see {@link #createSshSessionContextManager()} and
 *     {@link #createTlsSessionContextManager(CallHomeTlsAuthProvider, CallHomeStatusRecorder)}</li>
 *     <li>Due to both {@link NetconfClientSessionListener} and {@link SettableFuture} are required to build session
 *     context the {@link CallHomeTopology#enableNode(Node)} (Node)} is called using synthetic {@link Node} instance
 *     composed via {@link #asNode(String, SocketAddress, Protocol)}. This triggers Netconf client construct/connect
 *     logic (as explained above) resulting captured object placed into {@link #netconfLayerMapping}.</li>
 *     <li>Accepted instance of {@link NetconfClientSessionListener} is used to establish Netconf layer --
 *     see {@link CallHomeTransportChannelListener}</li>
 *     <li>Accepted instance of {@link SettableFuture} (representing connection to remote device) is used to
 *     signal connection state to topology component</li>
 * </ul>
 */
@Component(service = CallHomeMountService.class, immediate = true,
    configurationPid = "org.opendaylight.netconf.topology.callhome")
@Designate(ocd = CallHomeMountService.Configuration.class)
@Singleton
public final class CallHomeMountService implements AutoCloseable {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = "Host address used for Call-Home server.")
        String host() default "0.0.0.0";

        @AttributeDefinition(description = "Port used for Call-Home SSH server.", min = "1", max = "65535")
        int ssh$_$port() default 4334;

        @AttributeDefinition(description = "Port used for Call-Home TLS server.", min = "1", max = "65535")
        int tls$_$port() default 4335;

        @AttributeDefinition(description = "Connection timeout for Call-Home server.")
        int connection$_$timeout$_$millis() default 10_000;

        @AttributeDefinition(description = "Maximum number of connections for Call-Home server.")
        int max$_$connections() default 64;

        @AttributeDefinition(description = "Delay between keep alive messages in seconds.", min = "0")
        int keep$_$alive$_$delay() default 120;

        @AttributeDefinition(description = "Timeout for blocking operations within transactions.", min = "0")
        int request$_$timeout$_$millis() default 60000;

        @AttributeDefinition(description = "Initial timeout in milliseconds to wait between connection attempts.",
            min = "0", max = "65535")
        int min$_$backoff$_$millis() default 2000;

        @AttributeDefinition(description = "Maximum timeout in milliseconds to wait between connection attempts.",
            min = "0")
        int max$_$backoff$_$millis() default 1800000;

        @AttributeDefinition(description = """
            Multiplier for backoff timeout. The backoff will be multiplied by this
            value with every additional attempt.""", min = "0")
        double backoff$_$multiplier() default 1.5;

        @AttributeDefinition(description = """
            Range of backoff randomization. The backoff will be multiplied by a
            random number in the range (1 - backoff-jitter, 1 + backoff-jitter). Backoff-jitter must be
            in the range (0, 0.5).""", min = "0", max = "0.5")
        double backoff$_$jitter() default 0.1;

        @AttributeDefinition(description = """
            Limit of concurrent messages that can be send before reply messages
            are received.""", min = "0", max = "65535")
        int concurrent$_$rpc$_$limit() default 0;

        @AttributeDefinition(description = "Maximum number of connection retries", min = "0")
        int max$_$connection$_$attempts() default 0;

        @AttributeDefinition(description =
            "Enables connection of legacy NETCONF devices that are not schema-based and implement just RFC 4741.")
        boolean schemaless() default false;

        @AttributeDefinition(description = "Time that slave actor will wait for response from master.",
            min = "1", max = "65535")
        int actor$_$response$_$wait$_$time() default 5;

        @AttributeDefinition(description =
            "The operation allows the client to lock the entire configuration datastore system of a device.")
        boolean lock$_$datastore() default true;

        @AttributeDefinition(description = """
            If true, the connector would auto disconnect/reconnect when schemas are
            changed in the remote device.""")
        boolean reconnect$_$on$_$changed$_$schema() default false;
    }

    private static final Protocol SSH_PROTOCOL = new ProtocolBuilder().setName(Protocol.Name.SSH).build();
    private static final Protocol TLS_PROTOCOL = new ProtocolBuilder().setName(Protocol.Name.TLS).build();
    private static final DataObjectReference<Device> DEVICE_IDENTIFIER = DataObjectReference
        .builder(NetconfCallhomeServer.class).child(AllowedDevices.class)
        .child(Device.class).build();

    private final Map<String, NetconfLayer> netconfLayerMapping = new ConcurrentHashMap<>();
    private final CallHomeTopology topology;
    private final Configuration config;
    private final Registration allowedDevicesReg;

    @Activate
    @Inject
    public CallHomeMountService(
            final @Reference NetconfTimer timer,
            final @Reference NetconfTopologySchemaAssembler schemaAssembler,
            final @Reference SchemaResourceManager schemaRepositoryProvider,
            final @Reference BaseNetconfSchemaProvider baseSchemaProvider,
            final @Reference DataBroker dataBroker,
            final @Reference DOMMountPointService mountService,
            final @Reference DeviceActionFactory deviceActionFactory, final Configuration config) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, timer, schemaAssembler, schemaRepositoryProvider,
            baseSchemaProvider, dataBroker, mountService, deviceActionFactory, config);
    }

    public CallHomeMountService(final String topologyId, final NetconfTimer timer,
            final NetconfTopologySchemaAssembler schemaAssembler, final SchemaResourceManager schemaRepositoryProvider,
            final BaseNetconfSchemaProvider baseSchemaProvider, final DataBroker dataBroker,
            final DOMMountPointService mountService, final DeviceActionFactory deviceActionFactory,
            final Configuration config) {
        this.config = config;
        allowedDevicesReg = dataBroker.registerTreeChangeListener(LogicalDatastoreType.CONFIGURATION, DEVICE_IDENTIFIER,
            this::onAllowedDevicesChanged);
        final var clientConfBuilderFactory = createClientConfigurationBuilderFactory();
        final var clientFactory = createClientFactory();
        topology = new CallHomeTopology(topologyId, clientFactory, timer, schemaAssembler,
            schemaRepositoryProvider, dataBroker, mountService, clientConfBuilderFactory,
            baseSchemaProvider, deviceActionFactory);
    }

    @VisibleForTesting
    CallHomeMountService(final CallHomeTopology topology, final Configuration config) {
        this.topology = topology;
        this.config = config;
        this.allowedDevicesReg = () -> {
            // do nothing
        };
    }

    @VisibleForTesting
    NetconfClientConfigurationBuilderFactory createClientConfigurationBuilderFactory() {
        // use minimal configuration, only id and session listener are used
        return (nodeId, node) -> NetconfClientConfigurationBuilder.create()
            .withName(nodeId.getValue())
            .withConnectionTimeoutMillis(config.connection$_$timeout$_$millis())
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

    private Node asNode(final String id, final SocketAddress socketAddress, final Protocol protocol) {
        final var nodeAddress = socketAddress instanceof InetSocketAddress inetSocketAddress
            ? inetSocketAddress : new InetSocketAddress("0.0.0.0", 0);
        // construct synthetic Node object with minimal required parameters
        return new NodeBuilder()
            .setNodeId(new NodeId(id))
            .addAugmentation(new NetconfNodeAugmentBuilder()
                .setNetconfNode(new NetconfNodeBuilder()
                    .setHost(new Host(IetfInetUtil.ipAddressFor(nodeAddress.getAddress())))
                    .setPort(new PortNumber(Uint16.valueOf(nodeAddress.getPort())))
                    .setTcpOnly(false)
                    .setProtocol(protocol)
                    // below parameters are required for NetconfNodeHandler
                    .setSchemaless(config.schemaless())
                    .setReconnectOnChangedSchema(config.reconnect$_$on$_$changed$_$schema())
                    .setConnectionTimeoutMillis(Uint32.valueOf(config.connection$_$timeout$_$millis()))
                    .setDefaultRequestTimeoutMillis(Uint32.valueOf(config.request$_$timeout$_$millis()))
                    .setMaxConnectionAttempts(Uint32.valueOf(config.max$_$connection$_$attempts()))
                    .setMinBackoffMillis(Uint16.valueOf(config.min$_$backoff$_$millis()))
                    .setMaxBackoffMillis(Uint32.valueOf(config.max$_$backoff$_$millis()))
                    .setBackoffMultiplier(Decimal64.valueOf(config.backoff$_$multiplier(), RoundingMode.HALF_DOWN))
                    .setBackoffJitter(Decimal64.valueOf(config.backoff$_$jitter(), RoundingMode.HALF_DOWN))
                    .setKeepaliveDelay(Uint32.valueOf(config.keep$_$alive$_$delay()))
                    .setConcurrentRpcLimit(Uint16.valueOf(config.concurrent$_$rpc$_$limit()))
                    .setActorResponseWaitTime(Uint16.valueOf(config.actor$_$response$_$wait$_$time()))
                    .setLockDatastore(config.lock$_$datastore())
                    .setCredentials(new LoginPwUnencryptedBuilder().build())
                    .build())
                .build())
            .build();
    }

    public CallHomeSshSessionContextManager createSshSessionContextManager() {
        return new CallHomeSshSessionContextManager() {
            @Override
            public CallHomeSshSessionContext createContext(final String id, final ClientSession clientSession) {
                final var remoteAddr = clientSession.getRemoteAddress();
                topology.enableNode(asNode(id, remoteAddr, SSH_PROTOCOL));
                final var netconfLayer = netconfLayerMapping.remove(id);
                return netconfLayer == null ? null : new CallHomeSshSessionContext(id, remoteAddr, clientSession,
                    netconfLayer.sessionListener, netconfLayer.netconfSessionFuture);
            }

            @Override
            public void remove(final String id) {
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
            public void remove(final String id) {
                super.remove(id);
                topology.disableNode(new NodeId(id));
            }
        };
    }

    @VisibleForTesting
    void onAllowedDevicesChanged(final List<DataTreeModification<Device>> changes) {
        for (final var change : changes) {
            final var deletedDevice  = change.getRootNode().dataBefore();
            if (deletedDevice  != null) {
                topology.disableNode(new NodeId(deletedDevice.getUniqueId()));
            }
        }
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        netconfLayerMapping.forEach((key, value) -> value.netconfSessionFuture.cancel(true));
        netconfLayerMapping.clear();
        allowedDevicesReg.close();
    }

    private record NetconfLayer(String id, NetconfClientSessionListener sessionListener,
        SettableFuture<NetconfClientSession> netconfSessionFuture) {
    }
}
