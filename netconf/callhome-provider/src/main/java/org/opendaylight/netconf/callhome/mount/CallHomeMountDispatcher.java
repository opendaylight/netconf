/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import static java.util.Objects.requireNonNull;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.callhome.mount.CallHomeMountSessionContext.CloseCallback;
import org.opendaylight.netconf.callhome.protocol.CallHomeChannelActivator;
import org.opendaylight.netconf.callhome.protocol.CallHomeNetconfSubsystemListener;
import org.opendaylight.netconf.callhome.protocol.CallHomeProtocolSessionContext;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MountPointManager;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallHomeMountDispatcher implements NetconfClientDispatcher, CallHomeNetconfSubsystemListener {

    private static final Logger LOG = LoggerFactory.getLogger(CallHomeMountDispatcher.class);

    private final String topologyId;
    private final EventExecutor eventExecutor;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final SchemaResourceManager schemaRepositoryProvider;
    private final CallHomeMountSessionManager sessionManager;
    private final DataBroker dataBroker;
    private final DOMMountPointService mountService;
    private final AAAEncryptionService encryptionService;
    private final MountPointManager mountPointManager;

    protected CallHomeTopology topology;

    private final CloseCallback onCloseHandler = deviceContext -> {
        LOG.info("Removing {} from Netconf Topology.", deviceContext.getId());
        topology.disconnectNode(deviceContext.getId());
    };

    private final DeviceActionFactory deviceActionFactory;
    private final BaseNetconfSchemas baseSchemas;

    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
                                   final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                   final SchemaResourceManager schemaRepositoryProvider,
                                   final BaseNetconfSchemas baseSchemas, final DataBroker dataBroker,
                                   final DOMMountPointService mountService,
                                   final AAAEncryptionService encryptionService,
                                   final MountPointManager mountPointManager) {
        this(topologyId, eventExecutor, keepaliveExecutor, processingExecutor, schemaRepositoryProvider, baseSchemas,
            dataBroker, mountService, encryptionService, null, mountPointManager);
    }

    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
            final MountPointManager mountPointManager) {
        this.topologyId = topologyId;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.schemaRepositoryProvider = schemaRepositoryProvider;
        this.deviceActionFactory = deviceActionFactory;
        this.sessionManager = new CallHomeMountSessionManager();
        this.baseSchemas = requireNonNull(baseSchemas);
        this.dataBroker = dataBroker;
        this.mountService = mountService;
        this.encryptionService = encryptionService;
        this.mountPointManager = mountPointManager;
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        return activateChannel(clientConfiguration);
    }

    @Override
    public Future<Void> createReconnectingClient(final NetconfReconnectingClientConfiguration clientConfiguration) {
        return activateChannel(clientConfiguration);
    }

    private <V> Future<V> activateChannel(final NetconfClientConfiguration conf) {
        final InetSocketAddress remoteAddr = conf.getAddress();
        final CallHomeMountSessionContext context = getSessionManager().getByAddress(remoteAddr);
        LOG.info("Activating NETCONF channel for ip {} device context {}", remoteAddr, context);
        if (context == null) {
            return new FailedFuture<>(eventExecutor, new NullPointerException());
        }
        return context.activateNetconfChannel(conf.getSessionListener());
    }

    void createTopology() {
        this.topology = new CallHomeTopology(topologyId, this, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountService, encryptionService, baseSchemas,
                deviceActionFactory, mountPointManager);
    }

    @Override
    public void onNetconfSubsystemOpened(final CallHomeProtocolSessionContext session,
                                         final CallHomeChannelActivator activator) {
        final CallHomeMountSessionContext deviceContext =
                getSessionManager().createSession(session, activator, onCloseHandler);
        if (deviceContext != null) {
            final NodeId nodeId = deviceContext.getId();
            final Node configNode = deviceContext.getConfigNode();
            LOG.info("Provisioning fake config {}", configNode);
            topology.connectNode(nodeId, configNode);
        }
    }

    public CallHomeMountSessionManager getSessionManager() {
        return sessionManager;
    }
}
