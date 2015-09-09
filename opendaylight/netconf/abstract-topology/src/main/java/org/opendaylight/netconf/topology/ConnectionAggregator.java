/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

/**
 * Aggregate different connection states from multiple connections to a single device
 */
@Beta
public interface ConnectionAggregator {

    ListenableFuture<Node> combineConnectAttempts(final List<ListenableFuture<Node>> connectionFutures);

    ListenableFuture<Node> combineUpdateAttempts(final List<ListenableFuture<Node>> connectionFutures);

    ListenableFuture<Void> combineDisconnectAttempts(final List<ListenableFuture<Void>> connectionFutures);

    /**
     * Aggregator implementation expecting just a single connection for every device
     */
    public static final class SingleConnectionAggregator implements ConnectionAggregator {

        @Override public ListenableFuture<Node> combineConnectAttempts( final List<ListenableFuture<Node>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }

        private <T> ListenableFuture<T> getSingleFuture(final List<ListenableFuture<T>> connectionFutures) {
            Preconditions.checkArgument(connectionFutures.size() == 1);
            return connectionFutures.get(0);
        }

        @Override public ListenableFuture<Node> combineUpdateAttempts(final List<ListenableFuture<Node>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }

        @Override public ListenableFuture<Void> combineDisconnectAttempts(final List<ListenableFuture<Void>> connectionFutures) {
            return getSingleFuture(connectionFutures);
        }
    }
}
