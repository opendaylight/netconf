/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ActorProxyNetconfServiceFacade;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ProxyNetconfService;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyNetconfDataTreeService implements DataStoreService {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyNetconfDataTreeService.class);

    private final Duration askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;

    /**
     * Constructor for {@code ProxyNetconfDataTreeService}.
     *
     * @param id               id
     * @param masterNode       {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param askTimeout       ask timeout
     */
    public ProxyNetconfDataTreeService(final RemoteDeviceId id, final ActorRef masterNode, final Duration askTimeout) {
        this.id = id;
        this.masterNode = masterNode;
        this.askTimeout = askTimeout;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return edit(netconfService -> netconfService.create(path, data));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final YangInstanceIdentifier path) {
        return edit(netconfService -> netconfService.delete(path));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final YangInstanceIdentifier path) {
        return edit(netconfService -> netconfService.remove(path));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return edit(netconfService -> netconfService.merge(path, data));
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return edit(netconfService -> netconfService.replace(path, data));
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        final var masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        final var netconfService = new ProxyNetconfService(id, masterActor, askTimeout);
        return netconfService.get(store, path, fields);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        final var masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        final var netconfService = new ProxyNetconfService(id, masterActor, askTimeout);
        return netconfService.commit();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        final var masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        final var netconfService = new ProxyNetconfService(id, masterActor, askTimeout);
        return netconfService.cancel();
    }

    private ListenableFuture<? extends DOMRpcResult> edit(
            final Function<DataStoreService, ListenableFuture<? extends DOMRpcResult>> operation) {
        final var masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        final var future = operation.apply(new ProxyNetconfService(id, masterActor, askTimeout));
        Futures.addCallback(future, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                // Nothing to do
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancelIfEditTimedOut(masterActor, cause);
            }
        }, MoreExecutors.directExecutor());
        return future;
    }

    /**
     * An edit whose reply timed out may still be applied by the master, see the FIXME in
     * {@link ActorProxyNetconfServiceFacade}. Discard it through a fresh actor. Nothing needs to be done if it was the
     * ask for the actor which timed out, as the edit was never issued then.
     */
    private void cancelIfEditTimedOut(final CompletionStage<Object> masterActor, final Throwable cause) {
        if (!masterActor.toCompletableFuture().isCompletedExceptionally()
                && Throwables.getCausalChain(cause).stream().anyMatch(AskTimeoutException.class::isInstance)) {
            LOG.warn("{}: Edit timed out and may still be applied by the master, discarding changes", id);
            Futures.addCallback(cancel(), new FutureCallback<DOMRpcResult>() {
                @Override
                public void onSuccess(final DOMRpcResult result) {
                    LOG.debug("{}: Changes discarded after edit timeout", id);
                }

                @Override
                public void onFailure(final Throwable failure) {
                    LOG.warn("{}: Failed to discard changes after edit timeout", id, failure);
                }
            }, MoreExecutors.directExecutor());
        }
    }
}
