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
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchangeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.TopologyTypes1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.TopologyTypes1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.network.topology.topology.topology.types.TopologyNetconfBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypes;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.TopologyTypesBuilder;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNetconfTopology {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfTopology.class);

    //TODO: should be list of mina-sshd defaults
    //          Where to put this??
    public static final List<SshKeyExchangeAlgorithm> DEFAULT_KEY_EXCHANGE_ALGORITHMS =
        List.of(new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Sntrup761x25519Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Mlkem768x25519Sha256),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Mlkem1024nistp384Sha384),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Mlkem768nistp256Sha256),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Curve25519Sha256),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.Curve448Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.EcdhSha2Nistp521),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.EcdhSha2Nistp384),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.EcdhSha2Nistp256),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroupExchangeSha256),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroup18Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroup17Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroup16Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroup15Sha512),
            new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                .iana.ssh.key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.DiffieHellmanGroup14Sha256));

    private final HashMap<NodeId, NetconfNodeHandler> activeConnectors = new HashMap<>();
    private final NetconfClientFactory clientFactory;
    private final DeviceActionFactory deviceActionFactory;
    private final SchemaResourceManager schemaManager;
    private final BaseNetconfSchemaProvider baseSchemaProvider;
    private final NetconfClientConfigurationBuilderFactory builderFactory;
    private final NetconfTimer timer;

    protected final NetconfTopologySchemaAssembler schemaAssembler;
    protected final DataBroker dataBroker;
    protected final DOMMountPointService mountPointService;
    protected final String topologyId;

    private SshTransportTopologyParameters sshParams;
    private Registration dtclReg;

    protected AbstractNetconfTopology(final String topologyId, final NetconfClientFactory clientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaManager, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory, final BaseNetconfSchemaProvider baseSchemaProvider) {
        this.topologyId = requireNonNull(topologyId);
        this.clientFactory = requireNonNull(clientFactory);
        this.timer = requireNonNull(timer);
        this.schemaAssembler = requireNonNull(schemaAssembler);
        this.schemaManager = requireNonNull(schemaManager);
        this.deviceActionFactory = deviceActionFactory;
        this.dataBroker = requireNonNull(dataBroker);
        this.mountPointService = mountPointService;
        this.builderFactory = requireNonNull(builderFactory);
        this.baseSchemaProvider = requireNonNull(baseSchemaProvider);

        // FIXME: this should be a put(), as we are initializing and will be re-populating the datastore with all the
        //        devices. Whatever has been there before should be nuked to properly re-align lifecycle.

        // TODO: make this attribute, and be updatable trough TreeChangeListener?
        this.sshParams = defaultSshParams();
        final var config = new TopologyTypes1Builder()
            .setTopologyNetconf(new TopologyNetconfBuilder()
                .setSshTransportTopologyParameters(sshParams)
                .build())
            .build();

        final var wtx = dataBroker.newWriteOnlyTransaction();
        wtx.merge(LogicalDatastoreType.OPERATIONAL, DataObjectIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build(), new TopologyBuilder()
                        .setTopologyId(new TopologyId(topologyId))
                        .setTopologyTypes(new TopologyTypesBuilder().addAugmentation(config).build())
                        .build());

        //TODO: close this or find better place where to put this
        final var dtclReg = dataBroker.registerTreeChangeListener(LogicalDatastoreType.CONFIGURATION,
            DataObjectReference.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(TopologyTypes.class)
                .augmentation(TopologyTypes1.class)
                .child(TopologyNetconf.class)
                .child(SshTransportTopologyParameters.class)
                .build(), new SshParamsConfig());

        final var future = wtx.commit();
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Unable to initialize topology {}", topologyId, e);
            throw new IllegalStateException(e);
        }

        LOG.info("Topology {} initialized", topologyId);
    }

    static SshTransportTopologyParameters defaultSshParams() {
        return new SshTransportTopologyParametersBuilder()
            .setKeyExchange(new KeyExchangeBuilder()
                .setKeyExchangeAlg(DEFAULT_KEY_EXCHANGE_ALGORITHMS)
                //TODO: add others
                .build())
            .build();
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
        final var netconfNodeAugment = node.augmentation(NetconfNodeAugment.class);
        final var netconfNode = netconfNodeAugment != null ? netconfNodeAugment.getNetconfNode() : null;
        if (netconfNode == null) {
            LOG.warn("RemoteDevice{{}} is missing NETCONF node configuration, not connecting it", nodeId);
            return;
        }
        final RemoteDeviceId deviceId;
        try {
            deviceId = NetconfNodeUtils.toRemoteDeviceId(nodeId, netconfNode);
        } catch (NoSuchElementException e) {
            LOG.warn("RemoteDevice{{}} has invalid configuration, not connecting it", nodeId, e);
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Connecting RemoteDevice{{}}, with config {}", nodeId, hideCredentials(node));
        }

        // Instantiate the handler ...
        final var nodeOptional = node.augmentation(NetconfNodeAugmentedOptional.class);
        final var deviceSalFacade = createSalFacade(deviceId, netconfNode.getCredentials(),
            netconfNode.requireLockDatastore());

        final NetconfNodeHandler nodeHandler = new NetconfNodeHandler(clientFactory, timer, baseSchemaProvider,
            schemaManager, schemaAssembler, builderFactory, deviceActionFactory, deviceSalFacade, deviceId, nodeId,
            netconfNode, nodeOptional, sshParams);

        // ... record it ...
        activeConnectors.put(nodeId, nodeHandler);

        // ... and start it
        nodeHandler.connect();
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

    protected RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, final Credentials credentials,
            final boolean lockDatastore) {
        return new NetconfTopologyDeviceSalFacade(deviceId, credentials,  mountPointService, lockDatastore, dataBroker);
    }

    /**
     * Hiding of private credentials from node configuration (credentials data is replaced by asterisks).
     *
     * @param nodeConfiguration Node configuration container.
     * @return String representation of node configuration with credentials replaced by asterisks.
     */
    @VisibleForTesting
    static final String hideCredentials(final Node nodeConfiguration) {
        final var nodeConfigurationString = nodeConfiguration.toString();
        final var netconfNodeAugmentation = nodeConfiguration.augmentation(NetconfNodeAugment.class);
        final var netconfNode = netconfNodeAugmentation != null ? netconfNodeAugmentation.getNetconfNode() : null;
        if (netconfNode != null && netconfNode.getCredentials() != null) {
            final var nodeCredentials = netconfNode.getCredentials().toString();
            return nodeConfigurationString.replace(nodeCredentials, "***");
        }
        return nodeConfigurationString;
    }

    private final class SshParamsConfig implements DataTreeChangeListener<SshTransportTopologyParameters> {

        @Override
        public void onDataTreeChanged(@NonNull List<DataTreeModification<SshTransportTopologyParameters>> changes) {
            LOG.info("Topology config change.");
            for (var change : changes) {
                final var rootNode = change.getRootNode();
                final var modType = rootNode.modificationType();
                switch (modType) {
                    case SUBTREE_MODIFIED, WRITE -> {
                        sshParams = rootNode.dataAfter();
                        final var wtx = dataBroker.newWriteOnlyTransaction();
                        wtx.put(LogicalDatastoreType.OPERATIONAL, DataObjectIdentifier.builder(NetworkTopology.class)
                            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                            .child(TopologyTypes.class)
                            .augmentation(TopologyTypes1.class)
                            .child(TopologyNetconf.class)
                            .child(SshTransportTopologyParameters.class)
                            .build(), sshParams);
                        wtx.commit();
                        LOG.info("Topology config change. Write");
                    }
                    case DELETE -> {
                        //TODO: is this correct??
                        sshParams = defaultSshParams();
                        final var wtx = dataBroker.newWriteOnlyTransaction();
                        wtx.put(LogicalDatastoreType.OPERATIONAL, DataObjectIdentifier.builder(NetworkTopology.class)
                            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                            .child(TopologyTypes.class)
                            .augmentation(TopologyTypes1.class)
                            .child(TopologyNetconf.class)
                            .child(SshTransportTopologyParameters.class)
                            .build(), sshParams);
                        wtx.commit();
                        LOG.info("Topology config change. Delete");
                    }
                    default -> LOG.debug("Unsupported modification type: {}.", modType);
                }
            }
        }
    }
}
