/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import akka.actor.ActorContext;
import akka.dispatch.OnComplete;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.RpcMessage;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

/**
 * ProxyRestconfFacade doesn't communicate directly with the device, but routes method calls via provided {@link RestconfFacadeActor}.
 */
public class ProxyRestconfFacade implements RestconfFacade {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyRestconfFacade.class);

    private final RestconfFacadeActor masterFacade;
    private final ActorContext actorSystem;
    private final NotificationAdapter adapter;

    /**
     * Creates new ProxyRestconfFacade
     * @param masterFacade master facade typed actor
     * @param actorContext  actor context
     * @param adapter adapter
     */
    public ProxyRestconfFacade(final RestconfFacadeActor masterFacade, final ActorContext actorContext, final NotificationAdapter adapter) {
        this.masterFacade = masterFacade;
        this.actorSystem = actorContext;
        this.adapter = adapter;
    }

    @Override
    public ListenableFuture<Void> headData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final Future<Void> result = masterFacade.headData(datastore, path);
        return convertToListenableFuture(result);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final SettableFuture<Optional<NormalizedNode<?, ?>>> result = SettableFuture.create();
        masterFacade.getData(datastore, path).onComplete(new OnComplete<Optional<NormalizedNodeMessage>>() {
            @Override
            public void onComplete(final Throwable throwable, final Optional<NormalizedNodeMessage> message) throws Throwable {
                if (throwable == null) {
                    result.set(message.transform(new Function<NormalizedNodeMessage, NormalizedNode<?, ?>>() {
                        @Nullable
                        @Override
                        public NormalizedNode<?, ?> apply(final NormalizedNodeMessage input) {
                            return input.getNode();
                        }
                    }));
                } else {
                    result.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return result;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> postOperation(final SchemaPath type, final ContainerNode input) {
        final SettableFuture<Optional<NormalizedNode<?, ?>>> result = SettableFuture.create();
        masterFacade.postOperation(new RpcMessage(type, input)).onComplete(new OnComplete<Optional<RpcMessage>>() {
            @Override
            public void onComplete(final Throwable throwable, final Optional<RpcMessage> message) throws Throwable {
                if (throwable == null) {
                    result.set(message.transform(new Function<RpcMessage, NormalizedNode<?, ?>>() {
                        @Nullable
                        @Override
                        public NormalizedNode<?, ?> apply(final RpcMessage input) {
                            return input.getContent();
                        }
                    }));
                } else {
                    result.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return result;
    }

    @Override
    public ListenableFuture<Void> postConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Future<Void> result = masterFacade.postConfig(new NormalizedNodeMessage(path, input));
        return convertToListenableFuture(result);
    }

    @Override
    public ListenableFuture<Void> putConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Future<Void> result = masterFacade.putConfig(new NormalizedNodeMessage(path, input));
        return convertToListenableFuture(result);
    }

    @Override
    public ListenableFuture<Void> patchConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Future<Void> result = masterFacade.patchConfig(new NormalizedNodeMessage(path, input));
        return convertToListenableFuture(result);
    }

    @Override
    public ListenableFuture<Void> deleteConfig(final YangInstanceIdentifier path) {
        final Future<Void> result = masterFacade.deleteConfig(path);
        return convertToListenableFuture(result);
    }

    @Override
    public void registerNotificationListener(final RestconfDeviceStreamListener listener) {
        adapter.registerListener(listener);
    }

    @Override
    public Collection<RpcError> parseErrors(final HttpException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private ListenableFuture<Void> convertToListenableFuture(final Future<Void> scalaFuture) {
        final SettableFuture<Void> result = SettableFuture.create();
        scalaFuture.onComplete(new OnComplete<Void>() {
            @Override
            public void onComplete(final Throwable throwable, final Void aVoid) throws Throwable {
                if (throwable == null) {
                    result.set(null);
                } else {
                    result.setException(throwable);
                }
            }
        }, actorSystem.dispatcher());
        return result;
    }

    @Override
    public void close() throws Exception {
        //noop
    }
}
