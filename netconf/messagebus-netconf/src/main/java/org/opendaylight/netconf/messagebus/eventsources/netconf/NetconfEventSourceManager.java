/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetconfEventSourceManager implements DataChangeListener. On topology changes, it manages creation,
 * updating and removing registrations of event sources.
 */
@Deprecated(forRemoval = true)
public final class NetconfEventSourceManager implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfEventSourceManager.class);
    private static final TopologyKey NETCONF_TOPOLOGY_KEY = new TopologyKey(
            new TopologyId(TopologyNetconf.QNAME.getLocalName()));
    private static final InstanceIdentifier<Node> NETCONF_DEVICE_PATH = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, NETCONF_TOPOLOGY_KEY).child(Node.class);

    private Map<String, String> streamMap;
    private final ConcurrentHashMap<InstanceIdentifier<?>, NetconfEventSourceRegistration> registrationMap =
            new ConcurrentHashMap<>();
    private final DOMNotificationPublishService publishService;
    private final DOMMountPointService domMounts;
    private ListenerRegistration<NetconfEventSourceManager> listenerRegistration;
    private final EventSourceRegistry eventSourceRegistry;
    private final DataBroker dataBroker;
    private final BindingNormalizedNodeSerializer serializer;

    public NetconfEventSourceManager(final DataBroker dataBroker,
                                     final BindingNormalizedNodeSerializer serializer,
                                     final DOMNotificationPublishService domPublish,
                                     final DOMMountPointService domMount,
                                     final EventSourceRegistry eventSourceRegistry) {
        this.dataBroker = requireNonNull(dataBroker);
        this.serializer = requireNonNull(serializer);
        this.domMounts = requireNonNull(domMount);
        this.publishService = requireNonNull(domPublish);
        this.eventSourceRegistry = requireNonNull(eventSourceRegistry);
    }

    /**
     * Invoked by blueprint.
     */
    public void initialize() {
        listenerRegistration = verifyNotNull(dataBroker).registerDataTreeChangeListener(DataTreeIdentifier.create(
                LogicalDatastoreType.OPERATIONAL, NETCONF_DEVICE_PATH), this);
        LOG.info("NetconfEventSourceManager initialized.");
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change: changes) {
            LOG.debug("DataTreeModification: {}", change);
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final InstanceIdentifier<Node> identifier = change.getRootPath().getRootIdentifier();
            switch (rootNode.getModificationType()) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    nodeCreated(identifier, rootNode.getDataAfter());
                    break;
                case DELETE:
                    nodeRemoved(identifier);
                    break;
                default:
                    break;
            }
        }
    }

    private void nodeCreated(final InstanceIdentifier<?> key, final Node node) {
        if (!validateNode(node)) {
            LOG.warn("NodeCreated event : Node [{}] is null or not valid.", key);
            return;
        }
        LOG.info("Netconf event source [{}] is creating...", key);
        NetconfEventSourceRegistration nesr = NetconfEventSourceRegistration.create(serializer, requireNonNull(key),
            node, this);
        if (nesr != null) {
            NetconfEventSourceRegistration nesrOld = registrationMap.put(key, nesr);
            if (nesrOld != null) {
                nesrOld.close();
            }
        }
    }

    private void nodeRemoved(final InstanceIdentifier<?> key) {
        LOG.info("Netconf event source [{}] is removing...", key);
        NetconfEventSourceRegistration nesr = registrationMap.remove(requireNonNull(key));
        if (nesr != null) {
            nesr.close();
        }
    }

    private static boolean validateNode(final Node node) {
        return node == null ? false : isNetconfNode(node);
    }

    Map<String, String> getStreamMap() {
        return streamMap;
    }

    DOMNotificationPublishService getPublishService() {
        return publishService;
    }

    DOMMountPointService getDomMounts() {
        return domMounts;
    }

    EventSourceRegistry getEventSourceRegistry() {
        return eventSourceRegistry;
    }

    /**
     * Invoked by blueprint.
     *
     * @param streamMap Stream map
     */
    public void setStreamMap(final Map<String, String> streamMap) {
        this.streamMap = streamMap;
    }

    private static boolean isNetconfNode(final Node node) {
        return node.augmentation(NetconfNode.class) != null;
    }

    @Override
    public void close() {
        listenerRegistration.close();
        for (final NetconfEventSourceRegistration reg : registrationMap.values()) {
            reg.close();
        }
        registrationMap.clear();
    }
}
