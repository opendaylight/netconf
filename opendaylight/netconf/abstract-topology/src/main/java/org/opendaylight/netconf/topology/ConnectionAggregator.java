package org.opendaylight.netconf.topology;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.Augmentation;

/**
 * Aggregate different connection states from multiple connections to a single device
 */
public interface ConnectionAggregator<NA extends Augmentation<Node>> {

    ListenableFuture<NA> combineConnectAttempts(final List<ListenableFuture<NA>> connectionFutures);

    ListenableFuture<NA> combineUpdateAttempts(final List<ListenableFuture<NA>> connectionFutures);

    ListenableFuture<Void> combineDisconnectAttempts(final List<ListenableFuture<Void>> connectionFutures);

    /**
     * Aggregator implementation expecting just a single connection for every device
     */
    public static final class SingleConnectionAggregator<NA extends Augmentation<Node>> implements ConnectionAggregator<NA> {

        @Override public ListenableFuture<NA> combineConnectAttempts( final List<ListenableFuture<NA>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }

        private <T> ListenableFuture<T> getSingleFuture(final List<ListenableFuture<T>> connectionFutures) {
            Preconditions.checkArgument(connectionFutures.size() == 1);
            return connectionFutures.get(0);
        }

        @Override public ListenableFuture<NA> combineUpdateAttempts(final List<ListenableFuture<NA>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }

        @Override public ListenableFuture<Void> combineDisconnectAttempts(final List<ListenableFuture<Void>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }
    }
}
