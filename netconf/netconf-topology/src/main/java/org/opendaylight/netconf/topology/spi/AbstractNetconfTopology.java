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
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfDeviceCommunicatorFactory;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfDeviceCommunicatorInitializerFactory;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.RemoteDevice;
import org.opendaylight.netconf.nativ.netconf.communicator.UserPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.NetconfDeviceCapabilities;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.topology.api.NetconfTopology;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.schema.storage.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology implements NetconfTopology {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    protected static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    protected static final int DEFAULT_KEEPALIVE_DELAY = 0;
    protected static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;

    private final EventExecutor eventExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final SchemaResourceManager schemaManager;
    private final BaseNetconfSchemas baseSchemas;

    protected final ScheduledThreadPool keepaliveExecutor;
    protected final ListeningExecutorService processingExecutor;
    protected final DataBroker dataBroker;
    protected final DOMMountPointService mountPointService;
    protected final String topologyId;
    protected String privateKeyPath;
    protected String privateKeyPassphrase;
    protected final HashMap<NodeId, NetconfConnectorDTO> activeConnectors = new HashMap<>();

    private final NetconfDeviceCommunicatorFactory netconfDeviceCommunicatorFactory;

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientDispatcher dispatcher,
            final NetconfDeviceCommunicatorInitializerFactory netconfDeviceCommunicatorInitFactory,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final DeviceActionFactory deviceActionFactory,
            final BaseNetconfSchemas baseSchemas) {
        this.topologyId = topologyId;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = MoreExecutors.listeningDecorator(processingExecutor.getExecutor());
        this.schemaManager = requireNonNull(schemaManager);
        this.deviceActionFactory = deviceActionFactory;
        this.dataBroker = dataBroker;
        this.mountPointService = mountPointService;
        this.baseSchemas = requireNonNull(baseSchemas);
        this.netconfDeviceCommunicatorFactory = netconfDeviceCommunicatorInitFactory.init(dispatcher);
        new NetconfKeystoreAdapter(dataBroker, netconfDeviceCommunicatorFactory.getKeystore());
    }

    @Override
    public ListenableFuture<NetconfDeviceCapabilities> connectNode(final NodeId nodeId, final Node configNode) {
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
    public ListenableFuture<Void> disconnectNode(final NodeId nodeId) {
        LOG.debug("Disconnecting RemoteDevice{{}}", nodeId.getValue());

        final NetconfConnectorDTO connectorDTO = activeConnectors.remove(nodeId);
        if (connectorDTO == null) {
            return Futures.immediateFailedFuture(
                new IllegalStateException("Unable to disconnect device that is not connected"));
        }

        connectorDTO.close();
        return Futures.immediateFuture(null);
    }

    protected ListenableFuture<NetconfDeviceCapabilities> setupConnection(final NodeId nodeId,
                                                                          final Node configNode) {
        final NetconfNode netconfNode = configNode.augmentation(NetconfNode.class);
        final NetconfNodeAugmentedOptional nodeOptional = configNode.augmentation(NetconfNodeAugmentedOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        final NetconfConnectorDTO deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, nodeOptional);
        final NativeNetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator.initializeRemoteConnection();

        activeConnectors.put(nodeId, deviceCommunicatorDTO);

        Futures.addCallback(future, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(final NetconfDeviceCapabilities result) {
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

    protected NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node) {
        return createDeviceCommunicator(nodeId, node, null);
    }

    protected NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
            final NetconfNodeAugmentedOptional nodeOptional) {
        //setup default values since default value is not supported in mdsal
        final long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null
                ? DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis().toJava();
        final long keepaliveDelay = node.getKeepaliveDelay() == null
                ? DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay().toJava();

        final IpAddress ipAddress = node.getHost().getIpAddress();
        final InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null
                ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue().toJava());
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId(nodeId.getValue(), address);

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade = createSalFacade(remoteDeviceId);

        if (keepaliveDelay > 0) {
            LOG.warn("Adding keepalive facade, for device {}", nodeId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade, this.keepaliveExecutor.getExecutor(),
                    keepaliveDelay, defaultRequestTimeoutMillis);
        }

        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> device;
        final List<SchemaSourceRegistration<?>> yanglibRegistrations;
        if (node.isSchemaless()) {
            device = new SchemalessNetconfDevice(baseSchemas, remoteDeviceId, salFacade);
            yanglibRegistrations = List.of();
        } else {
            final boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null
                ? DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

            final SchemaResourcesDTO resources = schemaManager.getSchemaResources(node, nodeId.getValue());
            device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(reconnectOnChangedSchema)
                .setSchemaResourcesDTO(resources)
                .setGlobalProcessingExecutor(this.processingExecutor)
                .setId(remoteDeviceId)
                .setSalFacade(salFacade)
                .setNode(node)
                .setEventExecutor(eventExecutor)
                .setNodeOptional(nodeOptional)
                .setDeviceActionFactory(deviceActionFactory)
                .setBaseSchemas(baseSchemas)
                .build();
            yanglibRegistrations = registerDeviceSchemaSources(remoteDeviceId, nodeId, node, resources);
        }

        final Optional<UserPreferences> userCapabilities = getUserCapabilities(node);

        final NativeNetconfDeviceCommunicator netconfDeviceCommunicator =
                userCapabilities.isPresent() ? netconfDeviceCommunicatorFactory.create(remoteDeviceId, device,
                        userCapabilities.get(), node)
                        : netconfDeviceCommunicatorFactory.create(remoteDeviceId, device, node);

        if (salFacade instanceof KeepaliveSalFacade) {
            ((KeepaliveSalFacade)salFacade).setListener(netconfDeviceCommunicator);
        }

        return new NetconfConnectorDTO(netconfDeviceCommunicator, salFacade, yanglibRegistrations);
    }

    private List<SchemaSourceRegistration<?>> registerDeviceSchemaSources(final RemoteDeviceId remoteDeviceId,
            final NodeId nodeId, final NetconfNode node, final SchemaResourcesDTO resources) {
        final YangLibrary yangLibrary = node.getYangLibrary();
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

    /**
     * Sets the private key path from location specified in configuration file using blueprint.
     */
    public void setPrivateKeyPath(final String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    /**
     * Sets the private key passphrase from location specified in configuration file using blueprint.
     */
    public void setPrivateKeyPassphrase(final String privateKeyPassphrase) {
        this.privateKeyPassphrase = privateKeyPassphrase;
    }

    protected abstract RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(RemoteDeviceId id);

    private static Optional<UserPreferences> getUserCapabilities(final NetconfNode node) {
        // if none of yang-module-capabilities or non-module-capabilities is specified
        // just return absent
        if (node.getYangModuleCapabilities() == null && node.getNonModuleCapabilities() == null) {
            return Optional.empty();
        }

        final List<String> capabilities = new ArrayList<>();

        boolean overrideYangModuleCaps = false;
        if (node.getYangModuleCapabilities() != null) {
            capabilities.addAll(node.getYangModuleCapabilities().getCapability());
            overrideYangModuleCaps = node.getYangModuleCapabilities().isOverride();
        }

        //non-module capabilities should not exist in yang module capabilities
        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(capabilities);
        Preconditions.checkState(netconfSessionPreferences.getNonModuleCaps().isEmpty(),
                "List yang-module-capabilities/capability should contain only module based capabilities. "
                        + "Non-module capabilities used: " + netconfSessionPreferences.getNonModuleCaps());

        boolean overrideNonModuleCaps = false;
        if (node.getNonModuleCapabilities() != null) {
            capabilities.addAll(node.getNonModuleCapabilities().getCapability());
            overrideNonModuleCaps = node.getNonModuleCapabilities().isOverride();
        }

        return Optional.of(new UserPreferences(NetconfSessionPreferences
            .fromStrings(capabilities, CapabilityOrigin.UserDefined), overrideYangModuleCaps, overrideNonModuleCaps));
    }

    protected static class NetconfConnectorDTO implements AutoCloseable {
        private final List<SchemaSourceRegistration<?>> yanglibRegistrations;
        private final NativeNetconfDeviceCommunicator communicator;
        private final RemoteDeviceHandler<NetconfSessionPreferences> facade;

        public NetconfConnectorDTO(
                final NativeNetconfDeviceCommunicator communicator,
                final RemoteDeviceHandler<NetconfSessionPreferences> facade,
                final List<SchemaSourceRegistration<?>> yanglibRegistrations) {
            this.communicator = communicator;
            this.facade = facade;
            this.yanglibRegistrations = yanglibRegistrations;
        }

        public NativeNetconfDeviceCommunicator getCommunicator() {
            return communicator;
        }

        public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
            return facade;
        }

        @Override
        public void close() {
            communicator.close();
            facade.close();
            yanglibRegistrations.forEach(SchemaSourceRegistration::close);
        }
    }
}
