/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.japi.Creator;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

public final class BaseNodeManager implements NodeManager {

    private static final Logger LOG = LoggerFactory.getLogger(BaseNodeManager.class);

    private final String nodeId;
    private final NodeManagerCallback delegate;

    private BaseNodeManager(final String nodeId,
                            final String topologyId,
                            final ActorSystem actorSystem,
                            final NodeManagerCallbackFactory delegateFactory,
                            final RoleChangeStrategy roleChangeStrategy) {
        LOG.debug("Creating BaseNodeManager, id: {}, {}", topologyId, nodeId );
        this.nodeId = nodeId;
        this.delegate = delegateFactory.create(nodeId, topologyId, actorSystem);
        // if we want to override the place election happens,
        // we need to override this with noop election strategy and implement election in callback
        // cannot leak "this" here! have to use TypedActor.self()
        roleChangeStrategy.registerRoleCandidate((NodeManager) TypedActor.self());
    }

    @Nonnull @Override public Node getInitialState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        LOG.trace("Retrieving Node {} initial state", nodeId);
        return delegate.getInitialState(nodeId, configNode);
    }

    @Nonnull @Override public Node getFailedState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        LOG.trace("Retrieving Node {} failed state", nodeId);
        return delegate.getFailedState(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Node> onNodeCreated(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        LOG.debug("Creating Node {}, with configuration: {}", nodeId.getValue(), configNode);
        return delegate.onNodeCreated(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Node> onNodeUpdated(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        LOG.debug("Updating Node {}, with configuration: {}", nodeId.getValue(), configNode);
        return delegate.onNodeUpdated(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Void> onNodeDeleted(@Nonnull final NodeId nodeId) {
        LOG.debug("Deleting Node {}", nodeId.getValue());
        return delegate.onNodeDeleted(nodeId);
    }

    @Nonnull
    @Override
    public ListenableFuture<Node> getCurrentStatusForNode(@Nonnull NodeId nodeId) {
        LOG.debug("Getting current status for node: {}", nodeId.getValue());
        return delegate.getCurrentStatusForNode(nodeId);
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        LOG.debug("Node {} role has changed from: {} to {}", nodeId,
                (roleChangeDTO.wasOwner() ? "master" : "slave"),
                (roleChangeDTO.isOwner() ? "master" : "slave"));

        delegate.onRoleChanged(roleChangeDTO);
    }

    @Override
    public void onReceive(Object o, ActorRef actorRef) {
        delegate.onReceive(o, actorRef);
    }

    @Override
    public Future<NormalizedNodeMessage> onRemoteNodeCreated(final NormalizedNodeMessage message) {
        return null;
    }

    @Override
    public Future<Void> onRemoteNodeDeleted(final NodeId nodeId) {
        return null;
    }

    @Override
    public Future<NormalizedNodeMessage> remoteGetCurrentStatusForNode(final NodeId nodeId) {
        return null;
    }

    @Override
    public void onDeviceConnected(SchemaContext remoteSchemaContext, NetconfSessionPreferences netconfSessionPreferences, DOMRpcService deviceRpc) {
        delegate.onDeviceConnected(remoteSchemaContext, netconfSessionPreferences, deviceRpc);
    }

    @Override
    public void onDeviceDisconnected() {
        delegate.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(Throwable throwable) {
        delegate.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(DOMNotification domNotification) {
        delegate.onNotification(domNotification);
    }

    @Override
    public void close() {
        // NOOP
    }

    /**
     * Builder of BaseNodeManager instances that are proxied as TypedActors
     */
    public static class BaseNodeManagerBuilder {
        private String nodeId;
        private String topologyId;
        private NodeManagerCallbackFactory delegateFactory;
        private RoleChangeStrategy roleChangeStrategy;
        private ActorContext actorContext;


        public BaseNodeManagerBuilder setNodeId(final String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public BaseNodeManagerBuilder setTopologyId(final String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        public BaseNodeManagerBuilder setDelegateFactory(final NodeManagerCallbackFactory delegateFactory) {
            this.delegateFactory = delegateFactory;
            return this;
        }

        public BaseNodeManagerBuilder setRoleChangeStrategy(final RoleChangeStrategy roleChangeStrategy) {
            this.roleChangeStrategy = roleChangeStrategy;
            return this;
        }

        public BaseNodeManagerBuilder setActorContext(final ActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        public NodeManager build() {
            Preconditions.checkNotNull(nodeId);
            Preconditions.checkNotNull(topologyId);
            Preconditions.checkNotNull(delegateFactory);
            Preconditions.checkNotNull(roleChangeStrategy);
            Preconditions.checkNotNull(actorContext);
            LOG.debug("Creating typed actor with id: {}", nodeId);

            return TypedActor.get(actorContext).typedActorOf(new TypedProps<>(NodeManager.class, new Creator<BaseNodeManager>() {
                @Override
                public BaseNodeManager create() throws Exception {
                    return new BaseNodeManager(nodeId, topologyId, actorContext.system(), delegateFactory, roleChangeStrategy);
                }
            }), nodeId);
        }
    }
}
