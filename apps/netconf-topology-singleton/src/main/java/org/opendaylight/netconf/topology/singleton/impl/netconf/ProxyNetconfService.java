/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.util.Timeout;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

/**
 * ProxyNetconfService uses provided {@link ActorRef} to delegate method calls to master
 * {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfDataTreeServiceActor}.
 */
public class ProxyNetconfService implements DataStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyNetconfService.class);

    private final RemoteDeviceId id;
    @GuardedBy("queuedOperations")
    private final List<Consumer<ProxyNetconfServiceFacade>> queuedOperations = new ArrayList<>();

    private volatile ProxyNetconfServiceFacade netconfFacade;

    public ProxyNetconfService(final RemoteDeviceId id, final Future<Object> masterActorFuture,
                               final ExecutionContext executionContext, final Timeout askTimeout) {
        this.id = id;
        masterActorFuture.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object masterActor) {
                final ProxyNetconfServiceFacade newNetconfFacade;
                if (failure != null) {
                    LOG.debug("{}: Failed to obtain master actor", id, failure);
                    newNetconfFacade = new FailedProxyNetconfServiceFacade(id, failure);
                } else {
                    LOG.debug("{}: Obtained master actor {}", id, masterActor);
                    newNetconfFacade = new ActorProxyNetconfServiceFacade((ActorRef) masterActor, id,
                        executionContext, askTimeout);
                }
                executePriorNetconfOperations(newNetconfFacade);
            }
        }, executionContext);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Create {}", id, path);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.create(path, data)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {}", id, path);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.delete(path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {}", id, path);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.remove(path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Merge {}", id, path);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.merge(path, data)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Replace {}", id, path);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.replace(path, data)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get {} {} with fields: {}", id, store, path, fields);
        final var returnFuture = SettableFuture.<Optional<NormalizedNode>>create();
        processNetconfOperation(facade ->
            returnFuture.setFuture(facade.get(store, path, fields)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit", id);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.commit()));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        LOG.debug("{}: Cancel", id);
        final var returnFuture = SettableFuture.<DOMRpcResult>create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.cancel()));
        return returnFuture;
    }

    private void processNetconfOperation(final Consumer<ProxyNetconfServiceFacade> operation) {
        final ProxyNetconfServiceFacade facadeOnEntry;
        synchronized (queuedOperations) {
            if (netconfFacade == null) {
                LOG.debug("{}: Queuing netconf operation", id);

                queuedOperations.add(operation);
                facadeOnEntry = null;
            } else {
                facadeOnEntry = netconfFacade;
            }
        }

        if (facadeOnEntry != null) {
            operation.accept(facadeOnEntry);
        }
    }

    private void executePriorNetconfOperations(final ProxyNetconfServiceFacade newNetconfFacade) {
        while (true) {
            // Access to queuedOperations and netconfFacade must be protected and atomic
            // (ie synchronized) with respect to #processNetconfOperation to handle timing
            // issues and ensure no ProxyNetconfServiceFacade is missed and that they are processed
            // in the order they occurred.

            // We'll make a local copy of the queuedOperations list to handle re-entrancy
            // in case a netconf operation results in another netconf operation being
            // queued (eg a put operation from a client read Future callback that is notified
            // synchronously).
            final Collection<Consumer<ProxyNetconfServiceFacade>> operationsBatch;
            synchronized (queuedOperations) {
                if (queuedOperations.isEmpty()) {
                    // We're done invoking the netconf operations so we can now publish the
                    // ProxyNetconfServiceFacade.
                    netconfFacade = newNetconfFacade;
                    break;
                }

                operationsBatch = new ArrayList<>(queuedOperations);
                queuedOperations.clear();
            }

            // Invoke netconf operations outside the sync block to avoid unnecessary blocking.
            for (Consumer<ProxyNetconfServiceFacade> oper : operationsBatch) {
                oper.accept(newNetconfFacade);
            }
        }
    }
}
