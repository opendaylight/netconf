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
import org.opendaylight.netconf.nettyutil.ReconnectFuture;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final AAAEncryptionService encryptionService;

    protected CallHomeTopology topology;

    private final CloseCallback onCloseHandler = deviceContext -> {
        final var nodeId = deviceContext.getId();
        LOG.info("Removing {} from Netconf Topology.", nodeId);
        topology.disconnectNode(nodeId);
    };

    private final DeviceActionFactory deviceActionFactory;
    private final BaseNetconfSchemas baseSchemas;

    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
                                   final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                   final SchemaResourceManager schemaRepositoryProvider,
                                   final BaseNetconfSchemas baseSchemas, final DataBroker dataBroker,
                                   final DOMMountPointService mountService,
                                   final AAAEncryptionService encryptionService) {
        this(topologyId, eventExecutor, keepaliveExecutor, processingExecutor, schemaRepositoryProvider, baseSchemas,
            dataBroker, mountService, encryptionService, null);
    }

    public CallHomeMountDispatcher(final String topologyId, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final BaseNetconfSchemas baseSchemas,
            final DataBroker dataBroker, final DOMMountPointService mountService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory) {
        this.topologyId = topologyId;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.schemaRepositoryProvider = schemaRepositoryProvider;
        this.deviceActionFactory = deviceActionFactory;
        this.baseSchemas = requireNonNull(baseSchemas);
        this.dataBroker = dataBroker;
        this.mountService = mountService;
        this.encryptionService = encryptionService;
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        return activateChannel(clientConfiguration);
    }

    @Override
    public ReconnectFuture createReconnectingClient(final NetconfReconnectingClientConfiguration clientConfiguration) {
        return new SingleReconnectFuture(eventExecutor, activateChannel(clientConfiguration));
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
        final var deviceContext = sessionManager().createSession(session, activator, onCloseHandler);
        if (deviceContext != null) {
            final NodeId nodeId = deviceContext.getId();
            final Node configNode = deviceContext.getConfigNode();
            LOG.info("Provisioning fake config {}", configNode);
            topology.connectNode(nodeId, configNode);
        }
    }

    @VisibleForTesting
    void createTopology() {
        topology = new CallHomeTopology(topologyId, this, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountService, encryptionService, baseSchemas,
                deviceActionFactory);
    }

    @VisibleForTesting
    CallHomeMountSessionManager sessionManager() {
        return sessionManager;
    }
}
