/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.NodeAdministrator;
import org.opendaylight.netconf.topology.NodeAdministratorCallback;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public final class BaseNodeAdmin<M> implements NodeAdministrator, Peer<BaseNodeAdmin<M>> {

    private boolean isMaster;
    private NodeAdministratorCallback<M> delegate;

    public BaseNodeAdmin(final NodeAdministratorCallback<M> delegate) {
        this.delegate = delegate;
    }

    private boolean elect() {
        // FIXME implement this with EntityElectionService
        return true;
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override public Iterable<BaseNodeAdmin<M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    @Nonnull @Override public Node getInitialState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.getInitialState(nodeId, configNode);
    }

    @Nonnull @Override public Node getFailedState(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.getFailedState(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Node> connect(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        delegate.setPeerContext(new PeerContext<M>() {
            @Override public void notifyPeers(final M msg) {
                for (BaseNodeAdmin<M> namBaseNodeAdmin : getPeers()) {
                    namBaseNodeAdmin.delegate.handle(msg);
                }
            }
        });

        final ListenableFuture<Node> connect = delegate.connect(nodeId, configNode);

        Futures.addCallback(connect, new FutureCallback<Node>() {
            @Override public void onSuccess(final Node result) {
                isMaster = elect();
                // after election master needs to register mountpoint in mdsal
            }

            @Override public void onFailure(final Throwable t) {
                // LOG
                // not partaking in the election since we are not even connected or failed in some other way
            }
        });
        return connect;
    }

    @Nonnull @Override public ListenableFuture<Node> update(@Nonnull final NodeId nodeId, @Nonnull final Node configNode) {
        return delegate.update(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Void> delete(@Nonnull final NodeId nodeId) {
        return delegate.delete(nodeId);
    }
}
