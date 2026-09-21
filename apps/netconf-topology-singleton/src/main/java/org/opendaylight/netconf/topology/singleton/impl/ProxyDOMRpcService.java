/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.time.Duration;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.opendaylight.mdsal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyDOMRpcService implements DOMRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyDOMRpcService.class);

    private final ActorRef masterActorRef;
    private final RemoteDeviceId id;
    private final Duration actorResponseWaitTime;

    public ProxyDOMRpcService(final ActorRef masterActorRef, final RemoteDeviceId remoteDeviceId,
            final Duration actorResponseWaitTime) {
        this.masterActorRef = masterActorRef;
        id = remoteDeviceId;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public ListenableFuture<DOMRpcResult> invokeRpc(final QName type, final ContainerNode input) {
        LOG.trace("{}: Rpc operation invoked with schema type: {} and node: {}.", id, type, input);

        final var normalizedNodeMessage = input != null
            ? new NormalizedNodeMessage(YangInstanceIdentifier.of(), input)
            : null;
        final var scalaFuture = Patterns.ask(masterActorRef,
                new InvokeRpcMessage(new SchemaPathMessage(type), normalizedNodeMessage), actorResponseWaitTime);

        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();

        scalaFuture.whenComplete((response, failure) -> {
            if (failure != null) {
                settableFuture.setException(switch (failure) {
                    case ClusteringRpcException ex -> ex;
                    default -> new ClusteringRpcException(id + ": Exception during remote rpc invocation.", failure);
                });
                return;
            }

            if (response instanceof EmptyResultResponse) {
                settableFuture.set(null);
                return;
            }

            final var errors = ((InvokeRpcMessageReply) response).getRpcErrors();
            final var normalizedNodeMessageResult = ((InvokeRpcMessageReply) response).getNormalizedNodeMessage();
            final DOMRpcResult result;
            if (normalizedNodeMessageResult == null) {
                result = new DefaultDOMRpcResult(ImmutableList.copyOf(errors));
            } else {
                result = new DefaultDOMRpcResult((ContainerNode) normalizedNodeMessageResult.getNode(), errors);
            }
            settableFuture.set(result);
        });

        return settableFuture;
    }

    @Override
    public Registration registerRpcListener(final DOMRpcAvailabilityListener listener) {
        // NOOP, only proxy
        throw new UnsupportedOperationException("RegisterRpcListener: DOMRpc service not working in cluster.");
    }
}
