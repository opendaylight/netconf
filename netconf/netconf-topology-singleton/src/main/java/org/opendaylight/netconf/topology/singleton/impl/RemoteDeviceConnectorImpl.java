/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.util.Timeout;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.listener.UserPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfConnectorDTO;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;
import org.opendaylight.protocol.framework.TimedReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.util.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteDeviceConnectorImpl implements RemoteDeviceConnector {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDeviceConnectorImpl.class);

    /**
     * Keeps track of initialized Schema resources.  A Map is maintained in which the key represents the name
     * of the schema cache directory, and the value is a corresponding <code>SchemaResourcesDTO</code>.  The
     * <code>SchemaResourcesDTO</code> is essentially a container that allows for the extraction of the
     * <code>SchemaRegistry</code> and <code>SchemaContextFactory</code> which should be used for a particular
     * Netconf mount.  Access to <code>schemaResourcesDTOs</code> should be surrounded by appropriate
     * synchronization locks.
     */
    private static final Map<String, NetconfDevice.SchemaResourcesDTO> schemaResourcesDTOs = new HashMap<>();
    private final Timeout actorResponseWaitTime;

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.
    static {
        schemaResourcesDTOs.put(NetconfTopologyUtils.DEFAULT_CACHE_DIRECTORY,
                new NetconfDevice.SchemaResourcesDTO(NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY,
                        NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY,
                        NetconfTopologyUtils.DEFAULT_SCHEMA_CONTEXT_FACTORY,
                        new NetconfStateSchemasResolverImpl()));
        NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(NetconfTopologyUtils.DEFAULT_CACHE);
        NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(
                TextToASTTransformer.create(NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY,
                        NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY));
    }

    private final NetconfTopologySetup netconfTopologyDeviceSetup;
    private final RemoteDeviceId remoteDeviceId;
    private SchemaSourceRegistry schemaRegistry = NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;
    private final SchemaRepository schemaRepository = NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;
    private SchemaContextFactory schemaContextFactory = NetconfTopologyUtils.DEFAULT_SCHEMA_CONTEXT_FACTORY;
    private NetconfConnectorDTO deviceCommunicatorDTO;

    public RemoteDeviceConnectorImpl(final NetconfTopologySetup netconfTopologyDeviceSetup,
                                     final RemoteDeviceId remoteDeviceId, final Timeout actorResponseWaitTime) {

        this.netconfTopologyDeviceSetup = Preconditions.checkNotNull(netconfTopologyDeviceSetup);
        this.remoteDeviceId = remoteDeviceId;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public void startRemoteDeviceConnection(final ActorRef deviceContextActorRef) {

        final NetconfNode netconfNode = netconfTopologyDeviceSetup.getNode().getAugmentation(NetconfNode.class);
        final NodeId nodeId = netconfTopologyDeviceSetup.getNode().getNodeId();
        Preconditions.checkNotNull(netconfNode.getHost());
        Preconditions.checkNotNull(netconfNode.getPort());
        Preconditions.checkNotNull(netconfNode.isTcpOnly());

        this.deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, deviceContextActorRef);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig =
                getClientConfig(netconfClientSessionListener, netconfNode);
        final ListenableFuture<NetconfDeviceCapabilities> future = deviceCommunicator
                .initializeRemoteConnection(netconfTopologyDeviceSetup.getNetconfClientDispatcher(), clientConfig);

        Futures.addCallback(future, new FutureCallback<NetconfDeviceCapabilities>() {
            @Override
            public void onSuccess(final NetconfDeviceCapabilities result) {
                LOG.debug("{}: Connector started successfully", remoteDeviceId);
            }

            @Override
            public void onFailure(@Nullable final Throwable throwable) {
                LOG.error("{}: Connector failed, {}", remoteDeviceId, throwable);
            }
        });
    }

    @Override
    public void stopRemoteDeviceConnection() {
        Preconditions.checkNotNull(deviceCommunicatorDTO, remoteDeviceId + ": Device communicator was not created.");
        try {
            deviceCommunicatorDTO.close();
        } catch (final Exception e) {
            LOG.error("{}: Error at closing device communicator.", remoteDeviceId, e);
        }
    }

    @VisibleForTesting
    NetconfConnectorDTO createDeviceCommunicator(final NodeId nodeId, final NetconfNode node,
                                                 final ActorRef deviceContextActorRef) {
        //setup default values since default value is not supported in mdsal
        final Long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis();
        final Long keepaliveDelay = node.getKeepaliveDelay() == null
                ? NetconfTopologyUtils.DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay();
        final Boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null
                ? NetconfTopologyUtils.DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade = new MasterSalFacade(remoteDeviceId,
                netconfTopologyDeviceSetup.getDomBroker(), netconfTopologyDeviceSetup.getBindingAwareBroker(),
                netconfTopologyDeviceSetup.getActorSystem(), deviceContextActorRef, actorResponseWaitTime);
        if (keepaliveDelay > 0) {
            LOG.info("{}: Adding keepalive facade.", remoteDeviceId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade,
                    netconfTopologyDeviceSetup.getKeepaliveExecutor().getExecutor(), keepaliveDelay,
                    defaultRequestTimeoutMillis);
        }

        // pre register yang library sources as fallback schemas to schema registry
        final List<SchemaSourceRegistration<YangTextSchemaSource>> registeredYangLibSources = Lists.newArrayList();
        if (node.getYangLibrary() != null) {
            final String yangLibURL = node.getYangLibrary().getYangLibraryUrl().getValue();
            final String yangLibUsername = node.getYangLibrary().getUsername();
            final String yangLigPassword = node.getYangLibrary().getPassword();

            final LibraryModulesSchemas libraryModulesSchemas;
            if (yangLibURL != null) {
                if (yangLibUsername != null && yangLigPassword != null) {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword);
                } else {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL);
                }

                for (final Map.Entry<SourceIdentifier, URL> sourceIdentifierURLEntry :
                        libraryModulesSchemas.getAvailableModels().entrySet()) {
                    registeredYangLibSources
                            .add(schemaRegistry.registerSchemaSource(
                                    new YangLibrarySchemaYangSourceProvider(remoteDeviceId,
                                            libraryModulesSchemas.getAvailableModels()),
                                    PotentialSchemaSource
                                            .create(sourceIdentifierURLEntry.getKey(), YangTextSchemaSource.class,
                                                    PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
            }
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = setupSchemaCacheDTO(nodeId, node);
        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> device;
        if (node.isSchemaless()) {
            device = new SchemalessNetconfDevice(remoteDeviceId, salFacade);
        } else {
            device = new NetconfDeviceBuilder()
                    .setReconnectOnSchemasChange(reconnectOnChangedSchema)
                    .setSchemaResourcesDTO(schemaResourcesDTO)
                    .setGlobalProcessingExecutor(netconfTopologyDeviceSetup.getProcessingExecutor().getExecutor())
                    .setId(remoteDeviceId)
                    .setSalFacade(salFacade)
                    .build();
        }

        final Optional<NetconfSessionPreferences> userCapabilities = getUserCapabilities(node);
        final int rpcMessageLimit =
                node.getConcurrentRpcLimit() == null
                        ? NetconfTopologyUtils.DEFAULT_CONCURRENT_RPC_LIMIT : node.getConcurrentRpcLimit();

        if (rpcMessageLimit < 1) {
            LOG.info("{}: Concurrent rpc limit is smaller than 1, no limit will be enforced.", remoteDeviceId);
        }

        return new NetconfConnectorDTO(
                userCapabilities.isPresent()
                        ? new NetconfDeviceCommunicator(
                        remoteDeviceId, device, new UserPreferences(userCapabilities.get(),
                        node.getYangModuleCapabilities().isOverride(), node.getNonModuleCapabilities().isOverride()),
                        rpcMessageLimit)
                        : new NetconfDeviceCommunicator(remoteDeviceId, device, rpcMessageLimit), salFacade);
    }

    private Optional<NetconfSessionPreferences> getUserCapabilities(final NetconfNode node) {
        if (node.getYangModuleCapabilities() == null && node.getNonModuleCapabilities() == null) {
            return Optional.empty();
        }
        final List<String> capabilities = new ArrayList<>();

        if (node.getYangModuleCapabilities() != null) {
            capabilities.addAll(node.getYangModuleCapabilities().getCapability());
        }

        //non-module capabilities should not exist in yang module capabilities
        final NetconfSessionPreferences netconfSessionPreferences = NetconfSessionPreferences.fromStrings(capabilities);
        Preconditions.checkState(netconfSessionPreferences.getNonModuleCaps().isEmpty(), "List yang-module-capabilities/capability " +
                "should contain only module based capabilities. Non-module capabilities used: " +
                netconfSessionPreferences.getNonModuleCaps());

        if (node.getNonModuleCapabilities() != null) {
            capabilities.addAll(node.getNonModuleCapabilities().getCapability());
        }

        return Optional.of(NetconfSessionPreferences.fromStrings(capabilities, CapabilityOrigin.UserDefined));
    }

    private NetconfDevice.SchemaResourcesDTO setupSchemaCacheDTO(final NodeId nodeId, final NetconfNode node) {
        // Setup information related to the SchemaRegistry, SchemaResourceFactory, etc.
        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = null;
        final String moduleSchemaCacheDirectory = node.getSchemaCacheDirectory();
        // Only checks to ensure the String is not empty or null;  further checks related to directory accessibility
        // and file permissions are handled during the FilesystemSchemaSourceCache initialization.
        if (!Strings.isNullOrEmpty(moduleSchemaCacheDirectory)) {
            // If a custom schema cache directory is specified, create the backing DTO; otherwise, the SchemaRegistry
            // and SchemaContextFactory remain the default values.
            if (!moduleSchemaCacheDirectory.equals(NetconfTopologyUtils.DEFAULT_CACHE_DIRECTORY)) {
                // Multiple modules may be created at once;  synchronize to avoid issues with data consistency among
                // threads.
                synchronized (schemaResourcesDTOs) {
                    // Look for the cached DTO to reuse SchemaRegistry and SchemaContextFactory variables if
                    // they already exist
                    schemaResourcesDTO = schemaResourcesDTOs.get(moduleSchemaCacheDirectory);
                    if (schemaResourcesDTO == null) {
                        schemaResourcesDTO = createSchemaResourcesDTO(moduleSchemaCacheDirectory);
                        schemaResourcesDTO.getSchemaRegistry().registerSchemaSourceListener(
                                TextToASTTransformer.create((SchemaRepository) schemaResourcesDTO.getSchemaRegistry(),
                                        schemaResourcesDTO.getSchemaRegistry())
                        );
                        schemaResourcesDTOs.put(moduleSchemaCacheDirectory, schemaResourcesDTO);
                    }
                }
                LOG.info("{} : netconf connector will use schema cache directory {} instead of {}",
                        remoteDeviceId, moduleSchemaCacheDirectory, NetconfTopologyUtils.DEFAULT_CACHE_DIRECTORY);
            }
        } else {
            LOG.info("{} : using the default directory {}",
                    remoteDeviceId, NetconfTopologyUtils.QUALIFIED_DEFAULT_CACHE_DIRECTORY);
        }

        if (schemaResourcesDTO == null) {
            schemaResourcesDTO =
                    new NetconfDevice.SchemaResourcesDTO(schemaRegistry, schemaRepository, schemaContextFactory,
                            new NetconfStateSchemasResolverImpl());
        }

        return schemaResourcesDTO;
    }

    /**
     * Creates the backing Schema classes for a particular directory.
     *
     * @param moduleSchemaCacheDirectory The string directory relative to "cache"
     * @return A DTO containing the Schema classes for the Netconf mount.
     */
    private NetconfDevice.SchemaResourcesDTO createSchemaResourcesDTO(final String moduleSchemaCacheDirectory) {
        final SharedSchemaRepository repository = new SharedSchemaRepository(moduleSchemaCacheDirectory);
        final SchemaContextFactory schemaContextFactory
                = repository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
        this.schemaRegistry = repository;
        this.schemaContextFactory = schemaContextFactory;

        final FilesystemSchemaSourceCache<YangTextSchemaSource> deviceCache =
                createDeviceFilesystemCache(moduleSchemaCacheDirectory);
        repository.registerSchemaSourceListener(deviceCache);
        return new NetconfDevice.SchemaResourcesDTO(repository, repository, schemaContextFactory,
                new NetconfStateSchemasResolverImpl());
    }

    /**
     * Creates a <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory.
     *
     * @param schemaCacheDirectory The custom cache directory relative to "cache"
     * @return A <code>FilesystemSchemaSourceCache</code> for the custom schema cache directory
     */
    private FilesystemSchemaSourceCache<YangTextSchemaSource> createDeviceFilesystemCache(
            final String schemaCacheDirectory) {
        final String relativeSchemaCacheDirectory =
                NetconfTopologyUtils.CACHE_DIRECTORY + File.separator + schemaCacheDirectory;
        return new FilesystemSchemaSourceCache<>(schemaRegistry, YangTextSchemaSource.class,
                new File(relativeSchemaCacheDirectory));
    }

    //TODO: duplicate code
    private InetSocketAddress getSocketAddress(final Host host, final int port) {
        if (host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        } else {
            final IpAddress ipAddress = host.getIpAddress();
            final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue() :
                    ipAddress.getIpv6Address().getValue();
            return new InetSocketAddress(ip, port);
        }
    }

    @VisibleForTesting
    NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener,
                                                           final NetconfNode node) {

        //setup default values since default value is not supported in mdsal
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_CONNECTION_TIMEOUT_MILLIS : node.getConnectionTimeoutMillis();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null
                ? NetconfTopologyUtils.DEFAULT_MAX_CONNECTION_ATTEMPTS : node.getMaxConnectionAttempts();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null
                ? NetconfTopologyUtils.DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS : node.getBetweenAttemptsTimeoutMillis();
        final BigDecimal sleepFactor = node.getSleepFactor() == null
                ? NetconfTopologyUtils.DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = getSocketAddress(node.getHost(), node.getPort().getValue());

        final ReconnectStrategyFactory sf =
                new TimedReconnectStrategyFactory(netconfTopologyDeviceSetup.getEventExecutor(), maxConnectionAttempts,
                        betweenAttemptsTimeoutMillis, sleepFactor);
        final ReconnectStrategy strategy = sf.createReconnectStrategy();

        final AuthenticationHandler authHandler;
        final Credentials credentials = node.getCredentials();
        if (credentials instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) {
            authHandler = new LoginPassword(
                    ((org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) credentials).getUsername(),
                    ((org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) credentials).getPassword());
        } else {
            throw new IllegalStateException(remoteDeviceId + ": Only login/password authentication is supported");
        }

        return NetconfReconnectingClientConfigurationBuilder.create()
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(strategy)
                .withAuthHandler(authHandler)
                .withProtocol(node.isTcpOnly()
                        ? NetconfClientConfiguration.NetconfClientProtocol.TCP
                        : NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    @VisibleForTesting
    Map<String, NetconfDevice.SchemaResourcesDTO> getSchemaResourcesDTOs() {
        return schemaResourcesDTOs;
    }

    private static final class TimedReconnectStrategyFactory implements ReconnectStrategyFactory {
        private final Long connectionAttempts;
        private final EventExecutor executor;
        private final double sleepFactor;
        private final int minSleep;

        TimedReconnectStrategyFactory(final EventExecutor executor, final Long maxConnectionAttempts,
                                      final int minSleep, final BigDecimal sleepFactor) {
            if (maxConnectionAttempts != null && maxConnectionAttempts > 0) {
                connectionAttempts = maxConnectionAttempts;
            } else {
                connectionAttempts = null;
            }

            this.sleepFactor = sleepFactor.doubleValue();
            this.executor = executor;
            this.minSleep = minSleep;
        }

        @Override
        public ReconnectStrategy createReconnectStrategy() {
            final Long maxSleep = null;
            final Long deadline = null;

            return new TimedReconnectStrategy(executor, minSleep,
                    minSleep, sleepFactor, maxSleep, connectionAttempts, deadline);
        }
    }
}
