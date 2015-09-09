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
import org.opendaylight.yangtools.yang.binding.Augmentation;

public final class BaseNodeAdmin<NA extends Augmentation<Node>, M> implements NodeAdministrator<NA>, Peer<BaseNodeAdmin<NA, M>> {

    private boolean isMaster;
    private NodeAdministratorCallback<NA, M> delegate;

    public BaseNodeAdmin(final NodeAdministratorCallback<NA, M> delegate) {
        this.delegate = delegate;
    }

    private boolean elect() {
        // FIXME implement this with EntityElectionService
        return true;
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override public Iterable<BaseNodeAdmin<NA, M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    @Nonnull @Override public NA getInitialState(@Nonnull final NodeId nodeId, @Nonnull final NA configNode) {
        return delegate.getInitialState(nodeId, configNode);
    }

    @Nonnull @Override public NA getFailedState(@Nonnull final NodeId nodeId, @Nonnull final NA configNode) {
        return delegate.getFailedState(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<NA> connect(@Nonnull final NodeId nodeId, @Nonnull final NA configNode) {
        delegate.setPeerContext(new PeerContext<M>() {
            @Override public void notifyPeers(final M msg) {
                for (BaseNodeAdmin<NA, M> namBaseNodeAdmin : getPeers()) {
                    namBaseNodeAdmin.delegate.handle(msg);
                }
            }
        });

        final ListenableFuture<NA> connect = delegate.connect(nodeId, configNode);

        Futures.addCallback(connect, new FutureCallback<NA>() {
            @Override public void onSuccess(final NA result) {
                isMaster = elect();
            }

            @Override public void onFailure(final Throwable t) {
                // LOG
                // not partaking in the election since we are not even connected or failed in some other way
            }
        });
        return connect;
    }

    @Nonnull @Override public ListenableFuture<NA> update(@Nonnull final NodeId nodeId, @Nonnull final NA configNode) {
        return delegate.update(nodeId, configNode);
    }

    @Nonnull @Override public ListenableFuture<Void> delete(@Nonnull final NodeId nodeId) {
        return delegate.delete(nodeId);
    }
}
