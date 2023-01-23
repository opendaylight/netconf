/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.TimedReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.auth.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.SslHandlerFactoryImpl;
import org.opendaylight.netconf.topology.api.NetconfTopology;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology implements NetconfTopology {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    private final NetconfClientDispatcher clientDispatcher;
    private final EventExecutor eventExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfKeystoreAdapter keystoreAdapter;
    private final SchemaResourceManager schemaManager;
    private final BaseNetconfSchemas baseSchemas;

    protected final ScheduledThreadPool keepaliveExecutor;
    protected final ListeningExecutorService processingExecutor;
    protected final DataBroker dataBroker;
    protected final DOMMountPointService mountPointService;
    protected final String topologyId;
    protected final AAAEncryptionService encryptionService;
    protected final HashMap<NodeId, NetconfConnectorDTO> activeConnectors = new HashMap<>();

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                      final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                      final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
                                      final DataBroker dataBroker, final DOMMountPointService mountPointService,
                                      final AAAEncryptionService encryptionService,
                                      final DeviceActionFactory deviceActionFactory,
                                      final BaseNetconfSchemas baseSchemas) {
        this.topologyId = requireNonNull(topologyId);
        this.clientDispatcher = clientDispatcher;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = MoreExecutors.listeningDecorator(processingExecutor.getExecutor());
        this.schemaManager = requireNonNull(schemaManager);
        this.deviceActionFactory = deviceActionFactory;
        this.dataBroker = requireNonNull(dataBroker);
        this.mountPointService = mountPointService;
        this.encryptionService = encryptionService;
        this.baseSchemas = requireNonNull(baseSchemas);

        keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);

        // FIXME: this should be a put(), as we are initializing and will be re-populating the datastore with all the
        //        devices. Whatever has been there before should be nuked to properly re-align lifecycle.
        final var wtx = dataBroker.newWriteOnlyTransaction();
        wtx.merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build(), new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build());
        final var future = wtx.commit();
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Unable to initialize topology {}", topologyId, e);
            throw new IllegalStateException(e);
        }

        LOG.debug("Topology {} initialized", topologyId);
    }

    @Override
    public ListenableFuture<Empty> connectNode(final NodeId nodeId, final Node configNode) {
        LOG.info("Connecting RemoteDevice{{}} , with config {}", nodeId, hideCredentials(configNode));
        return setupConnection(nodeId, configNode);
    }

    /**
     * Hiding of private credentials from node configuration (credentials data is replaced by asterisks).
     *
     * @param nodeConfiguration Node configuration container.
     * @return String representation of node configuration with credentials replaced by asterisks.
     */
    @VisibleForTesting
    public static String hideCredentials(final Node nodeConfiguration) {
        final NetconfNode netconfNodeAugmentation = nodeConfiguration.augmentation(NetconfNode.class);
        final String nodeCredentials = netconfNodeAugmentation.getCredentials().toString();
        final String nodeConfigurationString = nodeConfiguration.toString();
        return nodeConfigurationString.replace(nodeCredentials, "***");
    }

    @Override
    public ListenableFuture<Empty> disconnectNode(final NodeId nodeId) {
        final var nodeName = nodeId.getValue();
        LOG.debug("Disconnecting RemoteDevice{{}}", nodeName);

        final NetconfConnectorDTO connectorDTO = activeConnectors.remove(nodeId);
        if (connectorDTO == null) {
            return Futures.immediateFailedFuture(
                new IllegalStateException("Cannot disconnect " + nodeName + " as it is not connected"));
        }

        connectorDTO.close();
        return Futures.immediateFuture(Empty.value());
    }

    protected ListenableFuture<Empty> setupConnection(final NodeId nodeId, final Node configNode) {
        final NetconfNode netconfNode = configNode.augmentation(NetconfNode.class);
        final NetconfNodeAugmentedOptional nodeOptional = configNode.augmentation(NetconfNodeAugmentedOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        final NetconfConnectorDTO deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, nodeOptional);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig =
                getClientConfig(netconfClientSessionListener, netconfNode, nodeId);
        final ListenableFuture<Empty> future =
                deviceCommunicator.initializeRemoteConnection(clientDispatcher, clientConfig);

        activeConnectors.put(nodeId, deviceCommunicatorDTO);

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(final Empty result) {
                LOG.debug("Connector for {} started succesfully", nodeId.getValue());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Connector for {} failed", nodeId.getValue(), throwable);
                // remove this node from active connectors?
            }
        }, MoreExecutors.directExecutor());

        return future;
    }

    protected NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        final var deviceId = NetconfNodeUtils.toRemoteDeviceId(nodeId, node);
        final long keepaliveDelay = node.requireKeepaliveDelay().toJava();

        final var deviceSalFacade = new NetconfTopologyDeviceSalFacade(deviceId, mountPointService,
            node.requireLockDatastore(), dataBroker);
        // The facade we are going it present to NetconfDevice
        RemoteDeviceHandler salFacade;
        final KeepaliveSalFacade keepAliveFacade;
        if (keepaliveDelay > 0) {
            LOG.info("Adding keepalive facade, for device {}", nodeId);
            salFacade = keepAliveFacade = new KeepaliveSalFacade(deviceId, deviceSalFacade,
                keepaliveExecutor.getExecutor(), keepaliveDelay, node.requireDefaultRequestTimeoutMillis().toJava());
        } else {
            salFacade = deviceSalFacade;
            keepAliveFacade = null;
        }

        // Setup reconnection on empty context, if so configured
        if (nodeOptional != null && nodeOptional.getIgnoreMissingSchemaSources() != null
                && nodeOptional.getIgnoreMissingSchemaSources().getAllowed()) {
            LOG.warn("Ignoring missing schema sources is not currently implemented for {}", deviceId);
        }
        final boolean isNotificationSubscriptionEnabled = nodeOptional != null && nodeOptional.getNotification() != null
                ? true : false;

        final RemoteDevice<NetconfDeviceCommunicator> device;
        final List<SchemaSourceRegistration<?>> yanglibRegistrations;
        if (node.requireSchemaless()) {
            device = new SchemalessNetconfDevice(baseSchemas, deviceId, salFacade);
            yanglibRegistrations = List.of();
        } else {
            final SchemaResourcesDTO resources = schemaManager.getSchemaResources(node.getSchemaCacheDirectory(),
                nodeId.getValue());
            device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(node.requireReconnectOnChangedSchema())
                .setSchemaResourcesDTO(resources)
                .setGlobalProcessingExecutor(processingExecutor)
                .setId(deviceId)
                .setSalFacade(salFacade)
                .setDeviceActionFactory(deviceActionFactory)
                .setBaseSchemas(baseSchemas)
                .setNotificationStream(isNotificationSubscriptionEnabled ? nodeOptional.getNotification()
                        .getStreamName() : "NETCONF")
                .setNotificationSubscribe(isNotificationSubscriptionEnabled ? nodeOptional.getNotification()
                        .getSubscribe() : false)
                .build();
            yanglibRegistrations = registerDeviceSchemaSources(deviceId, node, resources);
        }

        final int rpcMessageLimit = node.requireConcurrentRpcLimit().toJava();
        if (rpcMessageLimit < 1) {
            LOG.info("Concurrent rpc limit is smaller than 1, no limit will be enforced for device {}", deviceId);
        }

        final var netconfDeviceCommunicator = new NetconfDeviceCommunicator(deviceId, device, rpcMessageLimit,
            NetconfNodeUtils.extractUserCapabilities(node));

        if (keepAliveFacade != null) {
            keepAliveFacade.setListener(netconfDeviceCommunicator);
        }

        return new NetconfConnectorDTO(netconfDeviceCommunicator, salFacade, yanglibRegistrations);
    }

    private static List<SchemaSourceRegistration<?>> registerDeviceSchemaSources(final RemoteDeviceId remoteDeviceId,
            final NetconfNode node, final SchemaResourcesDTO resources) {
        final var yangLibrary = node.getYangLibrary();
        if (yangLibrary != null) {
            final Uri uri = yangLibrary.getYangLibraryUrl();
            if (uri != null) {
                final List<SchemaSourceRegistration<?>> registrations = new ArrayList<>();
                final String yangLibURL = uri.getValue();
                final SchemaSourceRegistry schemaRegistry = resources.getSchemaRegistry();

                // pre register yang library sources as fallback schemas to schema registry
                final LibraryModulesSchemas schemas;
                final String yangLibUsername = yangLibrary.getUsername();
                final String yangLigPassword = yangLibrary.getPassword();
                if (yangLibUsername != null && yangLigPassword != null) {
                    schemas = LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword);
                } else {
                    schemas = LibraryModulesSchemas.create(yangLibURL);
                }

                for (final Map.Entry<SourceIdentifier, URL> entry : schemas.getAvailableModels().entrySet()) {
                    registrations.add(schemaRegistry.registerSchemaSource(new YangLibrarySchemaYangSourceProvider(
                        remoteDeviceId, schemas.getAvailableModels()),
                        PotentialSchemaSource.create(entry.getKey(), YangTextSchemaSource.class,
                            PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
                return List.copyOf(registrations);
            }
        }

        return List.of();
    }

    public NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener,
                                                                  final NetconfNode node, final NodeId nodeId) {
        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor,
                node.requireMaxConnectionAttempts().toJava(), node.requireBetweenAttemptsTimeoutMillis().toJava(),
                node.requireSleepFactor().decimalValue());
        final NetconfReconnectingClientConfigurationBuilder reconnectingClientConfigurationBuilder;
        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol.getName() == Name.TLS) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                .withSslHandlerFactory(new SslHandlerFactoryImpl(keystoreAdapter, protocol.getSpecification()))
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS);
        } else {
            throw new IllegalStateException("Unsupported protocol type: " + protocol.getName());
        }

        if (node.getOdlHelloMessageCapabilities() != null) {
            reconnectingClientConfigurationBuilder.withOdlHelloCapabilities(
                    Lists.newArrayList(node.getOdlHelloMessageCapabilities().getCapability()));
        }

        return reconnectingClientConfigurationBuilder
                .withName(nodeId.getValue())
                .withAddress(NetconfNodeUtils.toInetSocketAddress(node))
                .withConnectionTimeoutMillis(node.requireConnectionTimeoutMillis().toJava())
                .withReconnectStrategy(sf.createReconnectStrategy())
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    private AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials
                instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev221225
                    .credentials.credentials.LoginPassword loginPassword) {
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPwUnencrypted unencrypted) {
            final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        }
        if (credentials instanceof KeyAuth keyAuth) {
            final var keyPair = keyAuth.getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(),
                    keystoreAdapter, encryptionService);
        }
        throw new IllegalStateException("Unsupported credential type: " + credentials.getClass());
    }
}
