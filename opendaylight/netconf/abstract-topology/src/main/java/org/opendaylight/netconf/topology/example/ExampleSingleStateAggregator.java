package org.opendaylight.netconf.topology.example;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * Aggregator implementation expecting just a single state
 */
public final class ExampleSingleStateAggregator implements StateAggregator {

    @Override public ListenableFuture<Node> combineCreateAttempts(final List<ListenableFuture<Node>> stateFutures) {
        return getSingleFuture(stateFutures);
    }

    private <T> ListenableFuture<T> getSingleFuture(final List<ListenableFuture<T>> stateFutures) {
        Preconditions.checkArgument(stateFutures.size() == 1, "Recieved multiple results, Single result is enforced here");
        return stateFutures.get(0);
    }

    @Override public ListenableFuture<Node> combineUpdateAttempts(final List<ListenableFuture<Node>> stateFutures) {
        return getSingleFuture(stateFutures);
    }

    @Override public ListenableFuture<Void> combineDeleteAttempts(final List<ListenableFuture<Void>> stateFutures) {
        return getSingleFuture(stateFutures);
    }
}
