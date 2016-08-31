/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.md.sal.connector.netconf;

import static org.opendaylight.controller.config.api.JmxAttributeValidationException.checkCondition;
import static org.opendaylight.controller.config.api.JmxAttributeValidationException.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.netty.util.concurrent.EventExecutor;
import java.io.File;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPassword;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.LibraryModulesSchemas;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceBuilder;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.listener.UserPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;
import org.opendaylight.protocol.framework.TimedReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
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
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class NetconfConnectorModule extends org.opendaylight.controller.config.yang.md.sal.connector.netconf.AbstractNetconfConnectorModule
{
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConnectorModule.class);

    /**
     * Filesystem based caches are stored relative to the cache directory.
     */
    private static final String CACHE_DIRECTORY = "cache";

    /**
     * The default cache directory relative to <code>CACHE_DIRECTORY</code>
     */
    private static final String DEFAULT_CACHE_DIRECTORY = "schema";

    /**
     * The qualified schema cache directory <code>cache/schema</code>
     */
    private static final String QUALIFIED_DEFAULT_CACHE_DIRECTORY = CACHE_DIRECTORY + File.separator+ DEFAULT_CACHE_DIRECTORY;

    /**
     * The name for the default schema repository
     */
    private static final String DEFAULT_SCHEMA_REPOSITORY_NAME = "sal-netconf-connector";

    /**
     * The default schema repository in the case that one is not specified.
     */
    private static final SharedSchemaRepository DEFAULT_SCHEMA_REPOSITORY =
            new SharedSchemaRepository(DEFAULT_SCHEMA_REPOSITORY_NAME);

    /**
     * The default <code>FilesystemSchemaSourceCache</code>, which stores cached files in <code>cache/schema</code>.
     */
    private static final FilesystemSchemaSourceCache<YangTextSchemaSource> DEFAULT_CACHE =
            new FilesystemSchemaSourceCache<>(DEFAULT_SCHEMA_REPOSITORY, YangTextSchemaSource.class,
                    new File(QUALIFIED_DEFAULT_CACHE_DIRECTORY));

    /**
     * The default factory for creating <code>SchemaContext</code> instances.
     */
    private static final SchemaContextFactory DEFAULT_SCHEMA_CONTEXT_FACTORY =
            DEFAULT_SCHEMA_REPOSITORY.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);

    private static final int LOCAL_IO_FALLBACK_COST = PotentialSchemaSource.Costs.LOCAL_IO.getValue() + 1;

    /**
     * Keeps track of initialized Schema resources.  A Map is maintained in which the key represents the name
     * of the schema cache directory, and the value is a corresponding <code>SchemaResourcesDTO</code>.  The
     * <code>SchemaResourcesDTO</code> is essentially a container that allows for the extraction of the
     * <code>SchemaRegistry</code> and <code>SchemaContextFactory</code> which should be used for a particular
     * Netconf mount.  Access to <code>schemaResourcesDTOs</code> should be surrounded by appropriate
     * synchronization locks.
     */
    private static volatile Map<String, NetconfDevice.SchemaResourcesDTO> schemaResourcesDTOs = new HashMap<>();

    // Initializes default constant instances for the case when the default schema repository
    // directory cache/schema is used.
    static {
        schemaResourcesDTOs.put(DEFAULT_CACHE_DIRECTORY,
                new NetconfDevice.SchemaResourcesDTO(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY,
                        DEFAULT_SCHEMA_CONTEXT_FACTORY,
                        new NetconfStateSchemasResolverImpl()));
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(DEFAULT_CACHE);
        DEFAULT_SCHEMA_REPOSITORY.registerSchemaSourceListener(
                TextToASTTransformer.create(DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY));
    }

    private BundleContext bundleContext;
    private Optional<NetconfSessionPreferences> userCapabilities;
    private SchemaSourceRegistry schemaRegistry = DEFAULT_SCHEMA_REPOSITORY;
    private SchemaRepository schemaRepository = DEFAULT_SCHEMA_REPOSITORY;
    private SchemaContextFactory schemaContextFactory = DEFAULT_SCHEMA_CONTEXT_FACTORY;

    private Broker domRegistry;
    private NetconfClientDispatcher clientDispatcher;
    private BindingAwareBroker bindingRegistry;
    private ThreadPool processingExecutor;
    private ScheduledThreadPool keepaliveExecutor;
    private EventExecutor eventExecutor;

    /**
     * The name associated with the Netconf mount point.  This value is passed from <code>NetconfConnectorModuleFactory</code>.
     */
    private String instanceName;

    public NetconfConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final NetconfConnectorModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    protected void customValidation() {
        checkNotNull(getAddress(), addressJmxAttribute);
        checkCondition(isHostAddressPresent(getAddress()), "Host address not present in " + getAddress(), addressJmxAttribute);
        checkNotNull(getPort(), portJmxAttribute);

        checkNotNull(getConnectionTimeoutMillis(), connectionTimeoutMillisJmxAttribute);
        checkCondition(getConnectionTimeoutMillis() > 0, "must be > 0", connectionTimeoutMillisJmxAttribute);

        checkNotNull(getDefaultRequestTimeoutMillis(), defaultRequestTimeoutMillisJmxAttribute);
        checkCondition(getDefaultRequestTimeoutMillis() > 0, "must be > 0", defaultRequestTimeoutMillisJmxAttribute);

        checkNotNull(getBetweenAttemptsTimeoutMillis(), betweenAttemptsTimeoutMillisJmxAttribute);
        checkCondition(getBetweenAttemptsTimeoutMillis() > 0, "must be > 0", betweenAttemptsTimeoutMillisJmxAttribute);

        // Check username + password in case of ssh
        if(getTcpOnly() == false) {
            checkNotNull(getUsername(), usernameJmxAttribute);
            checkNotNull(getPassword(), passwordJmxAttribute);
        }

        userCapabilities = getUserCapabilities();
    }

    private boolean isHostAddressPresent(final Host address) {
        return address.getDomainName() != null ||
               address.getIpAddress() != null && (address.getIpAddress().getIpv4Address() != null || address.getIpAddress().getIpv6Address() != null);
    }

    @Deprecated
    private static ScheduledExecutorService DEFAULT_KEEPALIVE_EXECUTOR;

    @Override
    public java.lang.AutoCloseable createInstance() {
        initDependencies();
        final RemoteDeviceId id = new RemoteDeviceId(getIdentifier(), getSocketAddress());

        final ExecutorService globalProcessingExecutor = processingExecutor.getExecutor();

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade
                = new NetconfDeviceSalFacade(id, domRegistry, bindingRegistry);

        final Long keepaliveDelay = getKeepaliveDelay();
        if (shouldSendKeepalive()) {
            // Keepalive executor is optional for now and a default instance is supported
            final ScheduledExecutorService executor = keepaliveExecutor == null ? DEFAULT_KEEPALIVE_EXECUTOR : keepaliveExecutor.getExecutor();

            salFacade = new KeepaliveSalFacade(id, salFacade, executor, keepaliveDelay, getDefaultRequestTimeoutMillis());
        }

        // Setup information related to the SchemaRegistry, SchemaResourceFactory, etc.
        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO = null;
        final String moduleSchemaCacheDirectory = getSchemaCacheDirectory();
        // Only checks to ensure the String is not empty or null;  further checks related to directory accessibility and file permissions
        // are handled during the FilesystemSchemaSourceCache initialization.
        if (!Strings.isNullOrEmpty(moduleSchemaCacheDirectory)) {
            // If a custom schema cache directory is specified, create the backing DTO; otherwise, the SchemaRegistry and
            // SchemaContextFactory remain the default values.
            if (!moduleSchemaCacheDirectory.equals(DEFAULT_CACHE_DIRECTORY)) {
                // Multiple modules may be created at once;  synchronize to avoid issues with data consistency among threads.
                synchronized(schemaResourcesDTOs) {
                    // Look for the cached DTO to reuse SchemaRegistry and SchemaContextFactory variables if they already exist
                    final NetconfDevice.SchemaResourcesDTO dto =
                            schemaResourcesDTOs.get(moduleSchemaCacheDirectory);
                    if (dto == null) {
                        schemaResourcesDTO = createSchemaResourcesDTO(moduleSchemaCacheDirectory);
                        schemaRegistry.registerSchemaSourceListener(
                                TextToASTTransformer.create((SchemaRepository) schemaRegistry, schemaRegistry));
                        schemaResourcesDTOs.put(moduleSchemaCacheDirectory, schemaResourcesDTO);
                    } else {
                        setSchemaContextFactory(dto.getSchemaContextFactory());
                        setSchemaRegistry(dto.getSchemaRegistry());
                        schemaResourcesDTO = dto;
                    }
                    if (userCapabilities.isPresent()) {
                        for (QName qname : userCapabilities.get().getModuleBasedCaps()) {
                            final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier
                                    .create(qname.getLocalName(), qname.getFormattedRevision());
                            dto.getSchemaRegistry().registerSchemaSource(DEFAULT_CACHE, PotentialSchemaSource
                                    .create(sourceIdentifier, YangTextSchemaSource.class, LOCAL_IO_FALLBACK_COST));
                        }
                    }
                }
                LOG.info("Netconf connector for device {} will use schema cache directory {} instead of {}",
                        instanceName, moduleSchemaCacheDirectory, DEFAULT_CACHE_DIRECTORY);
            }
        } else {
            LOG.warn("schema-cache-directory for {} is null or empty;  using the default {}",
                    instanceName, QUALIFIED_DEFAULT_CACHE_DIRECTORY);
        }

        // pre register yang library sources as fallback schemas to schema registry
        List<SchemaSourceRegistration<YangTextSchemaSource>> registeredYangLibSources = Lists.newArrayList();
        if (getYangLibrary() != null) {
            final String yangLibURL = getYangLibrary().getYangLibraryUrl().getValue();
            final String yangLibUsername = getYangLibrary().getUsername();
            final String yangLigPassword = getYangLibrary().getPassword();

            LibraryModulesSchemas libraryModulesSchemas;
            if(yangLibURL != null) {
                if(yangLibUsername != null && yangLigPassword != null) {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL, yangLibUsername, yangLigPassword);
                } else {
                    libraryModulesSchemas = LibraryModulesSchemas.create(yangLibURL);
                }

                for (Map.Entry<SourceIdentifier, URL> sourceIdentifierURLEntry : libraryModulesSchemas.getAvailableModels().entrySet()) {
                    registeredYangLibSources.
                            add(schemaRegistry.registerSchemaSource(
                                    new YangLibrarySchemaYangSourceProvider(id, libraryModulesSchemas.getAvailableModels()),
                                    PotentialSchemaSource
                                            .create(sourceIdentifierURLEntry.getKey(), YangTextSchemaSource.class,
                                                    PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
            }
        }

        if (schemaResourcesDTO == null) {
            schemaResourcesDTO = new NetconfDevice.SchemaResourcesDTO(schemaRegistry, schemaRepository, schemaContextFactory,
                    new NetconfStateSchemasResolverImpl());
        }

        final NetconfDevice device = new NetconfDeviceBuilder()
                .setReconnectOnSchemasChange(getReconnectOnChangedSchema())
                .setSchemaResourcesDTO(schemaResourcesDTO)
                .setGlobalProcessingExecutor(globalProcessingExecutor)
                .setId(id)
                .setSalFacade(salFacade)
                .build();

        if (getConcurrentRpcLimit() < 1) {
            LOG.info("Concurrent rpc limit is smaller than 1, no limit will be enforced for device {}", id);
        }

        final NetconfDeviceCommunicator listener = userCapabilities.isPresent()
                ? new NetconfDeviceCommunicator(id, device, new UserPreferences(userCapabilities.get(),
                        getYangModuleCapabilities().getOverride(), getYangNonModuleCapabilities().getOverride()),
                        getConcurrentRpcLimit())
                : new NetconfDeviceCommunicator(id, device, getConcurrentRpcLimit());

        if (shouldSendKeepalive()) {
            ((KeepaliveSalFacade) salFacade).setListener(listener);
        }

        final NetconfReconnectingClientConfiguration clientConfig = getClientConfig(listener);
        listener.initializeRemoteConnection(clientDispatcher, clientConfig);

        return new SalConnectorCloseable(listener, salFacade, registeredYangLibSources);
    }

    /**
     * Creates the backing Schema classes for a particular directory.
     *
     * @param moduleSchemaCacheDirectory The string directory relative to "cache"
     * @return A DTO containing the Schema classes for the Netconf mount.
     */
    private NetconfDevice.SchemaResourcesDTO createSchemaResourcesDTO(final String moduleSchemaCacheDirectory) {
        final SharedSchemaRepository repository = new SharedSchemaRepository(instanceName);
        final SchemaContextFactory schemaContextFactory
                = repository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
        setSchemaRegistry(repository);
        setSchemaContextFactory(schemaContextFactory);
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
    private FilesystemSchemaSourceCache<YangTextSchemaSource> createDeviceFilesystemCache(final String schemaCacheDirectory) {
        final String relativeSchemaCacheDirectory = CACHE_DIRECTORY + File.separator + schemaCacheDirectory;
        return new FilesystemSchemaSourceCache<>(schemaRegistry, YangTextSchemaSource.class, new File(relativeSchemaCacheDirectory));
    }

    private void initDependencies() {
        domRegistry = getDomRegistryDependency();
        clientDispatcher = getClientDispatcherDependency();
        bindingRegistry = getBindingRegistryDependency();
        processingExecutor = getProcessingExecutorDependency();
        eventExecutor = getEventExecutorDependency();

        if(getKeepaliveExecutor() == null) {
            LOG.warn("Keepalive executor missing. Using default instance for now, the configuration needs to be updated");

            // Instantiate the default executor, now we know its necessary
            if(DEFAULT_KEEPALIVE_EXECUTOR == null) {
                DEFAULT_KEEPALIVE_EXECUTOR = Executors.newScheduledThreadPool(2, new ThreadFactory() {
                    @Override
                    public Thread newThread(final Runnable r) {
                        final Thread thread = new Thread(r);
                        thread.setName("netconf-southound-keepalives-" + thread.getId());
                        thread.setDaemon(true);
                        return thread;
                    }
                });
            }
        } else {
            keepaliveExecutor = getKeepaliveExecutorDependency();
        }
    }

    private boolean shouldSendKeepalive() {
        return getKeepaliveDelay() > 0;
    }

    private Optional<NetconfSessionPreferences> getUserCapabilities() {
        if (getYangModuleCapabilities() == null && getYangNonModuleCapabilities() == null) {
            return Optional.absent();
        }

        if ((getYangModuleCapabilities().getCapability() == null
                || getYangModuleCapabilities().getCapability().isEmpty())
                && (getYangNonModuleCapabilities().getCapability() == null
                        || getYangNonModuleCapabilities().getCapability().isEmpty())) {
            return Optional.absent();
        }

        final List<String> capabilities = new ArrayList<>();
        if (!(getYangModuleCapabilities().getCapability() == null
                || getYangModuleCapabilities().getCapability().isEmpty())) {
            capabilities.addAll(getYangModuleCapabilities().getCapability());
        }
        if (!(getYangNonModuleCapabilities().getCapability() == null
                || getYangNonModuleCapabilities().getCapability().isEmpty())) {
            capabilities.addAll(getYangNonModuleCapabilities().getCapability());
        }

        final NetconfSessionPreferences parsedOverrideCapabilities =
                NetconfSessionPreferences.fromStrings(capabilities);
        return Optional.of(parsedOverrideCapabilities);
    }

    public NetconfReconnectingClientConfiguration getClientConfig(final NetconfDeviceCommunicator listener) {
        final InetSocketAddress socketAddress = getSocketAddress();
        final long clientConnectionTimeoutMillis = getConnectionTimeoutMillis();

        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor,
                getMaxConnectionAttempts(), getBetweenAttemptsTimeoutMillis(), getSleepFactor());
        final ReconnectStrategy strategy = sf.createReconnectStrategy();

        return NetconfReconnectingClientConfigurationBuilder.create()
        .withAddress(socketAddress)
        .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
        .withReconnectStrategy(strategy)
        .withAuthHandler(new LoginPassword(getUsername(), getPassword()))
        .withProtocol(getTcpOnly() ?
                NetconfClientConfiguration.NetconfClientProtocol.TCP :
                NetconfClientConfiguration.NetconfClientProtocol.SSH)
        .withConnectStrategyFactory(sf)
        .withSessionListener(listener)
        .build();
    }

    private static final class SalConnectorCloseable implements AutoCloseable {
        private final RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
        private final List<SchemaSourceRegistration<YangTextSchemaSource>> registeredYangLibSources;
        private final NetconfDeviceCommunicator listener;

        public SalConnectorCloseable(final NetconfDeviceCommunicator listener,
                                     final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                                     final List<SchemaSourceRegistration<YangTextSchemaSource>> registeredYangLibSources) {
            this.listener = listener;
            this.salFacade = salFacade;
            this.registeredYangLibSources = registeredYangLibSources;
        }

        @Override
        public void close() {
            for (SchemaSourceRegistration<YangTextSchemaSource> registeredYangLibSource : registeredYangLibSources) {
                registeredYangLibSource.close();
            }
            listener.close();
            salFacade.close();
        }
    }

    private static final class TimedReconnectStrategyFactory implements ReconnectStrategyFactory {
        private final Long connectionAttempts;
        private final EventExecutor executor;
        private final double sleepFactor;
        private final int minSleep;

        TimedReconnectStrategyFactory(final EventExecutor executor, final Long maxConnectionAttempts, final int minSleep, final BigDecimal sleepFactor) {
            if (maxConnectionAttempts != null && maxConnectionAttempts > 0) {
                connectionAttempts = maxConnectionAttempts;
            } else {
                LOG.trace("Setting {} on {} to infinity", maxConnectionAttemptsJmxAttribute, this);
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

    private InetSocketAddress getSocketAddress() {
        if(getAddress().getDomainName() != null) {
            return new InetSocketAddress(getAddress().getDomainName().getValue(), getPort().getValue());
        } else {
            final IpAddress ipAddress = getAddress().getIpAddress();
            final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue();
            return new InetSocketAddress(ip, getPort().getValue());
        }
    }

    public void setSchemaRegistry(final SchemaSourceRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public void setSchemaContextFactory(final SchemaContextFactory schemaContextFactory) {
        this.schemaContextFactory = schemaContextFactory;
    }

    public void setInstanceName(final String instanceName) {
        this.instanceName = instanceName;
    }

}
