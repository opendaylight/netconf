/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.Timer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { CallHomeMountFactory.class, CallHomeNetconfSubsystemListener.class }, immediate = true)
// Non-final for testing
public class CallHomeMountFactory implements NetconfClientFactory, CallHomeNetconfSubsystemListener {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountFactory.class);

    private final CallHomeMountSessionManager sessionManager = new CallHomeMountSessionManager();
    private final String topologyId;
    private final Timer timer;
    private final ScheduledExecutorService scheduledExecutor;
    private final Executor processingExecutor;
    private final SchemaResourceManager schemaRepositoryProvider;
    private final DataBroker dataBroker;
    private final DOMMountPointService mountService;
    private final NetconfClientConfigurationBuilderFactory builderFactory;

    protected CallHomeTopology topology;

    private final DeviceActionFactory deviceActionFactory;
    private final BaseNetconfSchemas baseSchemas;

    public CallHomeMountFactory(final String topologyId, final Timer timer,
            final ScheduledExecutorService scheduledExecutor, final Executor processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final NetconfClientConfigurationBuilderFactory builderFactory) {
        this(topologyId, timer, scheduledExecutor, processingExecutor, schemaRepositoryProvider, baseSchemas,
            dataBroker, mountService, builderFactory, null);
    }

    @Activate
    public CallHomeMountFactory(@Reference(target = "(type=global-timer)") final Timer timer,
            @Reference(target = "(type=global-netconf-ssh-scheduled-executor)")
                final ScheduledThreadPool scheduledThreadPool,
            @Reference(target = "(type=global-netconf-processing-executor)") final ThreadPool processingThreadPool,
            @Reference final SchemaResourceManager schemaRepositoryProvider,
            @Reference final BaseNetconfSchemas baseSchemas, @Reference final DataBroker dataBroker,
            @Reference final DOMMountPointService mountService,
            @Reference(target = "(type=legacy)") final NetconfClientConfigurationBuilderFactory builderFactory,
            @Reference final DeviceActionFactory deviceActionFactory) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, timer, scheduledThreadPool.getExecutor(),
            processingThreadPool.getExecutor(), schemaRepositoryProvider, baseSchemas, dataBroker, mountService,
            builderFactory, deviceActionFactory);
    }

    public CallHomeMountFactory(final String topologyId, final Timer timer,
            final ScheduledExecutorService scheduledExecutor, final Executor processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory) {
        this.topologyId = topologyId;
        this.timer = requireNonNull(timer);
        this.scheduledExecutor = scheduledExecutor;
        this.processingExecutor = processingExecutor;
        this.schemaRepositoryProvider = schemaRepositoryProvider;
        this.deviceActionFactory = deviceActionFactory;
        this.baseSchemas = requireNonNull(baseSchemas);
        this.dataBroker = dataBroker;
        this.mountService = mountService;
        this.builderFactory = requireNonNull(builderFactory);
    }

    @Deprecated
    @Override
    public ListenableFuture<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        return activateChannel(clientConfiguration);
    }

    private ListenableFuture<NetconfClientSession> activateChannel(final NetconfClientConfiguration conf) {
        final InetSocketAddress remoteAddr = conf.getAddress();
        final CallHomeMountSessionContext context = sessionManager().getByAddress(remoteAddr);
        LOG.info("Activating NETCONF channel for ip {} device context {}", remoteAddr, context);
        return context == null ? Futures.immediateFailedFuture(new NullPointerException("context is null"))
            : context.activateNetconfChannel(conf.getSessionListener());
    }

    @Override
    public void onNetconfSubsystemOpened(final CallHomeProtocolSessionContext session,
                                         final CallHomeChannelActivator activator) {
        final var deviceContext = sessionManager().createSession(session, activator, device -> {
            final var nodeId = device.getId();
            LOG.info("Removing {} from Netconf Topology.", nodeId);
            topology.disconnectNode(nodeId);
        });
        if (deviceContext != null) {
            final Node configNode = deviceContext.getConfigNode();
            LOG.info("Provisioning fake config {}", configNode);
            topology.connectNode(configNode);
        }
    }

    @VisibleForTesting
    void createTopology() {
        topology = new CallHomeTopology(topologyId, this, timer, scheduledExecutor, processingExecutor,
            schemaRepositoryProvider, dataBroker, mountService, builderFactory, baseSchemas, deviceActionFactory);
    }

    @VisibleForTesting
    CallHomeMountSessionManager sessionManager() {
        return sessionManager;
    }

    @Override
    public void close() throws Exception {
        // no action
    }
}
