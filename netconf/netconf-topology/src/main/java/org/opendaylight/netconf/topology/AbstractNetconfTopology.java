/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
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
import java.util.concurrent.TimeUnit;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.api.NetconfMessage;
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
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.sal.connect.netconf.SchemalessNetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.auth.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.listener.UserPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfKeystoreAdapter;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.util.SslHandlerFactoryImpl;
import org.opendaylight.netconf.topology.api.NetconfTopology;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190621.NetconfNodeOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.key.auth.KeyBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencrypted;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.util.InMemorySchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.ASTSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology implements NetconfTopology {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    protected static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    protected static final int DEFAULT_KEEPALIVE_DELAY = 0;
    protected static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;
    protected static final int DEFAULT_CONCURRENT_RPC_LIMIT = 0;
    private static final boolean DEFAULT_IS_TCP_ONLY = false;
    private static final int DEFAULT_MAX_CONNECTION_ATTEMPTS = 0;
    private static final int DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS = 2000;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 20000L;
    private static final BigDecimal DEFAULT_SLEEP_FACTOR = new BigDecimal(1.5);

    // constants related to Schema Cache(s)
    /**
     * Filesystem based caches are stored relative to the cache directory.
     */
    private static final String CACHE_DIRECTORY = "cache";

    /**
     * The default cache directory relative to <code>CACHE_DIRECTORY</code>.
     */
    private static final String DEFAULT_CACHE_DIRECTORY = "schema";

    /**
     * The qualified schema cache directory <code>cache/schema</code>.
     */
    private static final String QUALIFIED_DEFAULT_CACHE_DIRECTORY =
            CACHE_DIRECTORY + File.separator + DEFAULT_CACHE_DIRECTORY;

    /**
     * The name for the default schema repository.
     */
    private static final String DEFAULT_SCHEMA_REPOSITORY_NAME = "sal-netconf-connector";

    /**
     * The default schema repository in the case that one is not specified.
     */
    private static final SharedSchemaRepository DEFAULT_SCHEMA_REPOSITORY =
            new SharedSchemaRepository(DEFAULT_SCHEMA_REPOSITORY_NAME);

    public static final InMemorySchemaSourceCache<ASTSchemaSource> DEFAULT_AST_CACHE =
            InMemorySchemaSourceCache.createSoftCache(DEFAULT_SCHEMA_REPOSITORY, ASTSchemaSource.class);

    /**
     * The default factory for creating <code>SchemaContext</code> instances.
     */
    private static final EffectiveModelContextFactory DEFAULT_SCHEMA_CONTEXT_FACTORY =
            DEFAULT_SCHEMA_REPOSITORY.createEffectiveModelContextFactory(
                SchemaContextFactoryConfiguration.getDefault());

    /**
     * Keeps track of initialized Schema resources.  A Map is maintained in which the key represents the name
     * of the schema cache directory, and the value is a corresponding <code>SchemaResourcesDTO</code>.  The
     * <code>SchemaResourcesDTO</code> is essentially a container that allows for the extraction of the
     * <code>SchemaRegistry</code> and <code>SchemaContextFactory</code> which should be used for a particular
     * Netconf mount.  Access to <code>SCHEMA_RESOURCES_DTO_MAP</code> should be surrounded by appropriate
     * synchronization locks.
     */
    private static final Map<String, NetconfDevice.SchemaResourcesDTO> SCHEMA_RESOURCES_DTO_MAP = new HashMap<>();

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.
    static {
        SCHEMA_RESOURCES_DTO_MAP.put(DEFAULT_CACHE_DIRECTORY,
                new NetconfDevice.SchemaResourcesDTO(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY,
                        DEFAULT_SCHEMA_CONTEXT_FACTORY,
                        new NetconfStateSchemasResolverImpl()));
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(DEFAULT_AST_CACHE);
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(
                TextToASTTransformer.create(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY));

        /*
         * Create the default <code>FilesystemSchemaSourceCache</code>, which stores cached files
         * in <code>cache/schema</code>. Try up to 3 times - we've seen intermittent failures on jenkins where
         * FilesystemSchemaSourceCache throws an IAE due to mkdirs failure. The theory is that there's a race
         * creating the dir and it already exists when mkdirs is called (mkdirs returns false in this case). In this
         * scenario, a retry should succeed.
         */
        int tries = 1;
        while (true) {
            try {
                FilesystemSchemaSourceCache<YangTextSchemaSource> defaultCache =
                        new FilesystemSchemaSourceCache<>(DEFAULT_SCHEMA_REPOSITORY, YangTextSchemaSource.class,
                                new File(QUALIFIED_DEFAULT_CACHE_DIRECTORY));
                DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(defaultCache);
                break;
            } catch (IllegalArgumentException e) {
                if (tries++ >= 3) {
                    LOG.error("Error creating default schema cache", e);
                    break;
                }
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }
        }
    }

    private final NetconfClientDispatcher clientDispatcher;
    private final EventExecutor eventExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfKeystoreAdapter keystoreAdapter;
    protected final ScheduledThreadPool keepaliveExecutor;
    protected final ListeningExecutorService processingExecutor;
    protected final SharedSchemaRepository sharedSchemaRepository;
    protected final DataBroker dataBroker;
    protected final DOMMountPointService mountPointService;
    protected final String topologyId;
    protected SchemaSourceRegistry schemaRegistry = DEFAULT_SCHEMA_REPOSITORY;
    protected SchemaRepository schemaRepository = DEFAULT_SCHEMA_REPOSITORY;
    protected SchemaContextFactory schemaContextFactory = DEFAULT_SCHEMA_CONTEXT_FACTORY;
    protected String privateKeyPath;
    protected String privateKeyPassphrase;
    protected final AAAEncryptionService encryptionService;
    protected final HashMap<NodeId, NetconfConnectorDTO> activeConnectors = new HashMap<>();

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                      final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                      final ThreadPool processingExecutor,
                                      final SchemaRepositoryProvider schemaRepositoryProvider,
                                      final DataBroker dataBroker, final DOMMountPointService mountPointService,
                                      final AAAEncryptionService encryptionService,
                                      final DeviceActionFactory deviceActionFactory) {
        this.topologyId = topologyId;
        this.clientDispatcher = clientDispatcher;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = MoreExecutors.listeningDecorator(processingExecutor.getExecutor());
        this.deviceActionFactory = deviceActionFactory;
        this.sharedSchemaRepository = schemaRepositoryProvider.getSharedSchemaRepository();
        this.dataBroker = dataBroker;
        this.mountPointService = mountPointService;
        this.encryptionService = encryptionService;

        this.keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
    }

    public void setSchemaRegistry(final SchemaSourceRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public void setSchemaContextFactory(final SchemaContextFactory schemaContextFactory) {
        this.schemaContextFactory = schemaContextFactory;
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
        if (!activeConnectors.containsKey(nodeId)) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Unable to disconnect device that is not connected"));
        }

        // retrieve connection, and disconnect it
        final NetconfConnectorDTO connectorDTO = activeConnectors.remove(nodeId);
        connectorDTO.getCommunicator().close();
        connectorDTO.getFacade().close();
        return Futures.immediateFuture(null);
    }

    protected ListenableFuture<NetconfDeviceCapabilities> setupConnection(final NodeId nodeId,
                                                                          final Node configNode) {
        final NetconfNode netconfNode = configNode.augmentation(NetconfNode.class);
        final NetconfNodeOptional nodeOptional = configNode.augmentation(NetconfNodeOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        final NetconfConnectorDTO deviceCommunicatorDTO = createDeviceCommunicator(nodeId, netconfNode, nodeOptional);
        final NetconfDeviceCommunicator deviceCommunicator = deviceCommunicatorDTO.getCommunicator();
        final NetconfClientSessionListener netconfClientSessionListener = deviceCommunicatorDTO.getSessionListener();
        final NetconfReconnectingClientConfiguration clientConfig =
                getClientConfig(netconfClientSessionListener, netconfNode);
        final ListenableFuture<NetconfDeviceCapabilities> future =
                deviceCommunicator.initializeRemoteConnection(clientDispatcher, clientConfig);

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
            final NetconfNodeOptional nodeOptional) {
        //setup default values since default value is not supported in mdsal
        final long defaultRequestTimeoutMillis = node.getDefaultRequestTimeoutMillis() == null
                ? DEFAULT_REQUEST_TIMEOUT_MILLIS : node.getDefaultRequestTimeoutMillis();
        final long keepaliveDelay = node.getKeepaliveDelay() == null
                ? DEFAULT_KEEPALIVE_DELAY : node.getKeepaliveDelay();
        final boolean reconnectOnChangedSchema = node.isReconnectOnChangedSchema() == null
                ? DEFAULT_RECONNECT_ON_CHANGED_SCHEMA : node.isReconnectOnChangedSchema();

        final IpAddress ipAddress = node.getHost().getIpAddress();
        final InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null
                ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue());
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId(nodeId.getValue(), address);

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade =
                createSalFacade(remoteDeviceId);

        if (keepaliveDelay > 0) {
            LOG.warn("Adding keepalive facade, for device {}", nodeId);
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade, this.keepaliveExecutor.getExecutor(),
                    keepaliveDelay, defaultRequestTimeoutMillis);
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

                for (final Map.Entry<SourceIdentifier, URL> sourceIdentifierURLEntry
                        : libraryModulesSchemas.getAvailableModels().entrySet()) {
                    registeredYangLibSources
                        .add(schemaRegistry.registerSchemaSource(
                                new YangLibrarySchemaYangSourceProvider(remoteDeviceId,
                                        libraryModulesSchemas.getAvailableModels()),
                                PotentialSchemaSource.create(sourceIdentifierURLEntry.getKey(),
                                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
            }
        }

        final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = setupSchemaCacheDTO(nodeId, node);
        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> device;
        if (node.isSchemaless()) {
            device = new SchemalessNetconfDevice(remoteDeviceId, salFacade);
        } else {
            NetconfDeviceBuilder netconfDeviceBuilder = new NetconfDeviceBuilder()
                    .setReconnectOnSchemasChange(reconnectOnChangedSchema)
                    .setSchemaResourcesDTO(schemaResourcesDTO)
                    .setGlobalProcessingExecutor(this.processingExecutor)
                    .setId(remoteDeviceId)
                    .setSalFacade(salFacade)
                    .setNode(node)
                    .setEventExecutor(eventExecutor)
                    .setNodeOptional(nodeOptional);
            if (this.deviceActionFactory != null) {
                netconfDeviceBuilder.setDeviceActionFactory(this.deviceActionFactory);
            }
            device = netconfDeviceBuilder.build();
        }

        final Optional<UserPreferences> userCapabilities = getUserCapabilities(node);
        final int rpcMessageLimit =
                node.getConcurrentRpcLimit() == null ? DEFAULT_CONCURRENT_RPC_LIMIT : node.getConcurrentRpcLimit();

        if (rpcMessageLimit < 1) {
            LOG.info("Concurrent rpc limit is smaller than 1, no limit will be enforced for device {}", remoteDeviceId);
        }

        NetconfDeviceCommunicator netconfDeviceCommunicator =
             userCapabilities.isPresent() ? new NetconfDeviceCommunicator(remoteDeviceId, device,
                     userCapabilities.get(), rpcMessageLimit)
            : new NetconfDeviceCommunicator(remoteDeviceId, device, rpcMessageLimit);

        if (salFacade instanceof KeepaliveSalFacade) {
            ((KeepaliveSalFacade)salFacade).setListener(netconfDeviceCommunicator);
        }
        return new NetconfConnectorDTO(netconfDeviceCommunicator, salFacade);
    }

    protected NetconfDevice.SchemaResourcesDTO setupSchemaCacheDTO(final NodeId nodeId, final NetconfNode node) {
        // Setup information related to the SchemaRegistry, SchemaResourceFactory, etc.
        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = null;
        final String moduleSchemaCacheDirectory = node.getSchemaCacheDirectory();
        // Only checks to ensure the String is not empty or null; further checks related to directory
        // accessibility and file permissionsare handled during the FilesystemSchemaSourceCache initialization.
        if (!Strings.isNullOrEmpty(moduleSchemaCacheDirectory)) {
            // If a custom schema cache directory is specified, create the backing DTO; otherwise,
            // the SchemaRegistry and SchemaContextFactory remain the default values.
            if (!moduleSchemaCacheDirectory.equals(DEFAULT_CACHE_DIRECTORY)) {
                // Multiple modules may be created at once;
                // synchronize to avoid issues with data consistency among threads.
                synchronized (SCHEMA_RESOURCES_DTO_MAP) {
                    // Look for the cached DTO to reuse SchemaRegistry and SchemaContextFactory variables
                    // if they already exist
                    schemaResourcesDTO = SCHEMA_RESOURCES_DTO_MAP.get(moduleSchemaCacheDirectory);
                    if (schemaResourcesDTO == null) {
                        schemaResourcesDTO = createSchemaResourcesDTO(moduleSchemaCacheDirectory);
                        schemaResourcesDTO.getSchemaRegistry().registerSchemaSourceListener(
                                TextToASTTransformer.create((SchemaRepository) schemaResourcesDTO.getSchemaRegistry(),
                                        schemaResourcesDTO.getSchemaRegistry())
                        );
                        SCHEMA_RESOURCES_DTO_MAP.put(moduleSchemaCacheDirectory, schemaResourcesDTO);
                    }
                }
                LOG.info("Netconf connector for device {} will use schema cache directory {} instead of {}",
                        nodeId.getValue(), moduleSchemaCacheDirectory, DEFAULT_CACHE_DIRECTORY);
            }
        } else {
            LOG.warn("schema-cache-directory for {} is null or empty;  using the default {}",
                    nodeId.getValue(), QUALIFIED_DEFAULT_CACHE_DIRECTORY);
        }

        if (schemaResourcesDTO == null) {
            schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry, schemaRepository,
                    schemaContextFactory, new NetconfStateSchemasResolverImpl());
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
        final EffectiveModelContextFactory contextFactory
                = repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault());
        setSchemaRegistry(repository);
        setSchemaContextFactory(contextFactory);
        final FilesystemSchemaSourceCache<YangTextSchemaSource> deviceCache =
                createDeviceFilesystemCache(moduleSchemaCacheDirectory);
        repository.registerSchemaSourceListener(deviceCache);
        repository.registerSchemaSourceListener(
            InMemorySchemaSourceCache.createSoftCache(repository, ASTSchemaSource.class));
        return new NetconfDevice.SchemaResourcesDTO(repository, repository, contextFactory,
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
        final String relativeSchemaCacheDirectory = CACHE_DIRECTORY + File.separator + schemaCacheDirectory;
        return new FilesystemSchemaSourceCache<>(schemaRegistry, YangTextSchemaSource.class,
                new File(relativeSchemaCacheDirectory));
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

    public NetconfReconnectingClientConfiguration getClientConfig(final NetconfClientSessionListener listener,
                                                                  final NetconfNode node) {

        //setup default values since default value is not supported in mdsal
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null
                ? DEFAULT_CONNECTION_TIMEOUT_MILLIS : node.getConnectionTimeoutMillis();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null
                ? DEFAULT_MAX_CONNECTION_ATTEMPTS : node.getMaxConnectionAttempts();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null
                ? DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS : node.getBetweenAttemptsTimeoutMillis();
        final boolean useTcp = node.isTcpOnly() == null ? DEFAULT_IS_TCP_ONLY : node.isTcpOnly();
        final BigDecimal sleepFactor = node.getSleepFactor() == null ? DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = getSocketAddress(node.getHost(), node.getPort().getValue());

        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor,
                maxConnectionAttempts, betweenAttemptsTimeoutMillis, sleepFactor);

        final NetconfReconnectingClientConfigurationBuilder reconnectingClientConfigurationBuilder;
        final Protocol protocol = node.getProtocol();
        if (useTcp) {
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
            reconnectingClientConfigurationBuilder
                    .withOdlHelloCapabilities(node.getOdlHelloMessageCapabilities().getCapability());
        }

        return reconnectingClientConfigurationBuilder
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(sf.createReconnectStrategy())
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
    }

    private AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                .rev150114.netconf.node.credentials.credentials.LoginPassword) {
            final org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                    .rev150114.netconf.node.credentials.credentials.LoginPassword loginPassword
                    = (org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology
                    .rev150114.netconf.node.credentials.credentials.LoginPassword) credentials;
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPwUnencrypted) {
            final LoginPasswordUnencrypted loginPassword =
                    ((LoginPwUnencrypted) credentials).getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPw) {
            final LoginPassword loginPassword = ((LoginPw) credentials).getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        }
        if (credentials instanceof KeyAuth) {
            final KeyBased keyPair = ((KeyAuth) credentials).getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(),
                    keystoreAdapter, encryptionService);
        }
        throw new IllegalStateException("Unsupported credential type: " + credentials.getClass());
    }

    protected abstract RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(RemoteDeviceId id);

    private static InetSocketAddress getSocketAddress(final Host host, final int port) {
        if (host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        }

        final IpAddress ipAddress = host.getIpAddress();
        final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue()
                : ipAddress.getIpv6Address().getValue();
        return new InetSocketAddress(ip, port);
    }

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

        private final NetconfDeviceCommunicator communicator;
        private final RemoteDeviceHandler<NetconfSessionPreferences> facade;

        public NetconfConnectorDTO(final NetconfDeviceCommunicator communicator,
                                   final RemoteDeviceHandler<NetconfSessionPreferences> facade) {
            this.communicator = communicator;
            this.facade = facade;
        }

        public NetconfDeviceCommunicator getCommunicator() {
            return communicator;
        }

        public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
            return facade;
        }

        public NetconfClientSessionListener getSessionListener() {
            return communicator;
        }

        @Override
        public void close() {
            communicator.close();
            facade.close();
        }
    }
}
