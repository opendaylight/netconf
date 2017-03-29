/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.TypedActor;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.RpcMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.impl.Promise;

public class RestconfFacadeActorImpl implements RestconfFacadeActor {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfFacadeActorImpl.class);

    private final RestconfFacade facade;
    private final ClusterNotificationDistributor distributor;

    public RestconfFacadeActorImpl(final RestconfFacade facade) {
        this.distributor = new ClusterNotificationDistributor(TypedActor.context());
        LOG.info("Creating");
        this.facade = Preconditions.checkNotNull(facade);
        facade.registerNotificationListener(distributor);
    }

    @Override
    public Future<Void> headData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final ListenableFuture<Void> headData = facade.headData(datastore, path);
        return convertToScalaFuture(headData);
    }

    @Override
    public Future<Optional<NormalizedNodeMessage>> getData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> data = facade.getData(datastore, path);
        final Promise.DefaultPromise<Optional<NormalizedNodeMessage>> promise = new Promise.DefaultPromise<>();
        Futures.addCallback(data, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    promise.success(Optional.<NormalizedNodeMessage>absent());
                } else {
                    promise.success(Optional.of(new NormalizedNodeMessage(path, result.get())));
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Optional<RpcMessage>> postOperation(final RpcMessage message) {
        Preconditions.checkState(message.getContent() instanceof ContainerNode);
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> result = facade.postOperation(message.getSchemaPath(), (ContainerNode) message.getContent());
        final Promise.DefaultPromise<Optional<RpcMessage>> promise = new Promise.DefaultPromise<>();
        Futures.addCallback(result, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    promise.success(Optional.<RpcMessage>absent());
                } else {
                    promise.success(Optional.of(new RpcMessage(message.getSchemaPath(), result.get())));
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Void> postConfig(final NormalizedNodeMessage message) {
        final ListenableFuture<Void> result = facade.postConfig(message.getIdentifier(), message.getNode());
        return convertToScalaFuture(result);
    }

    @Override
    public Future<Void> putConfig(final NormalizedNodeMessage message) {
        final ListenableFuture<Void> result = facade.putConfig(message.getIdentifier(), message.getNode());
        return convertToScalaFuture(result);
    }

    @Override
    public Future<Void> patchConfig(final NormalizedNodeMessage message) {
        final ListenableFuture<Void> result = facade.patchConfig(message.getIdentifier(), message.getNode());
        return convertToScalaFuture(result);
    }

    @Override
    public Future<Void> deleteConfig(final YangInstanceIdentifier path) {
        final ListenableFuture<Void> result = facade.deleteConfig(path);
        return convertToScalaFuture(result);
    }

    @Override
    public void subscribe(final ActorRef subscriber) {
        distributor.addSubscriber(subscriber);
    }

    private Future<Void> convertToScalaFuture(final ListenableFuture<Void> headData) {
        final Promise.DefaultPromise<Void> promise = new Promise.DefaultPromise<>();
        Futures.addCallback(headData, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                promise.success(null);
            }

            @Override
            public void onFailure(final Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public void onReceive(final Object message, final ActorRef sender) {
        if (message instanceof Terminated) {
            final Terminated t = (Terminated) message;
            distributor.removeSubscriber(t.getActor());
        }
    }
}
