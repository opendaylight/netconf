/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import akka.actor.TypedActor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnlySuccesStateAggregator implements StateAggregator{

    private static final Logger LOG = LoggerFactory.getLogger(OnlySuccesStateAggregator.class);

    @Override
    public ListenableFuture<Node> combineCreateAttempts(List<ListenableFuture<Node>> stateFutures) {
        final SettableFuture<Node> future = SettableFuture.create();
        final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
            @Override
            public void onSuccess(List<Node> result) {
                for (int i = 0; i < result.size() - 1; i++) {
                    if (!result.get(i).equals(result.get(i + 1))) {
                        future.setException(new IllegalStateException("Create futures have different result"));
                    }
                }
                future.set(result.get(0));
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("One of the combined create attempts failed {}", t);
                future.setException(t);
            }
        }, TypedActor.context().dispatcher());

        return future;
    }

    @Override
    public ListenableFuture<Node> combineUpdateAttempts(List<ListenableFuture<Node>> stateFutures) {
        final SettableFuture<Node> future = SettableFuture.create();
        final ListenableFuture<List<Node>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Node>>() {
            @Override
            public void onSuccess(List<Node> result) {
                for (int i = 0; i < result.size() - 1; i++) {
                    if (!result.get(i).equals(result.get(i + 1))) {
                        future.setException(new IllegalStateException("Update futures have different result"));
                    }
                }
                future.set(result.get(0));
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("One of the combined update attempts failed {}", t);
                future.setException(t);
            }
        });
        return future;
    }

    @Override
    public ListenableFuture<Void> combineDeleteAttempts(List<ListenableFuture<Void>> stateFutures) {
        final SettableFuture<Void> future = SettableFuture.create();
        final ListenableFuture<List<Void>> allAsList = Futures.allAsList(stateFutures);
        Futures.addCallback(allAsList, new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(List<Void> result) {
                future.set(null);
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.error("One of the combined delete attempts failed {}", t);
                future.setException(t);
            }
        });
        return future;
    }
}
