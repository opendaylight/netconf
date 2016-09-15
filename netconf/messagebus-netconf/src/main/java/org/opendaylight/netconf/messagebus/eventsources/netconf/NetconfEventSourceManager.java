/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetconfEventSourceManager implements DataChangeListener. On topology changes, it manages creation,
 * updating and removing registrations of event sources.
 */
public final class NetconfEventSourceManager implements DataChangeListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfEventSourceManager.class);
    private static final TopologyKey NETCONF_TOPOLOGY_KEY = new TopologyKey(
        new TopologyId(TopologyNetconf.QNAME.getLocalName()));
    private static final InstanceIdentifier<Node> NETCONF_DEVICE_PATH = InstanceIdentifier.create(NetworkTopology.class)
        .child(Topology.class, NETCONF_TOPOLOGY_KEY).child(Node.class);

    private Map<String, String> streamMap;
    private final ConcurrentHashMap<InstanceIdentifier<?>, NetconfEventSourceRegistration> registrationMap = new ConcurrentHashMap<>();
    private final DOMNotificationPublishService publishService;
    private final DOMMountPointService domMounts;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final EventSourceRegistry eventSourceRegistry;
    private final DataBroker dataBroker;

    public NetconfEventSourceManager(final DataBroker dataBroker,
                                     final DOMNotificationPublishService domPublish,
                                     final DOMMountPointService domMount,
                                     final EventSourceRegistry eventSourceRegistry) {
        Preconditions.checkNotNull(dataBroker);
        Preconditions.checkNotNull(domPublish);
        Preconditions.checkNotNull(domMount);
        Preconditions.checkNotNull(eventSourceRegistry);
        this.dataBroker = dataBroker;
        this.domMounts = domMount;
        this.publishService = domPublish;
        this.eventSourceRegistry = eventSourceRegistry;
    }

    /**
     * Invoke by blueprint
     */
    public void initialize() {
        Preconditions.checkNotNull(dataBroker);
        listenerRegistration = dataBroker
            .registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, NETCONF_DEVICE_PATH, this,
                DataChangeScope.SUBTREE);
        LOG.info("NetconfEventSourceManager initialized.");
    }

    @Override public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> event) {

        LOG.debug("[DataChangeEvent<InstanceIdentifier<?>, DataObject>: {}]", event);
        for (final Map.Entry<InstanceIdentifier<?>, DataObject> changeEntry : event.getCreatedData().entrySet()) {
            if (changeEntry.getValue() instanceof Node) {
                nodeCreated(changeEntry.getKey(), (Node) changeEntry.getValue());
            }
        }

        for (final Map.Entry<InstanceIdentifier<?>, DataObject> changeEntry : event.getUpdatedData().entrySet()) {
            if (changeEntry.getValue() instanceof Node) {
                nodeUpdated(changeEntry.getKey(), (Node) changeEntry.getValue());
            }
        }

        for (InstanceIdentifier<?> removePath : event.getRemovedPaths()) {
            DataObject removeObject = event.getOriginalData().get(removePath);
            if (removeObject instanceof Node) {
                nodeRemoved(removePath);
            }
        }

    }

    private void nodeCreated(final InstanceIdentifier<?> key, final Node node) {
        Preconditions.checkNotNull(key);
        if (!validateNode(node)) {
            LOG.warn("NodeCreated event : Node [{}] is null or not valid.", key.toString());
            return;
        }
        LOG.info("Netconf event source [{}] is creating...", key.toString());
        NetconfEventSourceRegistration nesr = NetconfEventSourceRegistration.create(key, node, this);
        if (nesr != null) {
            NetconfEventSourceRegistration nesrOld = registrationMap.put(key, nesr);
            if (nesrOld != null) {
                nesrOld.close();
            }
        }
    }

    private void nodeUpdated(final InstanceIdentifier<?> key, final Node node) {
        Preconditions.checkNotNull(key);
        if (!validateNode(node)) {
            LOG.warn("NodeUpdated event : Node [{}] is null or not valid.", key.toString());
            return;
        }

        LOG.info("Netconf event source [{}] is updating...", key.toString());
        NetconfEventSourceRegistration nesr = registrationMap.get(key);
        if (nesr != null) {
            nesr.updateStatus();
        } else {
            nodeCreated(key, node);
        }
    }

    private void nodeRemoved(final InstanceIdentifier<?> key) {
        Preconditions.checkNotNull(key);
        LOG.info("Netconf event source [{}] is removing...", key.toString());
        NetconfEventSourceRegistration nesr = registrationMap.remove(key);
        if (nesr != null) {
            nesr.close();
        }
    }

    private boolean validateNode(final Node node) {
        if (node == null) {
            return false;
        }
        return isNetconfNode(node);
    }

    public Map<String, String> getStreamMap() {
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
     * Invoke by blueprint
     * @param streamMap
     */
    public void setStreamMap(Map<String, String> streamMap) {
        this.streamMap = streamMap;
    }

    private boolean isNetconfNode(final Node node) {
        return node.getAugmentation(NetconfNode.class) != null;
    }

    @Override public void close() {
        listenerRegistration.close();
        for (final NetconfEventSourceRegistration reg : registrationMap.values()) {
            reg.close();
        }
        registrationMap.clear();
    }

}