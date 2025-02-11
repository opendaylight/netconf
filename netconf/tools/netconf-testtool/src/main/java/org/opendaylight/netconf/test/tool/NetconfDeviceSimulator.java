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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.netconf.server.ServerTransportInitializer;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.YangModuleCapability;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.UserAuthFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.test.tool.config.Configuration;
import org.opendaylight.netconf.test.tool.customrpc.SettableOperationProvider;
import org.opendaylight.netconf.test.tool.monitoring.NetconfMonitoringOperationServiceFactory;
import org.opendaylight.netconf.test.tool.operations.DefaultOperationsCreator;
import org.opendaylight.netconf.test.tool.operations.OperationsProvider;
import org.opendaylight.netconf.test.tool.rpchandler.SettableOperationRpcProvider;
import org.opendaylight.netconf.test.tool.schemacache.SchemaSourceCache;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.netconf.transport.ssh.ServerFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.server.rev240814.netconf.server.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.Submodule;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.fs.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.model.spi.source.URLYangTextSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.TextToIRTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSimulator implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSimulator.class);

    private final DefaultNetconfTimer timer = new DefaultNetconfTimer();
    private final Configuration configuration;
    private final List<TransportStack> servers;
    private final SSHTransportStackFactory sshStackFactory;
    private EffectiveModelContext schemaContext;

    private boolean sendFakeSchema = false;

    public NetconfDeviceSimulator(final Configuration configuration) {
        this.configuration = configuration;
        servers = new ArrayList<>(configuration.getDeviceCount());
        sshStackFactory = new SSHTransportStackFactory("netconf-device-simulator-threads",
            configuration.getThreadPoolSize());
    }

    private ServerTransportInitializer createTransportInitializer(final Set<Capability> capabilities,
            final YangTextSourceExtension sourceProvider) {
        final var transformedCapabilities = new HashSet<>(Collections2.transform(capabilities, input -> {
            if (sendFakeSchema) {
                sendFakeSchema = false;
                return new FakeCapability((YangModuleCapability) input);
            } else {
                return input;
            }
        }));
        transformedCapabilities.add(new BasicCapability(CapabilityURN.CANDIDATE));
        final var monitoringService1 = new DummyMonitoringService(transformedCapabilities);
        final var idProvider = new DefaultSessionIdProvider();

        final var aggregatedNetconfOperationServiceFactory = createOperationServiceFactory(
            sourceProvider, transformedCapabilities, monitoringService1, idProvider);

        return new ServerTransportInitializer(new TesttoolNegotiationFactory(timer,
            aggregatedNetconfOperationServiceFactory, idProvider,
            configuration.getGenerateConfigsTimeout(), monitoringService1, configuration.getCapabilities()));
    }

    private NetconfOperationServiceFactory createOperationServiceFactory(
            final YangTextSourceExtension sourceProvider,
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

        final var schemaRepo = new SharedSchemaRepository("netconf-simulator");
        final var capabilities = parseSchemasToModuleCapabilities(schemaRepo);
        final var transportInitializer = createTransportInitializer(capabilities,
            sourceIdentifier -> schemaRepo.getSchemaSource(sourceIdentifier, YangTextSource.class));

        final var ipAddress = getIpAddress(configuration);
        final var startingPort = getStartingPort(configuration);
        final var deviceCount = configuration.getDeviceCount();
        final var ports = IntStream.range(startingPort, Math.min(startingPort + deviceCount, 65536))
            .mapToObj(Integer::new).toList();

        final var openDevices = new ArrayList<Integer>(ports.size());
        final var configurator = configuration.isSsh() ? createServerFactoryManagerConfigurator(configuration) : null;

        LOG.debug("Ports: {}", ports);

        for (final int port : ports) {
            try {
                final var connectParams = connectionParams(ipAddress, port);
                final var serverFuture = configuration.isSsh()
                    ? sshStackFactory.listenServer(TransportConstants.SSH_SUBSYSTEM, transportInitializer,
                        connectParams, null, configurator)
                        : TCPServer.listen(transportInitializer, sshStackFactory.newServerBootstrap(), connectParams);
                servers.add(serverFuture.get());
                openDevices.add(port);
            } catch (UnsupportedConfigurationException | InterruptedException | ExecutionException e) {
                LOG.error("Could not start {} simulated device on port {}", proto, port, e);
                break;
            }
        }

        final var first = openDevices.get(0);
        final var last = openDevices.isEmpty() ? null : openDevices.get(openDevices.size() - 1);
        if (openDevices.size() == configuration.getDeviceCount()) {
            LOG.info("All simulated devices started successfully from port {} to {}", first, last);
        } else if (openDevices.isEmpty()) {
            LOG.warn("No simulated devices started.");
        } else {
            LOG.warn("Not all simulated devices started successfully. Started devices are on ports {} to {}",
                first, last);
        }
        return openDevices;
    }

    private static ServerFactoryManagerConfigurator createServerFactoryManagerConfigurator(
            final Configuration configuration) {
        final var authProvider = configuration.getAuthProvider();
        final var publicKeyAuthenticator = configuration.getPublickeyAuthenticator();
        return factoryManager -> {
            final var authFactoriesListBuilder = ImmutableList.<UserAuthFactory>builder();
            authFactoriesListBuilder.add(new UserAuthPasswordFactory());
            factoryManager.setPasswordAuthenticator(
                (usr, pass, session) -> authProvider.authenticated(usr, pass));
            if (publicKeyAuthenticator != null) {
                final var factory = new UserAuthPublicKeyFactory();
                factory.setSignatureFactories(factoryManager.getSignatureFactories());
                authFactoriesListBuilder.add(factory);
                factoryManager.setPublickeyAuthenticator(publicKeyAuthenticator);
            }
            factoryManager.setUserAuthFactories(authFactoriesListBuilder.build());
            factoryManager.setKeyPairProvider(new VirtualKeyPairProvider());
        };
    }

    private Set<Capability> parseSchemasToModuleCapabilities(final SharedSchemaRepository consumer) {
        final Set<SourceIdentifier> loadedSources = new HashSet<>();
        consumer.registerSchemaSourceListener(TextToIRTransformer.create(consumer, consumer));
        consumer.registerSchemaSourceListener(new SchemaSourceListener() {
            @Override
            public void schemaSourceEncountered(final SourceRepresentation schemaSourceRepresentation) {

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
            final FilesystemSchemaSourceCache<YangTextSource> cache = new FilesystemSchemaSourceCache<>(
                consumer, YangTextSource.class, configuration.getSchemasDir());
            consumer.registerSchemaSourceListener(cache);
        } else if (configuration.getModels() != null) {
            LOG.info("Loading models from classpath.");
            final SchemaSourceCache<YangTextSource> cache = new SchemaSourceCache<>(
                    consumer, YangTextSource.class, configuration.getModels());
            consumer.registerSchemaSourceListener(cache);
        } else {
            LOG.info("Custom module loading skipped.");
        }

        configuration.getDefaultYangResources().forEach(r -> {
            registerSource(consumer, r.resourcePath(), new SourceIdentifier(r.moduleName(), r.revision()));
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
        final var sourceId = new SourceIdentifier(moduleName);

        final String moduleContent;
        try {
            moduleContent = consumer.getSchemaSource(sourceId, YangTextSource.class).get().read();
        } catch (ExecutionException | InterruptedException | IOException e) {
            throw new IllegalStateException(
                "Cannot retrieve schema source for module " + sourceId + " from schema repository", e);
        }

        capabilities.add(new YangModuleCapability(moduleNamespace, moduleName, null, moduleContent));
    }

    private static void registerSource(final SharedSchemaRepository consumer, final String resource,
            final SourceIdentifier sourceId) {
        consumer.registerSchemaSource(sourceIdentifier -> Futures.immediateFuture(
            new URLYangTextSource(NetconfDeviceSimulator.class.getResource(resource))),
            PotentialSchemaSource.create(sourceId, YangTextSource.class,
                PotentialSchemaSource.Costs.IMMEDIATE.getValue()));
    }

    private static IpAddress getIpAddress(final Configuration configuration) {
        try {
            return IetfInetUtil.ipAddressFor(InetAddress.getByName(configuration.getIp()));
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve address " + configuration.getIp(), e);
        }
    }

    private static int getStartingPort(final Configuration configuration) {
        final int startingPort = configuration.getStartingPort();
        if (startingPort > 0 && startingPort < 65536) {
            return startingPort;
        }
        // find available port
        try {
            final var socket = new ServerSocket(0);
            final int port = socket.getLocalPort();
            socket.close();
            return port;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find available port", e);
        }
    }

    private static TcpServerGrouping connectionParams(final IpAddress address, final int port) {
        return new TcpServerParametersBuilder()
            .setLocalBind(BindingMap.of(new LocalBindBuilder()
                .setLocalAddress(address)
                .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                .build()))
            .build();
    }

    @Override
    public void close() {
        for (final var server : servers) {
            try {
                server.shutdown().get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.debug("Exception on simulated device shutdown", e);
            }
        }
        sshStackFactory.close();
    }
}
