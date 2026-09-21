/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.time.Duration;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringActionException;
import org.opendaylight.netconf.topology.singleton.messages.ContainerNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessage;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link DOMActionService} provided by device in Odl-Cluster environment to invoke action.
 * Communicates action message {@link InvokeActionMessage} to {@link ActorSystem} using {@link ActorRef} and transforms
 * replied NETCONF message to action result, using {@link DefaultDOMRpcResult}.
 */
public class ProxyDOMActionService implements Actions.Normalized {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyDOMActionService.class);

    private final RemoteDeviceId id;
    private final ActorRef masterActorRef;
    private final Duration actorResponseWaitTime;

    /**
     * Constructor for {@code ProxyDOMActionService}.
     *
     * @param masterActorRef ActorRef
     * @param remoteDeviceId {@link RemoteDeviceId} ref
     * @param actorResponseWaitTime Timeout
     */
    public ProxyDOMActionService(final ActorRef masterActorRef, final RemoteDeviceId remoteDeviceId,
            final Duration actorResponseWaitTime) {
        id = remoteDeviceId;
        this.masterActorRef = requireNonNull(masterActorRef);
        this.actorResponseWaitTime = requireNonNull(actorResponseWaitTime);
    }

    @Override
    public FluentFuture<DOMRpcResult> invokeAction(final Absolute type,
            final DOMDataTreeIdentifier domDataTreeIdentifier, final ContainerNode input) {
        requireNonNull(type);
        requireNonNull(input);
        requireNonNull(domDataTreeIdentifier);

        LOG.info("{}: Action Operation invoked with schema type: {} and node: {}.", id, type, input);
        final var containerNodeMessage = new ContainerNodeMessage(input);
        final var scalaFuture = Patterns.ask(masterActorRef, new InvokeActionMessage(
            new SchemaPathMessage(type), containerNodeMessage, domDataTreeIdentifier), actorResponseWaitTime);

        final var settableFuture = SettableFuture.<DOMRpcResult>create();

        scalaFuture.whenComplete((response, failure) -> {
            if (failure != null) {
                settableFuture.setException(switch (failure) {
                    case ClusteringActionException ex -> ex;
                    default -> new ClusteringActionException(
                        "%s: Exception during remote Action invocation.".formatted(id), failure);
                });
                return;
            }

            switch (response) {
                case EmptyResultResponse unused -> settableFuture.set(null);
                case InvokeActionMessageReply reply -> {
                    final var errors = reply.getRpcErrors();
                    final var responseMessage = reply.getContainerNodeMessage();
                    settableFuture.set(responseMessage == null ? new DefaultDOMRpcResult(ImmutableList.copyOf(errors))
                        : new DefaultDOMRpcResult(responseMessage.getNode(), ImmutableList.copyOf(errors)));
                }
                case null, default ->
                    settableFuture.setException(new IllegalStateException("Unexpected response " + response));
            }
        });

        return FluentFuture.from(settableFuture);
    }
}
