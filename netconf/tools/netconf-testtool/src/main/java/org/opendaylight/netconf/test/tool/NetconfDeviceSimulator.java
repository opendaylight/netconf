/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.opendaylight.controller.config.util.capability.BasicCapability;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.config.util.capability.YangModuleCapability;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.auth.AuthProvider;
import org.opendaylight.netconf.impl.NetconfServerDispatcherImpl;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.monitoring.osgi.NetconfMonitoringActivator;
import org.opendaylight.netconf.monitoring.osgi.NetconfMonitoringOperationService;
import org.opendaylight.netconf.ssh.SshProxyServer;
import org.opendaylight.netconf.ssh.SshProxyServerConfiguration;
import org.opendaylight.netconf.ssh.SshProxyServerConfigurationBuilder;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.opendaylight.yangtools.yang.parser.util.TextToASTTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSimulator implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSimulator.class);

    private final NioEventLoopGroup nettyThreadgroup;
    private final HashedWheelTimer hashedWheelTimer;
    private final List<Channel> devicesChannels = Lists.newArrayList();
    private final List<SshProxyServer> sshWrappers = Lists.newArrayList();
    private final ScheduledExecutorService minaTimerExecutor;
    private final ExecutorService nioExecutor;
    private SchemaContext schemaContext;

    private boolean sendFakeSchema = false;

    public NetconfDeviceSimulator(final int ThreadPoolSize) {
        this(new NioEventLoopGroup(), new HashedWheelTimer(),
                Executors.newScheduledThreadPool(ThreadPoolSize, new ThreadFactoryBuilder().setNameFormat("netconf-ssh-server-mina-timers-%d").build()),
                ThreadUtils.newFixedThreadPool("netconf-ssh-server-nio-group", ThreadPoolSize));
    }

    private NetconfDeviceSimulator(final NioEventLoopGroup eventExecutors, final HashedWheelTimer hashedWheelTimer, final ScheduledExecutorService minaTimerExecutor, final ExecutorService nioExecutor) {
        this.nettyThreadgroup = eventExecutors;
        this.hashedWheelTimer = hashedWheelTimer;
        this.minaTimerExecutor = minaTimerExecutor;
        this.nioExecutor = nioExecutor;
    }

    private NetconfServerDispatcherImpl createDispatcher(final Set<Capability> capabilities, final boolean exi, final int generateConfigsTimeout,
                                                         final Optional<File> notificationsFile, final boolean mdSal, final Optional<File> initialConfigXMLFile,
                                                         final SchemaSourceProvider<YangTextSchemaSource> sourceProvider) {

        final Set<Capability> transformedCapabilities = Sets.newHashSet(Collections2.transform(capabilities, new Function<Capability, Capability>() {
            @Override
            public Capability apply(final Capability input) {
                if (sendFakeSchema) {
                    sendFakeSchema = false;
                    return new FakeCapability((YangModuleCapability) input);
                } else {
                    return input;
                }
            }
        }));

        final SessionIdProvider idProvider = new SessionIdProvider();

        final AggregatedNetconfOperationServiceFactory aggregatedNetconfOperationServiceFactory = new AggregatedNetconfOperationServiceFactory();
        final NetconfOperationServiceFactory operationProvider = mdSal ? new MdsalOperationProvider(idProvider, transformedCapabilities, schemaContext, sourceProvider) :
                new SimulatedOperationProvider(idProvider, transformedCapabilities, notificationsFile, initialConfigXMLFile);

        transformedCapabilities.add(new BasicCapability("urn:ietf:params:netconf:capability:candidate:1.0"));

        final NetconfMonitoringService monitoringService1 = new DummyMonitoringService(transformedCapabilities);

        final NetconfMonitoringActivator.NetconfMonitoringOperationServiceFactory monitoringService =
                new NetconfMonitoringActivator.NetconfMonitoringOperationServiceFactory(
                        new NetconfMonitoringOperationService(monitoringService1));
        aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(operationProvider);
        aggregatedNetconfOperationServiceFactory.onAddNetconfOperationServiceFactory(monitoringService);

        final Set<String> serverCapabilities = exi
                ? NetconfServerSessionNegotiatorFactory.DEFAULT_BASE_CAPABILITIES
                : Sets.newHashSet(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0, XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1);

        final NetconfServerSessionNegotiatorFactory serverNegotiatorFactory = new TesttoolNegotiationFactory(
                hashedWheelTimer, aggregatedNetconfOperationServiceFactory, idProvider, generateConfigsTimeout, monitoringService1, serverCapabilities);

        final NetconfServerDispatcherImpl.ServerChannelInitializer serverChannelInitializer = new NetconfServerDispatcherImpl.ServerChannelInitializer(
                serverNegotiatorFactory);
        return new NetconfServerDispatcherImpl(serverChannelInitializer, nettyThreadgroup, nettyThreadgroup);
    }

    public List<Integer> start(final TesttoolParameters params) {
        LOG.info("Starting {}, {} simulated devices starting on port {}", params.deviceCount, params.ssh ? "SSH" : "TCP", params.startingPort);

        final SharedSchemaRepository schemaRepo = new SharedSchemaRepository("netconf-simulator");
        final Set<Capability> capabilities = parseSchemasToModuleCapabilities(params, schemaRepo);

        final NetconfServerDispatcherImpl dispatcher = createDispatcher(capabilities, params.exi, params.generateConfigsTimeout,
                Optional.fromNullable(params.notificationFile), params.mdSal, Optional.fromNullable(params.initialConfigXMLFile),
                new SchemaSourceProvider<YangTextSchemaSource>() {
                    @Override
                    public CheckedFuture<? extends YangTextSchemaSource, SchemaSourceException> getSource(final SourceIdentifier sourceIdentifier) {
                        return schemaRepo.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);
                    }
                });

        int currentPort = params.startingPort;

        final List<Integer> openDevices = Lists.newArrayList();

        // Generate key to temp folder
        final KeyPairProvider keyPairProvider = getPemGeneratorHostKeyProvider();

        for (int i = 0; i < params.deviceCount; i++) {
            if (currentPort > 65535) {
                LOG.warn("Port cannot be greater than 65535, stopping further attempts.");
                break;
            }
            final InetSocketAddress address = getAddress(params.ip, currentPort);

            final ChannelFuture server;
            if(params.ssh) {
                final InetSocketAddress bindingAddress = InetSocketAddress.createUnresolved("0.0.0.0", currentPort);
                final LocalAddress tcpLocalAddress = new LocalAddress(address.toString());

                server = dispatcher.createLocalServer(tcpLocalAddress);
                try {
                    final SshProxyServer sshServer = new SshProxyServer(minaTimerExecutor, nettyThreadgroup, nioExecutor);
                    sshServer.bind(getSshConfiguration(bindingAddress, tcpLocalAddress, keyPairProvider));
                    sshWrappers.add(sshServer);
                } catch (final BindException e) {
                    LOG.warn("Cannot start simulated device on {}, port already in use. Skipping.", address);
                    // Close local server and continue
                    server.cancel(true);
                    if(server.isDone()) {
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
                    throw new RuntimeException(e);
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
                    throw new RuntimeException(e);
                } catch (final ExecutionException e) {
                    LOG.warn("Cannot start tcp simulated device on {}, skipping", address, e);
                    continue;
                }

                LOG.debug("Simulated TCP device started on {}", address);
            }

            devicesChannels.add(server.channel());
            openDevices.add(currentPort - 1);
        }

        if(openDevices.size() == params.deviceCount) {
            LOG.info("All simulated devices started successfully from port {} to {}", params.startingPort, currentPort - 1);
        } else if (openDevices.size() == 0) {
            LOG.warn("No simulated devices started.");
        } else {
            LOG.warn("Not all simulated devices started successfully. Started devices ar on ports {}", openDevices);
        }

        return openDevices;
    }

    private static SshProxyServerConfiguration getSshConfiguration(final InetSocketAddress bindingAddress,
            final LocalAddress tcpLocalAddress, final KeyPairProvider keyPairProvider) {
        return new SshProxyServerConfigurationBuilder()
                .setBindingAddress(bindingAddress)
                .setLocalAddress(tcpLocalAddress)
                .setAuthenticator(new AuthProvider() {
                    @Override
                    public boolean authenticated(final String username, final String password) {
                        return true;
                    }
                })
                .setKeyPairProvider(keyPairProvider)
                .setIdleTimeout(Integer.MAX_VALUE)
                .createSshProxyServerConfiguration();
    }

    private static KeyPairProvider getPemGeneratorHostKeyProvider() {
        try {
            final Path tempFile = Files.createTempFile("tempKeyNetconfTest", "suffix");
            return SecurityUtils.createGeneratorHostKeyProvider(tempFile.toAbsolutePath());
        } catch (final IOException e) {
            LOG.error("Unable to generate PEM key", e);
            throw new RuntimeException("Unable to generate PEM key", e);
        }
    }

    private Set<Capability> parseSchemasToModuleCapabilities(final TesttoolParameters params, final SharedSchemaRepository consumer) {
        final Set<SourceIdentifier> loadedSources = Sets.newHashSet();

        consumer.registerSchemaSourceListener(TextToASTTransformer.create(consumer, consumer));

        consumer.registerSchemaSourceListener(new SchemaSourceListener() {
            @Override
            public void schemaSourceEncountered(final SchemaSourceRepresentation schemaSourceRepresentation) {}

            @Override
            public void schemaSourceRegistered(final Iterable<PotentialSchemaSource<?>> potentialSchemaSources) {
                for (final PotentialSchemaSource<?> potentialSchemaSource : potentialSchemaSources) {
                    loadedSources.add(potentialSchemaSource.getSourceIdentifier());
                }
            }

            @Override
            public void schemaSourceUnregistered(final PotentialSchemaSource<?> potentialSchemaSource) {}
        });

        if(params.schemasDir != null) {
            final FilesystemSchemaSourceCache<YangTextSchemaSource> cache = new FilesystemSchemaSourceCache<>(consumer, YangTextSchemaSource.class, params.schemasDir);
            consumer.registerSchemaSourceListener(cache);
        }

        addDefaultSchemas(consumer);

        try {
            //necessary for creating mdsal data stores and operations
            this.schemaContext = consumer.createSchemaContextFactory(
                SchemaSourceFilter.ALWAYS_ACCEPT)
                .createSchemaContext(loadedSources).checkedGet();
        } catch (final SchemaResolutionException e) {
            throw new RuntimeException("Cannot parse schema context", e);
        }

        final Set<Capability> capabilities = Sets.newHashSet();

        for (final Module module : schemaContext.getModules()) {
            for (final Module subModule : module.getSubmodules()) {
                addModuleCapability(consumer, capabilities, subModule);
            }
            addModuleCapability(consumer, capabilities, module);
        }
        return capabilities;
    }

    private static void addModuleCapability(final SharedSchemaRepository consumer, final Set<Capability> capabilities,
            final Module module) {
        final SourceIdentifier moduleSourceIdentifier = SourceIdentifier.create(module.getName(),
                (SimpleDateFormatUtil.DEFAULT_DATE_REV == module.getRevision() ? Optional.<String>absent() :
                        Optional.of(SimpleDateFormatUtil.getRevisionFormat().format(module.getRevision()))));
        try {
            final String moduleContent = new String(consumer.getSchemaSource(moduleSourceIdentifier, YangTextSchemaSource.class)
                    .checkedGet().read());
            capabilities.add(new YangModuleCapability(module, moduleContent));
            //IOException would be thrown in creating SchemaContext already
        } catch (SchemaSourceException |IOException e) {
            throw new RuntimeException("Cannot retrieve schema source for module " + moduleSourceIdentifier.toString() + " from schema repository", e);
        }
    }

    private static void addDefaultSchemas(final SharedSchemaRepository consumer) {
        SourceIdentifier sId = RevisionSourceIdentifier.create("ietf-netconf-monitoring", "2010-10-04");
        registerSource(consumer, "/META-INF/yang/ietf-netconf-monitoring.yang", sId);

        sId = RevisionSourceIdentifier.create("ietf-netconf-monitoring-extension", "2013-12-10");
        registerSource(consumer, "/META-INF/yang/ietf-netconf-monitoring-extension.yang", sId);

        sId = RevisionSourceIdentifier.create("ietf-yang-types", "2013-07-15");
        registerSource(consumer, "/META-INF/yang/ietf-yang-types@2013-07-15.yang", sId);

        sId = RevisionSourceIdentifier.create("ietf-inet-types", "2013-07-15");
        registerSource(consumer, "/META-INF/yang/ietf-inet-types@2013-07-15.yang", sId);
    }

    private static void registerSource(final SharedSchemaRepository consumer, final String resource,
            final SourceIdentifier sourceId) {
        consumer.registerSchemaSource(new SchemaSourceProvider<SchemaSourceRepresentation>() {
            @Override
            public CheckedFuture<? extends SchemaSourceRepresentation, SchemaSourceException> getSource(final SourceIdentifier sourceIdentifier) {
                return Futures.immediateCheckedFuture(new YangTextSchemaSource(sourceId) {
                    @Override
                    protected MoreObjects.ToStringHelper addToStringAttributes(final MoreObjects.ToStringHelper toStringHelper) {
                        return toStringHelper;
                    }

                    @Override
                    public InputStream openStream() throws IOException {
                        return getClass().getResourceAsStream(resource);
                    }
                });
            }
        }, PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class, PotentialSchemaSource.Costs.IMMEDIATE.getValue()));
    }

    private static InetSocketAddress getAddress(final String ip, final int port) {
        try {
            return new InetSocketAddress(Inet4Address.getByName(ip), port);
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        for (final SshProxyServer sshWrapper : sshWrappers) {
            try {
                sshWrapper.close();
            } catch (IOException e) {
                LOG.error("Failed to close wrapper {}", sshWrapper, e);
            }
        }
        for (final Channel deviceCh : devicesChannels) {
            deviceCh.close();
        }
        nettyThreadgroup.shutdownGracefully();
        minaTimerExecutor.shutdownNow();
        nioExecutor.shutdownNow();
        // close Everything
    }
}
