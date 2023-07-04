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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
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

@Component(service = { CallHomeMountDispatcher.class, CallHomeNetconfSubsystemListener.class }, immediate = true)
// Non-final for testing
public class CallHomeMountDispatcher implements NetconfClientDispatcher, CallHomeNetconfSubsystemListener {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountDispatcher.class);

    private final CallHomeMountSessionManager sessionManager = new CallHomeMountSessionManager();
    private final String topologyId;
    private final EventExecutor eventExecutor;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final SchemaResourceManager schemaRepositoryProvider;
    private final DataBroker dataBroker;
    private final DOMMountPointService mountService;
    private final NetconfClientConfigurationBuilderFactory builderFactory;

    protected CallHomeTopology topology;

    private final DeviceActionFactory deviceActionFactory;
    private final BaseNetconfSchemas baseSchemas;


    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final NetconfClientConfigurationBuilderFactory builderFactory) {
        this(topologyId, eventExecutor, keepaliveExecutor, processingExecutor, schemaRepositoryProvider, baseSchemas,
            dataBroker, mountService, builderFactory, null);
    }

    @Activate
    public CallHomeMountDispatcher(
            @Reference(target = "(type=global-event-executor)") final EventExecutor eventExecutor,
            @Reference(target = "(type=global-netconf-ssh-scheduled-executor)")
                final ScheduledThreadPool keepaliveExecutor,
            @Reference(target = "(type=global-netconf-processing-executor)") final ThreadPool processingExecutor,
            @Reference final SchemaResourceManager schemaRepositoryProvider,
            @Reference final BaseNetconfSchemas baseSchemas, @Reference final DataBroker dataBroker,
            @Reference final DOMMountPointService mountService,
            @Reference final NetconfClientConfigurationBuilderFactory builderFactory,
            @Reference final DeviceActionFactory deviceActionFactory) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, eventExecutor, keepaliveExecutor, processingExecutor,
            schemaRepositoryProvider, baseSchemas, dataBroker, mountService, builderFactory, deviceActionFactory);
    }

    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory) {
        this.topologyId = topologyId;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.schemaRepositoryProvider = schemaRepositoryProvider;
        this.deviceActionFactory = deviceActionFactory;
        this.baseSchemas = requireNonNull(baseSchemas);
        this.dataBroker = dataBroker;
        this.mountService = mountService;
        this.builderFactory = requireNonNull(builderFactory);
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        return activateChannel(clientConfiguration);
    }

    private Future<NetconfClientSession> activateChannel(final NetconfClientConfiguration conf) {
        final InetSocketAddress remoteAddr = conf.getAddress();
        final CallHomeMountSessionContext context = sessionManager().getByAddress(remoteAddr);
        LOG.info("Activating NETCONF channel for ip {} device context {}", remoteAddr, context);
        return context == null ? new FailedFuture<>(eventExecutor, new NullPointerException())
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
        topology = new CallHomeTopology(topologyId, this, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountService, builderFactory, baseSchemas, deviceActionFactory);
    }

    @VisibleForTesting
    CallHomeMountSessionManager sessionManager() {
        return sessionManager;
    }
}
