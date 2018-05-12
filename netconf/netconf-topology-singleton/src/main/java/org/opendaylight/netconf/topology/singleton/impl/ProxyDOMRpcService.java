/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

public class ProxyDOMRpcService implements DOMRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyManager.class);

    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;
    private final RemoteDeviceId id;
    private final Timeout actorResponseWaitTime;

    public ProxyDOMRpcService(final ActorSystem actorSystem, final ActorRef masterActorRef,
                              final RemoteDeviceId remoteDeviceId, final Timeout actorResponseWaitTime) {
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        id = remoteDeviceId;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Nonnull
    @Override
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(@Nonnull final SchemaPath type,
                                                                  @Nullable final NormalizedNode<?, ?> input) {
        LOG.trace("{}: Rpc operation invoked with schema type: {} and node: {}.", id, type, input);

        final NormalizedNodeMessage normalizedNodeMessage =
                new NormalizedNodeMessage(YangInstanceIdentifier.EMPTY, input);
        final Future<Object> scalaFuture = Patterns.ask(masterActorRef,
                new InvokeRpcMessage(new SchemaPathMessage(type), normalizedNodeMessage), actorResponseWaitTime);

        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();

        scalaFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    settableFuture.setException(failure);
                    return;
                }

                if (response instanceof EmptyResultResponse) {
                    settableFuture.set(null);
                    return;
                }

                final Collection<RpcError> errors = ((InvokeRpcMessageReply) response).getRpcErrors();
                final NormalizedNodeMessage normalizedNodeMessageResult =
                        ((InvokeRpcMessageReply) response).getNormalizedNodeMessage();
                final DOMRpcResult result;
                if (normalizedNodeMessageResult == null) {
                    result = new DefaultDOMRpcResult(errors);
                } else {
                    result = new DefaultDOMRpcResult(normalizedNodeMessageResult.getNode(), errors);
                }
                settableFuture.set(result);
            }
        }, actorSystem.dispatcher());

        return Futures.makeChecked(settableFuture,
            ex -> new ClusteringRpcException(id + ": Exception during remote rpc invocation.", ex));
    }

    @Nonnull
    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(
            @Nonnull final T listener) {
        // NOOP, only proxy
        throw new UnsupportedOperationException("RegisterRpcListener: DOMRpc service not working in cluster.");
    }
}
