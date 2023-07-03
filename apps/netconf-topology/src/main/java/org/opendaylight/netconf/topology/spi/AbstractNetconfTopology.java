/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    private final HashMap<NodeId, NetconfNodeHandler> activeConnectors = new HashMap<>();
    private final NetconfClientDispatcher clientDispatcher;
    private final EventExecutor eventExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final CredentialProvider credentialProvider;
    private final SslHandlerFactoryProvider sslHandlerFactoryProvider;
    private final SchemaResourceManager schemaManager;
    private final BaseNetconfSchemas baseSchemas;

    protected final ScheduledThreadPool keepaliveExecutor;
    protected final ListeningExecutorService processingExecutor;
    protected final DataBroker dataBroker;
    protected final DOMMountPointService mountPointService;
    protected final String topologyId;
    protected final AAAEncryptionService encryptionService;

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                                      final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                                      final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
                                      final DataBroker dataBroker, final DOMMountPointService mountPointService,
                                      final AAAEncryptionService encryptionService,
                                      final DeviceActionFactory deviceActionFactory,
                                      final BaseNetconfSchemas baseSchemas,
                                      final CredentialProvider credentialProvider,
                                      final SslHandlerFactoryProvider sslHandlerFactoryProvider) {
        this.topologyId = requireNonNull(topologyId);
        this.clientDispatcher = clientDispatcher;
        this.eventExecutor = eventExecutor;
        this.keepaliveExecutor = keepaliveExecutor;
        this.processingExecutor = MoreExecutors.listeningDecorator(processingExecutor.getExecutor());
        this.schemaManager = requireNonNull(schemaManager);
        this.deviceActionFactory = deviceActionFactory;
        this.dataBroker = requireNonNull(dataBroker);
        this.mountPointService = mountPointService;
        this.encryptionService = encryptionService;
        this.baseSchemas = requireNonNull(baseSchemas);
        this.credentialProvider = requireNonNull(credentialProvider);
        this.sslHandlerFactoryProvider = requireNonNull(sslHandlerFactoryProvider);

        // FIXME: this should be a put(), as we are initializing and will be re-populating the datastore with all the
        //        devices. Whatever has been there before should be nuked to properly re-align lifecycle.
        final var wtx = dataBroker.newWriteOnlyTransaction();
        wtx.merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build(), new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build());
        final var future = wtx.commit();
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Unable to initialize topology {}", topologyId, e);
            throw new IllegalStateException(e);
        }

        LOG.debug("Topology {} initialized", topologyId);
    }

    // Non-final for testing
    protected void ensureNode(final Node node) {
        lockedEnsureNode(node);
    }

    private synchronized void lockedEnsureNode(final Node node) {
        final var nodeId = node.requireNodeId();
        final var prev = activeConnectors.remove(nodeId);
        if (prev != null) {
            LOG.info("RemoteDevice{{}} was already configured, disconnecting", nodeId);
            prev.close();
        }

        LOG.info("Connecting RemoteDevice{{}}, with config {}", nodeId, hideCredentials(node));
        setupConnection(nodeId, node);
    }

    // Non-final for testing
    protected void deleteNode(final NodeId nodeId) {
        lockedDeleteNode(nodeId);
    }

    private synchronized void lockedDeleteNode(final NodeId nodeId) {
        final var nodeName = nodeId.getValue();
        LOG.debug("Disconnecting RemoteDevice{{}}", nodeName);

        final var connectorDTO = activeConnectors.remove(nodeId);
        if (connectorDTO != null) {
            connectorDTO.close();
        }
    }

    protected final synchronized void deleteAllNodes() {
        activeConnectors.values().forEach(NetconfNodeHandler::close);
        activeConnectors.clear();
    }

    @Holding("this")
    protected final void setupConnection(final NodeId nodeId, final Node configNode) {
        final var netconfNode = configNode.augmentation(NetconfNode.class);
        final var nodeOptional = configNode.augmentation(NetconfNodeAugmentedOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        // Instantiate the handler ...
        final var deviceId = NetconfNodeUtils.toRemoteDeviceId(nodeId, netconfNode);
        final var deviceSalFacade = createSalFacade(deviceId, netconfNode.requireLockDatastore());
        final var nodeHandler = new NetconfNodeHandler(clientDispatcher, eventExecutor, keepaliveExecutor.getExecutor(),
            baseSchemas, schemaManager, processingExecutor, deviceActionFactory,
            deviceSalFacade, deviceId, nodeId, netconfNode, nodeOptional, getClientConfig(netconfNode, nodeId));

        // ... record it ...
        activeConnectors.put(nodeId, nodeHandler);

        // ... and start it
        nodeHandler.connect();
    }

    protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, final boolean lockDatastore) {
        return new NetconfTopologyDeviceSalFacade(deviceId, mountPointService, lockDatastore, dataBroker);
    }

    @VisibleForTesting
    public NetconfClientConfigurationBuilder getClientConfig(final NetconfNode node, final NodeId nodeId) {
        final var builder  = NetconfClientConfigurationBuilder.create();

        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol.getName() == Name.TLS) {
            builder.withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withSslHandlerFactory(sslHandlerFactoryProvider.getSslHandlerFactory(protocol.getSpecification()));
        } else {
            throw new IllegalStateException("Unsupported protocol type: " + protocol.getName());
        }

        final var helloCapabilities = node.getOdlHelloMessageCapabilities();
        if (helloCapabilities != null) {
            builder.withOdlHelloCapabilities(List.copyOf(helloCapabilities.requireCapability()));
        }

        return builder
            .withName(nodeId.getValue())
            .withAddress(NetconfNodeUtils.toInetSocketAddress(node))
            .withConnectionTimeoutMillis(node.requireConnectionTimeoutMillis().toJava());
    }

    private AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials
                instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430
                    .credentials.credentials.LoginPassword loginPassword) {
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPwUnencrypted unencrypted) {
            final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        }
        if (credentials instanceof KeyAuth keyAuth) {
            final var keyPair = keyAuth.getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(), credentialProvider,
                encryptionService);
        }
        throw new IllegalStateException("Unsupported credential type: " + credentials.getClass());
    }

    /**
     * Hiding of private credentials from node configuration (credentials data is replaced by asterisks).
     *
     * @param nodeConfiguration Node configuration container.
     * @return String representation of node configuration with credentials replaced by asterisks.
     */
    @VisibleForTesting
    public static final String hideCredentials(final Node nodeConfiguration) {
        final var netconfNodeAugmentation = nodeConfiguration.augmentation(NetconfNode.class);
        final var nodeCredentials = netconfNodeAugmentation.getCredentials().toString();
        final var nodeConfigurationString = nodeConfiguration.toString();
        return nodeConfigurationString.replace(nodeCredentials, "***");
    }
}
