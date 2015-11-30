/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.japi.Creator;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPoint;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyMountPointFacade implements AutoCloseable, RemoteDeviceHandler<NetconfSessionPreferences> {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyMountPointFacade.class);

    private static final String MOUNT_POINT = "mountpoint";

    private final String topologyId;
    private final RemoteDeviceId id;
    private final Broker domBroker;
    private final BindingAwareBroker bindingBroker;
    private final long defaultRequestTimeoutMillis;

    private SchemaContext remoteSchemaContext = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private DOMRpcService deviceRpc = null;
    private final ClusteredNetconfDeviceMountInstanceProxy salProvider;

    private ActorSystem actorSystem;
    private DOMDataBroker deviceDataBroker = null;

    private final ArrayList<RemoteDeviceHandler<NetconfSessionPreferences>> connectionStatusListeners = new ArrayList<>();

    public TopologyMountPointFacade(final String topologyId,
                                    final RemoteDeviceId id,
                                    final Broker domBroker,
                                    final BindingAwareBroker bindingBroker,
                                    long defaultRequestTimeoutMillis) {
        this.topologyId = topologyId;
        this.id = id;
        this.domBroker = domBroker;
        this.bindingBroker = bindingBroker;
        this.defaultRequestTimeoutMillis = defaultRequestTimeoutMillis;
        this.salProvider = new ClusteredNetconfDeviceMountInstanceProxy(id);
        registerToSal(domBroker);
    }

    public void registerToSal(final Broker domRegistryDependency) {
        domRegistryDependency.registerProvider(salProvider);
    }

    @Override
    public void onDeviceConnected(final SchemaContext remoteSchemaContext,
                                  final NetconfSessionPreferences netconfSessionPreferences,
                                  final DOMRpcService deviceRpc) {
        // prepare our prerequisites for mountpoint
        this.remoteSchemaContext = remoteSchemaContext;
        this.netconfSessionPreferences = netconfSessionPreferences;
        this.deviceRpc = deviceRpc;
        for (RemoteDeviceHandler<NetconfSessionPreferences> listener : connectionStatusListeners) {
            listener.onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc);
        }
    }

    @Override
    public void onDeviceDisconnected() {
        // do not unregister mount point here, this gets handle by the underlying call from role change callback
        for (RemoteDeviceHandler<NetconfSessionPreferences> listener : connectionStatusListeners) {
            listener.onDeviceDisconnected();
        }
    }

    @Override
    public void onDeviceFailed(Throwable throwable) {
        // do not unregister mount point here, this gets handle by the underlying call from role change callback
        for (RemoteDeviceHandler<NetconfSessionPreferences> listener : connectionStatusListeners) {
            listener.onDeviceFailed(throwable);
        }
    }

    @Override
    public void onNotification(DOMNotification domNotification) {
        salProvider.getMountInstance().publish(domNotification);
    }

    public void registerMountPoint(final ActorSystem actorSystem, final ActorContext context) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(remoteSchemaContext, "Device has no remote schema context yet. Probably not fully connected.");
        Preconditions.checkNotNull(netconfSessionPreferences, "Device has no capabilities yet. Probably not fully connected.");
        this.actorSystem = actorSystem;
        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        LOG.warn("Creating master data broker for device {}", id);
        deviceDataBroker = TypedActor.get(context).typedActorOf(new TypedProps<>(ProxyNetconfDeviceDataBroker.class, new Creator<NetconfDeviceMasterDataBroker>() {
            @Override
            public NetconfDeviceMasterDataBroker create() throws Exception {
                return new NetconfDeviceMasterDataBroker(actorSystem, id, remoteSchemaContext, deviceRpc, netconfSessionPreferences, defaultRequestTimeoutMillis);
            }
        }), MOUNT_POINT);
        LOG.warn("Master data broker registered on path {}", TypedActor.get(actorSystem).getActorRefFor(deviceDataBroker).path());
        salProvider.getMountInstance().onTopologyDeviceConnected(remoteSchemaContext, deviceDataBroker, deviceRpc, notificationService);
        final Cluster cluster = Cluster.get(actorSystem);
        final Iterable<Member> members = cluster.state().getMembers();
        final ActorRef deviceDataBrokerRef = TypedActor.get(actorSystem).getActorRefFor(deviceDataBroker);
        for (final Member member : members) {
            if (!member.address().equals(cluster.selfAddress())) {
                final String path = member.address() + "/user/" + topologyId + "/" + id.getName();
                actorSystem.actorSelection(path).tell(new AnnounceMasterMountPoint(), deviceDataBrokerRef);
            }
        }
    }

    public void registerMountPoint(final ActorSystem actorSystem, final ActorContext context, final ActorRef masterRef) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(remoteSchemaContext, "Device has no remote schema context yet. Probably not fully connected.");
        Preconditions.checkNotNull(netconfSessionPreferences, "Device has no capabilities yet. Probably not fully connected.");
        this.actorSystem = actorSystem;
        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        LOG.warn("Creating a proxy for master data broker");
        final ProxyNetconfDeviceDataBroker masterDataBroker = TypedActor.get(actorSystem).typedActorOf(new TypedProps<>(ProxyNetconfDeviceDataBroker.class, NetconfDeviceMasterDataBroker.class), masterRef);
        LOG.warn("Creating slave data broker for device {}", id);
        final DOMDataBroker deviceDataBroker = new NetconfDeviceSlaveDataBroker(actorSystem, id, masterDataBroker);
        salProvider.getMountInstance().onTopologyDeviceConnected(remoteSchemaContext, deviceDataBroker, deviceRpc, notificationService);
    }

    public void unregisterMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
        if (deviceDataBroker != null) {
            LOG.warn("Stopping master data broker for device {}", id.getName());
            TypedActor.get(actorSystem).stop(deviceDataBroker);
            deviceDataBroker = null;
        }
    }

    public ConnectionStatusListenerRegistration registerConnectionStatusListener(final RemoteDeviceHandler<NetconfSessionPreferences> listener) {
        connectionStatusListeners.add(listener);
        return new ConnectionStatusListenerRegistration(listener);
    }

    @Override
    public void close() {
        closeGracefully(salProvider);
    }

    private void closeGracefully(final AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (final Exception e) {
                LOG.warn("{}: Ignoring exception while closing {}", id, resource, e);
            }
        }
    }

    public class ConnectionStatusListenerRegistration{

        private final RemoteDeviceHandler<NetconfSessionPreferences> listener;

        public ConnectionStatusListenerRegistration(final RemoteDeviceHandler<NetconfSessionPreferences> listener) {
            this.listener = listener;
        }

        public void close() {
            connectionStatusListeners.remove(listener);
        }
    }
}
