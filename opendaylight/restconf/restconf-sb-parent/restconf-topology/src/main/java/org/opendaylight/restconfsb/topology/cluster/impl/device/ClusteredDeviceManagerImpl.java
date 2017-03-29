/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPoint;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterMountPointDown;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.mountpoint.RestconfDevice;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceId;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceInfo;
import org.opendaylight.restconfsb.mountpoint.RestconfMount;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;

public class ClusteredDeviceManagerImpl implements ClusteredDeviceManager {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredDeviceManagerImpl.class);

    private static final String TOPOLOGY_ID = "topology-restconf";

    private final RestconfDeviceId deviceId;
    private final ListeningExecutorService processingExecutor;
    private final ScheduledThreadPool reconnectExecutor;
    private final DOMMountPointService mountpointService;
    private final SenderFactory senderFactory;
    private final Timeout askTimeout;
    private final Node node;
    private RestconfMount mount;
    private RestconfFacadeActor masterFacade;
    private ActorSystem actorSystem;
    private Sender sender;
    private ListenableFuture<List<Module>> modulesFuture;
    private ActorRef notificationReceiver;

    public ClusteredDeviceManagerImpl(final Node node, final SenderFactory senderFactory, final ThreadPool processingExecutor,
                                      final ScheduledThreadPool reconnectExecutor, final DOMMountPointService mountpointService,
                                      final Timeout askTimeout) {
        this.node = node;
        LOG.info("{}: Creating clustered device manager", node.getNodeId().getValue());
        this.deviceId = new RestconfDeviceId(node.getNodeId().getValue());
        Preconditions.checkNotNull(node.getAugmentation(RestconfNode.class));
        this.processingExecutor = MoreExecutors.listeningDecorator(Preconditions.checkNotNull(processingExecutor).getExecutor());
        this.reconnectExecutor = Preconditions.checkNotNull(reconnectExecutor);
        this.mountpointService = Preconditions.checkNotNull(mountpointService);
        this.askTimeout = Preconditions.checkNotNull(askTimeout);
        this.senderFactory = senderFactory;
    }

    @Override
    public synchronized void registerMasterMountPoint(final ActorSystem actorSystem, final ActorContext context) {
        LOG.info("{}: Registering master mountpoint", deviceId.getNodeName());
        this.actorSystem = actorSystem;
        LOG.warn("Creating master facade for device {}", deviceId.getNodeName());
        try {
            final ClusteredRestconfDevice device = ClusteredRestconfDevice.createMasterDevice(deviceId, getSender(), modulesFuture.get(), reconnectExecutor);

            final TypedProps<RestconfFacadeActor> props = new TypedProps<>(RestconfFacadeActor.class, new Creator<RestconfFacadeActor>() {
                @Override
                public RestconfFacadeActorImpl create() throws Exception {
                    return new RestconfFacadeActorImpl(device.getFacade());
                }
            }).withTimeout(askTimeout);
            masterFacade = TypedActor.get(context).typedActorOf(props);
            LOG.info("Master facade registered on path {}", TypedActor.get(actorSystem).getActorRefFor(masterFacade).path());

            final Cluster cluster = Cluster.get(actorSystem);
            final Iterable<Member> members = cluster.state().getMembers();
            final ActorRef masterFacadeRef = TypedActor.get(actorSystem).getActorRefFor(masterFacade);
            mount = new RestconfMount(device);
            mount.register(mountpointService);
            for (final Member member : members) {
                if (!member.address().equals(cluster.selfAddress())) {
                    final String path = member.address() + "/user/" + TOPOLOGY_ID + "/" + deviceId.getNodeName();
                    actorSystem.actorSelection(path).resolveOne(askTimeout).onComplete(new OnComplete<ActorRef>() {
                        @Override
                        public void onComplete(final Throwable failure, final ActorRef success) throws Throwable {
                            if (failure == null) {
                                success.tell(new AnnounceMasterMountPoint(), masterFacadeRef);
                            } else {
                                LOG.error("Cant resolve peer actor.", failure);
                            }
                        }
                    }, actorSystem.dispatcher());
                }
            }
        } catch (NodeConnectionException | InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }

    }

    @Override
    public synchronized void registerSlaveMountPoint(final ActorSystem actorSystem, final ActorContext context, final ActorRef masterRef) {
        LOG.info("{}: Registering slave mountpoint", deviceId.getNodeName());
        this.actorSystem = actorSystem;
        Futures.addCallback(modulesFuture, new FutureCallback<List<Module>>() {
            @Override
            public void onSuccess(@Nullable final List<Module> result) {
                synchronized (ClusteredDeviceManagerImpl.this) {
                    if (sender != null) {
                        try {
                            LOG.debug("Close sender");
                            sender.close();
                            sender = null;
                        } catch (final Exception e) {
                            LOG.warn("{}: Can't close sender: {}", deviceId.getNodeName(), e);
                        }
                    }

                    if (notificationReceiver != null) {
                        stopNotificationReceiver();
                    }
                    final TypedProps<RestconfFacadeActorImpl> props =
                            new TypedProps<>(RestconfFacadeActor.class, RestconfFacadeActorImpl.class).withTimeout(askTimeout);
                    final RestconfFacadeActor masterFacadeActor =
                            TypedActor.get(actorSystem).typedActorOf(props, masterRef);
                    LOG.warn("Creating slave facade for device {}", deviceId.getNodeName());
                    final NotificationAdapter adapter = new NotificationAdapter();
                    final ProxyRestconfFacade facade = new ProxyRestconfFacade(masterFacadeActor, context, adapter);
                    notificationReceiver = context.actorOf(SlaveNotificationReceiver.create(adapter));
                    LOG.info("Notification receiver address: {}", notificationReceiver.path().toStringWithoutAddress());
                    masterFacadeActor.subscribe(notificationReceiver);
                    try {
                        Preconditions.checkNotNull(result);
                        final RestconfDevice device = ClusteredRestconfDevice.createSlaveDevice(deviceId, facade, result);
                        mount = new RestconfMount(device);
                        mount.register(mountpointService);
                    } catch (final NodeConnectionException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                t.printStackTrace();
            }
        });


    }

    @Override
    public synchronized void unregisterMountPoint() {
        LOG.info("{}: Unregistering mountpoint", deviceId.getNodeName());
        if (notificationReceiver != null) {
            stopNotificationReceiver();
        }
        if (mount != null) {
            mount.deregister();
            mount = null;
        }
        if (masterFacade != null) {
            final ActorRef sender = TypedActor.get(actorSystem).getActorRefFor(masterFacade);
            LOG.debug("Stopping master facade for device {}", deviceId.getNodeName());
            for (final Member member : Cluster.get(actorSystem).state().getMembers()) {
                if (member.address().equals(Cluster.get(actorSystem).selfAddress())) {
                    continue;
                }
                actorSystem.actorSelection(member.address() + "/user/" + TOPOLOGY_ID + "/" + deviceId.getNodeName())
                        .tell(new AnnounceMasterMountPointDown(), sender);
            }
            TypedActor.get(actorSystem).stop(masterFacade);
            masterFacade = null;
        }
    }

    private void stopNotificationReceiver() {
        try {
            Await.result(Patterns.gracefulStop(notificationReceiver, askTimeout.duration()), askTimeout.duration());
            notificationReceiver = null;
        } catch (final Exception e) {
            LOG.warn("Failed to stop notification receiver", e);
        }
    }

    @Override
    public synchronized ListenableFuture<List<Module>> getSupportedModules(final RestconfNode restconfNode) {
        modulesFuture = processingExecutor.submit(new Callable<List<Module>>() {
            @Override
            public List<Module> call() throws Exception {
                sender = getSender();
                final RestconfDeviceInfo monitoring = new RestconfDeviceInfo(sender);
                return ImmutableList.copyOf(monitoring.getModules(restconfNode));
            }
        });
        return modulesFuture;
    }

    private Sender getSender() throws NodeConnectionException {
        if (sender == null) {
            sender = senderFactory.createSender(node, reconnectExecutor.getExecutor());
        }
        return sender;
    }

}
