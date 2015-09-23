/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public final class BaseNodeManager<M> implements NodeManager, Peer<BaseNodeManager<M>> {

    private boolean isMaster;
    private NodeManagerCallback<M> delegate;

    public BaseNodeManager(final String nodeId,
                           final TopologyManager topologyParent,
                           final NodeManagerCallbackFactory<M> delegateFactory,
                           final RoleChangeStrategy roleChangeStrategy) {
        this.delegate = delegateFactory.create(topologyParent, this, nodeId);
        // if we want to override the place election happens,
        // we need to override this with noop election strategy and implement election in callback
        roleChangeStrategy.registerRoleCandidate(this);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override public Iterable<BaseNodeManager<M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    @Nonnull @Override public Node getInitialState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.getInitialState(nodeId, configNode);
    }

    @Nonnull @Override public Node getFailedState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.getFailedState(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Node> nodeCreated(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        delegate.setPeerContext(new PeerContext<M>() {
            @Override
            public void notifyPeers(final M msg) {
                for (BaseNodeManager<M> namBaseNodeManager : getPeers()) {
                    namBaseNodeManager.delegate.handle(msg);
                }
            }
        });

        return delegate.nodeCreated(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Node> nodeUpdated(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.nodeUpdated(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Void> nodeDeleted(@Nonnull final NodeId nodeId) {
        return delegate.nodeDeleted(nodeId);
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        delegate.onRoleChanged(roleChangeDTO);
    }
}
