/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Status;
import akka.actor.UntypedAbstractActor;
import akka.util.JavaDurationConverters;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DiscardChangesRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetConfigWithFieldsRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetWithFieldsRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.LockRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.UnlockRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDataTreeServiceActor extends UntypedAbstractActor {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataTreeServiceActor.class);

    private final NetconfDataTreeService netconfService;
    private final long idleTimeout;

    private NetconfDataTreeServiceActor(final NetconfDataTreeService netconfService, final Duration idleTimeout) {
        this.netconfService = netconfService;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(JavaDurationConverters.asFiniteDuration(idleTimeout));
        }
    }

    static Props props(final NetconfDataTreeService netconfService, final Duration idleTimeout) {
        return Props.create(NetconfDataTreeServiceActor.class, () ->
            new NetconfDataTreeServiceActor(netconfService, idleTimeout));
    }

    @Override
    public void onReceive(final Object message) {
        if (message instanceof GetWithFieldsRequest getRequest) {
            final YangInstanceIdentifier path = getRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode>> future = netconfService.get(
                    getRequest.getPath(), getRequest.getFields());
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetRequest getRequest) {
            final YangInstanceIdentifier path = getRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode>> future = netconfService.get(path);
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetConfigWithFieldsRequest getConfigRequest) {
            final YangInstanceIdentifier path = getConfigRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode>> future = netconfService.getConfig(
                    path, getConfigRequest.getFields());
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetConfigRequest getConfigRequest) {
            final YangInstanceIdentifier path = getConfigRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode>> future = netconfService.getConfig(path);
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof LockRequest) {
            invokeRpcCall(netconfService::lock, sender(), self());
        } else if (message instanceof MergeEditConfigRequest request) {
            netconfService.merge(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation()));
        } else if (message instanceof ReplaceEditConfigRequest request) {
            netconfService.replace(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation()));
        } else if (message instanceof CreateEditConfigRequest request) {
            netconfService.create(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation()));
        } else if (message instanceof DeleteEditConfigRequest request) {
            netconfService.delete(request.getStore(), request.getPath());
        } else if (message instanceof RemoveEditConfigRequest request) {
            netconfService.remove(request.getStore(), request.getPath());
        } else if (message instanceof CommitRequest) {
            submit(sender(), self());
        } else if (message instanceof DiscardChangesRequest) {
            invokeRpcCall(netconfService::discardChanges, sender(), self());
        } else if (message instanceof UnlockRequest) {
            context().stop(self());
            invokeRpcCall(netconfService::unlock, sender(), self());
        } else if (message instanceof ReceiveTimeout) {
            LOG.warn("Haven't received any message for {} seconds, cancelling transaction and stopping actor",
                idleTimeout);
            invokeRpcCall(netconfService::discardChanges, sender(), self());
            invokeRpcCall(netconfService::unlock, sender(), self());
            context().stop(self());
        } else {
            unhandled(message);
        }
    }

    private void submit(final ActorRef requester, final ActorRef self) {
        Futures.addCallback(netconfService.commit(), new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (result == null) {
                    requester.tell(new EmptyResultResponse(), getSender());
                    return;
                }
                NormalizedNodeMessage nodeMessageResp = null;
                if (result.value() != null) {
                    nodeMessageResp = new NormalizedNodeMessage(YangInstanceIdentifier.of(), result.value());
                }
                requester.tell(new InvokeRpcMessageReply(nodeMessageResp, result.errors()), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                requester.tell(new Status.Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }

    private void invokeRpcCall(final Supplier<ListenableFuture<? extends DOMRpcResult>> operation,
        final ActorRef requester, final ActorRef self) {
        Futures.addCallback(operation.get(), new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult rpcResult) {
                if (rpcResult == null) {
                    requester.tell(new EmptyResultResponse(), getSender());
                    return;
                }
                NormalizedNodeMessage nodeMessageResp = null;
                if (rpcResult.value() != null) {
                    nodeMessageResp = new NormalizedNodeMessage(YangInstanceIdentifier.of(), rpcResult.value());
                }
                requester.tell(new InvokeRpcMessageReply(nodeMessageResp, rpcResult.errors()), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                requester.tell(new Status.Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }

    private static void sendResult(final ListenableFuture<Optional<NormalizedNode>> feature,
            final YangInstanceIdentifier path, final ActorRef sender, final ActorRef self) {
        Futures.addCallback(feature, new FutureCallback<>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode> result) {
                if (result.isEmpty()) {
                    sender.tell(new EmptyReadResponse(), self);
                    return;
                }
                sender.tell(new NormalizedNodeMessage(path, result.orElseThrow()), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                sender.tell(new Status.Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }
}
