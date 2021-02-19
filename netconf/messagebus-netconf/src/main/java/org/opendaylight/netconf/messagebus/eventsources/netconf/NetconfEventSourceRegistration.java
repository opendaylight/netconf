/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistration;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to keep connection status of netconf node  and event source registration object.
 */
@Deprecated(forRemoval = true)
final class NetconfEventSourceRegistration implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfEventSourceRegistration.class);
    private static final YangInstanceIdentifier NETCONF_DEVICE_DOM_PATH = YangInstanceIdentifier.builder()
            .node(NetworkTopology.QNAME).node(Topology.QNAME)
            .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), TopologyNetconf.QNAME
                    .getLocalName())
            .node(Node.QNAME).build();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id");
    private static final String NOTIFICATION_CAPABILITY_PREFIX = "(urn:ietf:params:xml:ns:netconf:notification";

    private final Node node;
    private final NetconfEventSourceManager netconfEventSourceManager;
    private final BindingNormalizedNodeSerializer serializer;

    private ConnectionStatus currentNetconfConnStatus;
    private EventSourceRegistration<NetconfEventSource> eventSourceRegistration;

    private NetconfEventSourceRegistration(final BindingNormalizedNodeSerializer serializer, final Node node,
            final NetconfEventSourceManager netconfEventSourceManager) {
        this.serializer = requireNonNull(serializer);
        this.node = node;
        this.netconfEventSourceManager = netconfEventSourceManager;
        this.eventSourceRegistration = null;
        this.currentNetconfConnStatus = ConnectionStatus.Connecting;
    }

    static NetconfEventSourceRegistration create(final BindingNormalizedNodeSerializer serializer,
            final InstanceIdentifier<?> instanceIdent, final Node node,
            final NetconfEventSourceManager netconfEventSourceManager) {
        requireNonNull(instanceIdent);
        requireNonNull(netconfEventSourceManager);
        if (!isEventSource(node)) {
            return null;
        }
        NetconfEventSourceRegistration nesr = new NetconfEventSourceRegistration(serializer, node,
            netconfEventSourceManager);
        nesr.updateStatus();
        LOG.debug("NetconfEventSourceRegistration for node {} has been initialized...", node.getNodeId().getValue());
        return nesr;
    }

    private static boolean isEventSource(final Node node) {
        final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
        if (netconfNode == null) {
            return false;
        }
        if (netconfNode.getAvailableCapabilities() == null) {
            return false;
        }
        final List<AvailableCapability> capabilities = netconfNode.getAvailableCapabilities().getAvailableCapability();
        if (capabilities == null || capabilities.isEmpty()) {
            return false;
        }
        for (final AvailableCapability capability : netconfNode.getAvailableCapabilities().getAvailableCapability()) {
            if (capability.getCapability().startsWith(NOTIFICATION_CAPABILITY_PREFIX)) {
                return true;
            }
        }

        return false;
    }

    Optional<EventSourceRegistration<NetconfEventSource>> getEventSourceRegistration() {
        return Optional.ofNullable(eventSourceRegistration);
    }

    NetconfNode getNetconfNode() {
        return node.augmentation(NetconfNode.class);
    }

    void updateStatus() {
        ConnectionStatus netconfConnStatus = getNetconfNode().getConnectionStatus();
        LOG.info("Change status on node {}, new status is {}", this.node.getNodeId().getValue(), netconfConnStatus);
        if (netconfConnStatus.equals(currentNetconfConnStatus)) {
            return;
        }
        changeStatus(netconfConnStatus);
    }

    private static boolean checkConnectionStatusType(final ConnectionStatus status) {
        return status == ConnectionStatus.Connected || status == ConnectionStatus.Connecting
                || status == ConnectionStatus.UnableToConnect;
    }

    private void changeStatus(final ConnectionStatus newStatus) {
        requireNonNull(newStatus);
        checkState(this.currentNetconfConnStatus != null);
        if (!checkConnectionStatusType(newStatus)) {
            throw new IllegalStateException("Unknown new Netconf Connection Status");
        }
        switch (this.currentNetconfConnStatus) {
            case Connecting:
            case UnableToConnect:
                if (newStatus == ConnectionStatus.Connected) {
                    if (this.eventSourceRegistration == null) {
                        registrationEventSource();
                    } else {
                        // reactivate stream on registered event source (invoke publish notification about connection)
                        this.eventSourceRegistration.getInstance().reActivateStreams();
                    }
                }
                break;
            case Connected:
                if (newStatus == ConnectionStatus.Connecting || newStatus == ConnectionStatus.UnableToConnect) {
                    // deactivate streams on registered event source (invoke publish notification about connection)
                    this.eventSourceRegistration.getInstance().deActivateStreams();
                }
                break;
            default:
                throw new IllegalStateException("Unknown current Netconf Connection Status");
        }
        this.currentNetconfConnStatus = newStatus;
    }

    private void registrationEventSource() {
        final Optional<DOMMountPoint> domMountPoint = netconfEventSourceManager.getDomMounts()
                .getMountPoint(domMountPath(node.getNodeId()));
        EventSourceRegistration<NetconfEventSource> registration = null;
        if (domMountPoint.isPresent()/* && mountPoint.isPresent()*/) {
            NetconfEventSourceMount mount = new NetconfEventSourceMount(serializer, node, domMountPoint.get());
            final NetconfEventSource netconfEventSource = new NetconfEventSource(
                    netconfEventSourceManager.getStreamMap(),
                    mount,
                    netconfEventSourceManager.getPublishService());
            registration = netconfEventSourceManager.getEventSourceRegistry().registerEventSource(netconfEventSource);
            LOG.info("Event source {} has been registered", node.getNodeId().getValue());
        }
        this.eventSourceRegistration = registration;
    }

    private static YangInstanceIdentifier domMountPath(final NodeId nodeId) {
        return YangInstanceIdentifier.builder(NETCONF_DEVICE_DOM_PATH)
                .nodeWithKey(Node.QNAME, NODE_ID_QNAME, nodeId.getValue()).build();
    }

    private void closeEventSourceRegistration() {
        if (getEventSourceRegistration().isPresent()) {
            getEventSourceRegistration().get().close();
        }
    }

    @Override
    public void close() {
        closeEventSourceRegistration();
    }

}
