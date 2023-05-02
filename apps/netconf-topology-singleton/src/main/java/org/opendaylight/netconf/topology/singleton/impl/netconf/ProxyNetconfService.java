/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
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
public class ProxyNetconfService implements NetconfDataTreeService {
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
    public ListenableFuture<DOMRpcResult> lock() {
        LOG.debug("{}: Lock", id);
        final SettableFuture<DOMRpcResult> future = SettableFuture.create();
        processNetconfOperation(facade -> future.setFuture(facade.lock()));
        return future;
    }

    @Override
    public ListenableFuture<DOMRpcResult> unlock() {
        LOG.debug("{}: Unlock", id);
        final SettableFuture<DOMRpcResult> future = SettableFuture.create();
        processNetconfOperation(facade -> future.setFuture(facade.unlock()));
        return future;
    }

    @Override
    public ListenableFuture<DOMRpcResult> discardChanges() {
        LOG.debug("{}: Discard changes", id);
        final SettableFuture<DOMRpcResult> future = SettableFuture.create();
        processNetconfOperation(facade -> future.setFuture(facade.discardChanges()));
        return future;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path) {
        LOG.debug("{}: Get {} {}", id, OPERATIONAL, path);
        final SettableFuture<Optional<NormalizedNode>> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.get(path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path,
                                                          final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get {} {} with fields: {}", id, OPERATIONAL, path, fields);
        final SettableFuture<Optional<NormalizedNode>> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.get(path, fields)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path) {
        LOG.debug("{}: Get config {} {}", id, CONFIGURATION, path);
        final SettableFuture<Optional<NormalizedNode>> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.getConfig(path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
                                                                final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get config {} {} with fields: {}", id, CONFIGURATION, path, fields);
        final SettableFuture<Optional<NormalizedNode>> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.getConfig(path, fields)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Merge {} {}", id, store, path);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.merge(store, path, data, defaultOperation)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Replace {} {}", id, store, path);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.replace(store, path, data, defaultOperation)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Create {} {}", id, store, path);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.create(store, path, data, defaultOperation)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {}", id, store, path);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.delete(store, path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {} {}", id, store, path);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.remove(store, path)));
        return returnFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit", id);
        final SettableFuture<DOMRpcResult> returnFuture = SettableFuture.create();
        processNetconfOperation(facade -> returnFuture.setFuture(facade.commit()));
        return returnFuture;
    }

    @Override
    public @NonNull Object getDeviceId() {
        return id;
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
