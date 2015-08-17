/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
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
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.NetconfStateSchemas;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.KeepaliveSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.SchemaRepositoryProvider;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;
import org.opendaylight.protocol.framework.TimedReconnectStrategy;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

public class NetconfTopologyImpl implements NetconfTopology, AutoCloseable {

    private final String topologyId;
    private final NetconfClientDispatcher clientDispatcher;
    private final BindingAwareBroker bindingAwareBroker;
    private final Broker domBroker;
    private final EventExecutor eventExecutor;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final SchemaRepositoryProvider sharedSchemaRepository;
    private SchemaSourceRegistry schemaSourceRegistry;
    private SchemaContextFactory schemaContextFactory;

    // TODO these will need to be retrieved from the configuration for each individual node, which requires model change
    private long defaultRequestTimeoutMilis;
    private long keepaliveDelay;
    private boolean shouldSendKeepalives;
    private boolean reconnectOnChangedSchema;
    private Long maxConnectionAttempts;
    private int betweenAteemptsTimeoutMilis;
    private BigDecimal sleepFactor;
    private String username;
    private String password;
    private boolean tcpOnly;

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                               final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                               final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                               final ThreadPool processingExecutor, final SchemaRepositoryProvider sharedSchemaRepository) {
        this.topologyId = topologyId;
        this.clientDispatcher = clientDispatcher;
        this.bindingAwareBroker = bindingAwareBroker;
        this.domBroker = domBroker;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = processingExecutor;
        this.sharedSchemaRepository = sharedSchemaRepository;
    }

    @Override
    public void close() throws Exception {
        //NOOP
    }

    @Override
    public String getTopologyId() {
        return topologyId;
    }

    @Override
    public ListenableFuture<Void> connectNode(NodeId nodeId, Node configNode) {
        // TODO keep a map of all open connections
        return createConnection(nodeId, configNode);
    }

    @Override
    public ListenableFuture<Void> disconnectNode(NodeId nodeId) {
        // retrieve connection, and disconnect it
        return null;
    }

    private ListenableFuture<Void> createConnection(final NodeId nodeId, final Node configNode) {
        NetconfNode node = configNode.getAugmentation(NetconfNode.class);
        IpAddress ipAddress = node.getHost().getIpAddress();
        InetSocketAddress address = new InetSocketAddress(ipAddress.getIpv4Address() != null ?
                ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue(),
                node.getPort().getValue());
        RemoteDeviceId remoteDeviceId = new RemoteDeviceId(nodeId.getValue(), address);

        RemoteDeviceHandler<NetconfSessionPreferences> salFacade =
                new NetconfDeviceSalFacade(remoteDeviceId, domBroker, bindingAwareBroker, defaultRequestTimeoutMilis);
        if (shouldSendKeepalives) {
            salFacade = new KeepaliveSalFacade(remoteDeviceId, salFacade, keepaliveExecutor.getExecutor(), keepaliveDelay);
        }

        NetconfDevice.SchemaResourcesDTO schemaResourcesDTO =
                new NetconfDevice.SchemaResourcesDTO(schemaSourceRegistry, schemaContextFactory, new NetconfStateSchemas.NetconfStateSchemasResolverImpl());

        NetconfDevice device = new NetconfDevice(schemaResourcesDTO, remoteDeviceId, salFacade, processingExecutor.getExecutor(), reconnectOnChangedSchema);

        NetconfDeviceCommunicator listener = new NetconfDeviceCommunicator(remoteDeviceId, device);

        final NetconfReconnectingClientConfiguration clientConfig = getClientConfig(listener, node.getHost(), node.getPort().getValue());
        return listener.initializeRemoteConnection(clientDispatcher, clientConfig);
    }

    public NetconfReconnectingClientConfiguration getClientConfig(final NetconfDeviceCommunicator listener, Host host, int port) {
        final InetSocketAddress socketAddress = getSocketAddress(host, port);
        final long clientConnectionTimeoutMillis = defaultRequestTimeoutMilis;

        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor,
                maxConnectionAttempts, betweenAteemptsTimeoutMilis, sleepFactor);
        final ReconnectStrategy strategy = sf.createReconnectStrategy();

        return NetconfReconnectingClientConfigurationBuilder.create()
                .withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(strategy)
                .withAuthHandler(new LoginPassword(username, password))
                .withProtocol(tcpOnly ?
                        NetconfClientConfiguration.NetconfClientProtocol.TCP :
                        NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withConnectStrategyFactory(sf)
                .withSessionListener(listener)
                .build();
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

    private InetSocketAddress getSocketAddress(final Host host, int port) {
        if(host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        } else {
            final IpAddress ipAddress = host.getIpAddress();
            final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue() : ipAddress.getIpv6Address().getValue();
            return new InetSocketAddress(ip, port);
        }
    }
}
