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

public final class BaseNodeAdmin<N extends Node, M> implements NodeAdministrator<N>, Peer<BaseNodeAdmin<N, M>> {

    private boolean isMaster;
    private NodeAdministratorCallback<N, M> delegate;

    public BaseNodeAdmin(final NodeAdministratorCallback<N, M> delegate) {
        this.delegate = delegate;
    }

    private boolean elect() {
        // FIXME implement this with EntityElectionService
        return true;
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override public Iterable<BaseNodeAdmin<N, M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    @Nonnull @Override public N getInitialState(@Nonnull final NodeId nodeId, @Nonnull final N configNode) {
        return delegate.getInitialState(nodeId, configNode);
    }

    @Nonnull @Override public N getFailedState(@Nonnull final NodeId nodeId, @Nonnull final N configNode) {
        return delegate.getFailedState(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<N> connect(@Nonnull final NodeId nodeId, @Nonnull final N configNode) {
        delegate.setPeerContext(new PeerContext<M>() {
            @Override public void notifyPeers(final M msg) {
                for (BaseNodeAdmin<N, M> namBaseNodeAdmin : getPeers()) {
                    namBaseNodeAdmin.delegate.handle(msg);
                }
            }
        });

        final ListenableFuture<N> connect = delegate.connect(nodeId, configNode);

        Futures.addCallback(connect, new FutureCallback<N>() {
            @Override public void onSuccess(final N result) {
                isMaster = elect();
            }

            @Override public void onFailure(final Throwable t) {
                // LOG
                // not partaking in the election since we are not even connected or failed in some other way
            }
        });
        return connect;
    }

    @Nonnull @Override public ListenableFuture<N> update(@Nonnull final NodeId nodeId, @Nonnull final N configNode) {
        return delegate.update(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Void> delete(@Nonnull final NodeId nodeId) {
        return delegate.delete(nodeId);
    }
}
