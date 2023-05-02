/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMActionServiceExtension;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringActionException;
import org.opendaylight.netconf.topology.singleton.messages.ContainerNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.SchemaPathMessage;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessage;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

/**
 * Implementation of {@link DOMActionService} provided by device in Odl-Cluster environment to invoke action.
 * Communicates action message {@link InvokeActionMessage} to {@link ActorSystem} using {@link ActorRef} and transforms
 * replied NETCONF message to action result, using {@link SimpleDOMActionResult}.
 */
public class ProxyDOMActionService implements Actions.Normalized {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyDOMActionService.class);

    private final RemoteDeviceId id;
    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;
    private final Timeout actorResponseWaitTime;

    /**
     * Constructor for {@code ProxyDOMActionService}.
     *
     * @param actorSystem ActorSystem
     * @param masterActorRef ActorRef
     * @param remoteDeviceId {@link RemoteDeviceId} ref
     * @param actorResponseWaitTime Timeout
     */
    public ProxyDOMActionService(final ActorSystem actorSystem, final ActorRef masterActorRef,
        final RemoteDeviceId remoteDeviceId, final Timeout actorResponseWaitTime) {
        id = remoteDeviceId;
        this.actorSystem = requireNonNull(actorSystem);
        this.masterActorRef = requireNonNull(masterActorRef);
        this.actorResponseWaitTime = requireNonNull(actorResponseWaitTime);
    }

    @Override
    public FluentFuture<DOMActionResult> invokeAction(final Absolute type,
            final DOMDataTreeIdentifier domDataTreeIdentifier, final ContainerNode input) {
        requireNonNull(type);
        requireNonNull(input);
        requireNonNull(domDataTreeIdentifier);

        LOG.info("{}: Action Operation invoked with schema type: {} and node: {}.", id, type, input);
        final ContainerNodeMessage containerNodeMessage = new ContainerNodeMessage(input);

        final Future<Object> scalaFuture = Patterns.ask(masterActorRef, new InvokeActionMessage(
            new SchemaPathMessage(type), containerNodeMessage, domDataTreeIdentifier), actorResponseWaitTime);

        final SettableFuture<DOMActionResult> settableFuture = SettableFuture.create();

        scalaFuture.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    if (failure instanceof ClusteringActionException) {
                        settableFuture.setException(failure);
                    } else {
                        settableFuture.setException(new ClusteringActionException(
                            id + ": Exception during remote Action invocation.", failure));
                    }
                    return;
                }

                if (response instanceof EmptyResultResponse) {
                    settableFuture.set(null);
                    return;
                }
                final Collection<? extends RpcError> errors = ((InvokeActionMessageReply) response).getRpcErrors();

                final ContainerNodeMessage containerNodeMessage =
                    ((InvokeActionMessageReply) response).getContainerNodeMessage();

                final DOMActionResult result;

                if (containerNodeMessage == null) {
                    result = new SimpleDOMActionResult(ImmutableList.copyOf(errors));
                } else {
                    result = new SimpleDOMActionResult(containerNodeMessage.getNode(), ImmutableList.copyOf(errors));
                }
                settableFuture.set(result);
            }
        }, actorSystem.dispatcher());

        return FluentFuture.from(settableFuture);
    }

    @Override
    public ClassToInstanceMap<DOMActionServiceExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of();
    }
}
