/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static java.util.Objects.requireNonNullElseGet;

import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.northbound.ssh.SshProxyServer;
import org.opendaylight.netconf.northbound.ssh.SshProxyServerConfiguration;
import org.opendaylight.netconf.northbound.ssh.SshProxyServerConfigurationBuilder;
import org.opendaylight.netconf.server.NetconfServerDispatcherImpl;
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.ServerChannelInitializer;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.YangModuleCapability;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyPairProvider;
import org.opendaylight.netconf.shaded.sshd.common.util.threads.ThreadUtils;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.customrpc.SettableOperationProvider;
import org.opendaylight.netconf.test.tool.monitoring.NetconfMonitoringOperationServiceFactory;
import org.opendaylight.netconf.test.tool.operations.DefaultOperationsCreator;
import org.opendaylight.netconf.test.tool.operations.OperationsProvider;
import org.opendaylight.netconf.test.tool.rpchandler.SettableOperationRpcProvider;
import org.opendaylight.netconf.test.tool.schemacache.SchemaSourceCache;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.Submodule;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.fs.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSimulator implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSimulator.class);

    private final NioEventLoopGroup nettyThreadgroup;
    private final HashedWheelTimer hashedWheelTimer;
    private final List<Channel> devicesChannels = new ArrayList<>();
    private final List<SshProxyServer> sshWrappers = new ArrayList<>();
    private final ScheduledExecutorService minaTimerExecutor;
    private final ExecutorService nioExecutor;
    private final Configuration configuration;
    private EffectiveModelContext schemaContext;

    private boolean sendFakeSchema = false;

    public NetconfDeviceSimulator(final Configuration configuration) {
        this.configuration = configuration;
        nettyThreadgroup = new NioEventLoopGroup();
        hashedWheelTimer = new HashedWheelTimer();
        minaTimerExecutor = Executors.newScheduledThreadPool(configuration.getThreadPoolSize(),
                new ThreadFactoryBuilder().setNameFormat("netconf-ssh-server-mina-timers-%d").build());
        nioExecutor = ThreadUtils.newFixedThreadPool("netconf-ssh-server-nio-group", configuration.getThreadPoolSize());
    }

    private NetconfServerDispatcherImpl createDispatcher(final Set<Capability> capabilities,
            final SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {

        final Set<Capability> transformedCapabilities = new HashSet<>(Collections2.transform(capabilities, input -> {
            if (sendFakeSchema) {
                sendFakeSchema = false;
                return new FakeCapability((YangModuleCapability) input);
            } else {
                return input;
            }
        }));
        transformedCapabilities.add(new BasicCapability(CapabilityURN.CANDIDATE));
        final NetconfMonitoringService monitoringService1 = new DummyMonitoringService(transformedCapabilities);
        final SessionIdProvider idProvider = new DefaultSessionIdProvider();

        final NetconfOperationServiceFactory aggregatedNetconfOperationServiceFactory = createOperationServiceFactory(
            sourceProvider, transformedCapabilities, monitoringService1, idProvider);

        final Set<String> serverCapabilities = configuration.getCapabilities();

        final NetconfServerSessionNegotiatorFactory serverNegotiatorFactory = new TesttoolNegotiationFactory(
                hashedWheelTimer, aggregatedNetconfOperationServiceFactory, idProvider,
                configuration.getGenerateConfigsTimeout(),
                monitoringService1, serverCapabilities);

        final ServerChannelInitializer serverChannelInitializer =
            new ServerChannelInitializer(serverNegotiatorFactory);
        return new NetconfServerDispatcherImpl(serverChannelInitializer, nettyThreadgroup, nettyThreadgroup);
    }

    private NetconfOperationServiceFactory createOperationServiceFactory(
            final SchemaSourceProvider<YangTextSchemaSource> sourceProvider,
            final Set<Capability> transformedCapabilities, final NetconfMonitoringService monitoringService1,
            final SessionIdProvider idProvider) {
        final AggregatedNetconfOperationServiceFactory aggregatedNetconfOperationServiceFactory =
            new AggregatedNetconfOperationServiceFactory();

        final NetconfOperationServiceFactory operationProvider;
        if (configuration.isMdSal()) {
            LOG.info("using MdsalOperationProvider.");
            operationProvider = new MdsalOperationProvider(
                idProvider, transformedCapabilities, schemaContext, sourceProvider);
        } else if (configuration.isXmlConfigurationProvided()) {
            LOG.info("using SimulatedOperationProvider.");
            operationProvider = new SimulatedOperationProvider(transformedCapabilities,
                    Optional.ofNullable(configuration.getNotificationFile()),
                    Optional.ofNullable(configuration.getInitialConfigXMLFile()));
        } else if (configuration.isNotificationsSupported()) {
            LOG.info("using SimulatedOperationProvider.");
            operationProvider = new SimulatedOperationProvider(transformedCapabilities,
                    Optional.ofNullable(configuration.getNotificationFile()),
                    Optional.empty());
        } else {
            LOG.info("using OperationsProvider.");
            operationProvider = new OperationsProvider(transformedCapabilities,
                requireNonNullElseGet(configuration.getOperationsCreator(), DefaultOperationsCreator::new));
        }

        final NetconfMonitoringOperationServiceFactory monitoringService =
                new NetconfMonitoringOperationServiceFactory(monitoringService1);
        aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(operationProvider);
        aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(monitoringService);
        if (configuration.getRpcConfigFile() != null) {
            final SettableOperationProvider settableService =
                    new SettableOperationProvider(configuration.getRpcConfigFile());
            aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(settableService);
        } else {
            final SettableOperationRpcProvider settableService =
                    new SettableOperationRpcProvider(configuration.getRpcHandler());
            aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(settableService);
        }
        return aggregatedNetconfOperationServiceFactory;
    }

    public List<Integer> start() {
        final var proto = configuration.isSsh() ? "SSH" : "TCP";
        LOG.info("Starting {}, {} simulated devices starting on port {}",
                configuration.getDeviceCount(), proto, configuration.getStartingPort());

        final SharedSchemaRepository schemaRepo = new SharedSchemaRepository("netconf-simulator");
        final Set<Capability> capabilities = parseSchemasToModuleCapabilities(schemaRepo);

        final NetconfServerDispatcherImpl dispatcher = createDispatcher(capabilities,
            sourceIdentifier -> schemaRepo.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class));

        int currentPort = configuration.getStartingPort();

        final List<Integer> openDevices = new ArrayList<>();

        // Generate key to temp folder
        final KeyPairProvider keyPairProvider = new VirtualKeyPairProvider();

        final AsynchronousChannelGroup group;
        try {
            group = AsynchronousChannelGroup.withThreadPool(nioExecutor);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create group", e);
        }

        for (int i = 0; i < configuration.getDeviceCount(); i++) {
            if (currentPort > 65535) {
                LOG.warn("Port cannot be greater than 65535, stopping further attempts.");
                break;
            }
            final InetSocketAddress address = getAddress(configuration.getIp(), currentPort);

            final ChannelFuture server;
            if (configuration.isSsh()) {
                final InetSocketAddress bindingAddress = InetSocketAddress.createUnresolved("0.0.0.0", currentPort);
                final LocalAddress tcpLocalAddress = new LocalAddress(address.toString());

                server = dispatcher.createLocalServer(tcpLocalAddress);
                try {
                    final SshProxyServer sshServer = new SshProxyServer(
                        minaTimerExecutor, nettyThreadgroup, group);
                    sshServer.bind(getSshConfiguration(bindingAddress, tcpLocalAddress, keyPairProvider));
                    sshWrappers.add(sshServer);
                } catch (final BindException e) {
                    LOG.warn("Cannot start simulated device on {}, port already in use. Skipping.", address);
                    // Close local server and continue
                    server.cancel(true);
                    if (server.isDone()) {
                        server.channel().close();
                    }
                    continue;
                } catch (final IOException e) {
                    LOG.warn("Cannot start simulated device on {} due to IOException.", address, e);
                    break;
                } finally {
                    currentPort++;
                }

                try {
                    server.get();
                } catch (final InterruptedException e) {
                    throw new IllegalStateException("Interrupted while waiting for server", e);
                } catch (final ExecutionException e) {
                    LOG.warn("Cannot start ssh simulated device on {}, skipping", address, e);
                    continue;
                }

                LOG.debug("Simulated SSH device started on {}", address);

            } else {
                server = dispatcher.createServer(address);
                currentPort++;

                try {
                    server.get();
                } catch (final InterruptedException e) {
                    throw new IllegalStateException("Interrupted while waiting for server", e);
                } catch (final ExecutionException e) {
                    LOG.warn("Cannot start tcp simulated device on {}, skipping", address, e);
                    continue;
                }

                LOG.debug("Simulated TCP device started on {}", server.channel().localAddress());
            }

            devicesChannels.add(server.channel());
            openDevices.add(currentPort - 1);
        }

        if (openDevices.size() == configuration.getDeviceCount()) {
            LOG.info("All simulated devices started successfully from port {} to {}",
                    configuration.getStartingPort(), currentPort - 1);
        } else if (openDevices.size() == 0) {
            LOG.warn("No simulated devices started.");
        } else {
            LOG.warn("Not all simulated devices started successfully. Started devices ar on ports {}", openDevices);
        }

        return openDevices;
    }

    private SshProxyServerConfiguration getSshConfiguration(final InetSocketAddress bindingAddress,
            final LocalAddress tcpLocalAddress, final KeyPairProvider keyPairProvider) {
        return new SshProxyServerConfigurationBuilder()
                .setBindingAddress(bindingAddress)
                .setLocalAddress(tcpLocalAddress)
                .setAuthenticator(configuration.getAuthProvider())
                .setPublickeyAuthenticator(configuration.getPublickeyAuthenticator())
                .setKeyPairProvider(keyPairProvider)
                .setIdleTimeout(Integer.MAX_VALUE)
                .createSshProxyServerConfiguration();
    }

    private Set<Capability> parseSchemasToModuleCapabilities(final SharedSchemaRepository consumer) {
        final Set<SourceIdentifier> loadedSources = new HashSet<>();
        consumer.registerSchemaSourceListener(TextToIRTransformer.create(consumer, consumer));
        consumer.registerSchemaSourceListener(new SchemaSourceListener() {
            @Override
            public void schemaSourceEncountered(final SchemaSourceRepresentation schemaSourceRepresentation) {

            }

            @Override
            public void schemaSourceRegistered(final Iterable<PotentialSchemaSource<?>> potentialSchemaSources) {
                for (final PotentialSchemaSource<?> potentialSchemaSource : potentialSchemaSources) {
                    loadedSources.add(potentialSchemaSource.getSourceIdentifier());
                }
            }

            @Override
            public void schemaSourceUnregistered(final PotentialSchemaSource<?> potentialSchemaSource) {

            }
        });

        if (configuration.getSchemasDir() != null) {
            LOG.info("Loading models from directory.");
            final FilesystemSchemaSourceCache<YangTextSchemaSource> cache = new FilesystemSchemaSourceCache<>(
                consumer, YangTextSchemaSource.class, configuration.getSchemasDir());
            consumer.registerSchemaSourceListener(cache);
        } else if (configuration.getModels() != null) {
            LOG.info("Loading models from classpath.");
            final SchemaSourceCache<YangTextSchemaSource> cache = new SchemaSourceCache<>(
                    consumer, YangTextSchemaSource.class, configuration.getModels());
            consumer.registerSchemaSourceListener(cache);
        } else {
            LOG.info("Custom module loading skipped.");
        }

        configuration.getDefaultYangResources().forEach(r -> {
            final SourceIdentifier sourceIdentifier = new SourceIdentifier(r.getModuleName(), r.getRevision());
            registerSource(consumer, r.getResourcePath(), sourceIdentifier);
        });

        try {
            //necessary for creating mdsal data stores and operations
            schemaContext = consumer.createEffectiveModelContextFactory()
                    .createEffectiveModelContext(loadedSources).get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(
                "Cannot parse schema context. Please read stack trace and check YANG files in schema directory.", e);
        }

        final Set<Capability> capabilities = new HashSet<>();

        for (final Module module : schemaContext.getModules()) {
            for (final Submodule subModule : module.getSubmodules()) {
                addModuleCapability(consumer, capabilities, subModule);
            }
            addModuleCapability(consumer, capabilities, module);
        }
        return capabilities;
    }

    private static void addModuleCapability(final SharedSchemaRepository consumer, final Set<Capability> capabilities,
                                            final ModuleLike module) {
        final var moduleNamespace = module.getNamespace().toString();
        final var moduleName = module.getName();
        final var revision = module.getRevision().map(Revision::toString).orElse(null);
        final var sourceId = new SourceIdentifier(moduleName, revision);

        final String moduleContent;
        try {
            moduleContent = consumer.getSchemaSource(sourceId, YangTextSchemaSource.class).get().read();
        } catch (ExecutionException | InterruptedException | IOException e) {
            throw new IllegalStateException(
                "Cannot retrieve schema source for module " + sourceId + " from schema repository", e);
        }

        capabilities.add(new YangModuleCapability(moduleNamespace, moduleName, revision, moduleContent));
    }

    private static void registerSource(final SharedSchemaRepository consumer, final String resource,
            final SourceIdentifier sourceId) {
        consumer.registerSchemaSource(sourceIdentifier -> Futures.immediateFuture(
            YangTextSchemaSource.forResource(NetconfDeviceSimulator.class, resource)),
            PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class,
                PotentialSchemaSource.Costs.IMMEDIATE.getValue()));
    }

    private static InetSocketAddress getAddress(final String ip, final int port) {
        try {
            return new InetSocketAddress(Inet4Address.getByName(ip), port);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve address " + ip, e);
        }
    }

    @Override
    public void close() {
        for (final SshProxyServer sshWrapper : sshWrappers) {
            try {
                sshWrapper.close();
            } catch (final IOException e) {
                LOG.debug("Wrapper {} failed to close", sshWrapper, e);
            }
        }
        for (final Channel deviceCh : devicesChannels) {
            deviceCh.close();
        }
        nettyThreadgroup.shutdownGracefully();
        minaTimerExecutor.shutdownNow();
        nioExecutor.shutdownNow();
    }
}
