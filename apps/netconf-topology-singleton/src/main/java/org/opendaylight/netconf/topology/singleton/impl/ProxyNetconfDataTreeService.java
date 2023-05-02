/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ProxyNetconfService;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class ProxyNetconfDataTreeService implements NetconfDataTreeService {
    private final Timeout askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;
    private final ExecutionContext executionContext;

    private volatile ProxyNetconfService proxyNetconfService;

    /**
     * Constructor for {@code ProxyNetconfDataTreeService}.
     *
     * @param id               id
     * @param masterNode       {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param executionContext ExecutionContext
     * @param askTimeout       ask timeout
     */
    public ProxyNetconfDataTreeService(final RemoteDeviceId id, final ActorRef masterNode,
                                       final ExecutionContext executionContext, final Timeout askTimeout) {
        this.id = id;
        this.masterNode = masterNode;
        this.executionContext = executionContext;
        this.askTimeout = askTimeout;
    }

    @Override
    public synchronized ListenableFuture<DOMRpcResult> lock() {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        proxyNetconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return proxyNetconfService.lock();
    }

    @Override
    public ListenableFuture<DOMRpcResult> unlock() {
        isLocked();
        return proxyNetconfService.unlock();
    }

    @Override
    public ListenableFuture<DOMRpcResult> discardChanges() {
        isLocked();
        return proxyNetconfService.discardChanges();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.get(path);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.get(path, fields);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.getConfig(path);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.getConfig(path, fields);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        isLocked();
        return proxyNetconfService.merge(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        isLocked();
        return proxyNetconfService.replace(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        isLocked();
        return proxyNetconfService.create(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        isLocked();
        return proxyNetconfService.delete(store, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        isLocked();
        return proxyNetconfService.remove(store, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        isLocked();
        return proxyNetconfService.commit();
    }

    @Override
    public @NonNull Object getDeviceId() {
        return id;
    }

    private void isLocked() {
        Preconditions.checkState(proxyNetconfService != null,
            "%s: Device's datastore must be locked first", id);
    }
}
