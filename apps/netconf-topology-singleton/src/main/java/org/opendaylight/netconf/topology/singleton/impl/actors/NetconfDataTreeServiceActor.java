/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.actors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ReceiveTimeout;
import org.apache.pekko.actor.Status;
import org.apache.pekko.actor.UntypedAbstractActor;
import org.apache.pekko.util.JavaDurationConverters;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CancelChangesRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.ReplaceEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDataTreeServiceActor extends UntypedAbstractActor {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataTreeServiceActor.class);

    private final DataStoreService dataStoreService;
    private final long idleTimeout;

    private NetconfDataTreeServiceActor(final DataStoreService dataStoreService, final Duration idleTimeout) {
        this.dataStoreService = dataStoreService;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(JavaDurationConverters.asFiniteDuration(idleTimeout));
        }
    }

    static Props props(final DataStoreService netconfService, final Duration idleTimeout) {
        return Props.create(NetconfDataTreeServiceActor.class, () ->
            new NetconfDataTreeServiceActor(netconfService, idleTimeout));
    }

    @Override
    public void onReceive(final Object message) {
        if (message instanceof GetRequest getRequest) {
            final YangInstanceIdentifier path = getRequest.path();
            final ListenableFuture<Optional<NormalizedNode>> future = dataStoreService.get(getRequest.store(),
                    getRequest.path(), getRequest.fields());
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof MergeEditConfigRequest request) {
            dataStoreService.merge(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode());
        } else if (message instanceof ReplaceEditConfigRequest request) {
            dataStoreService.replace(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode());
        } else if (message instanceof CreateEditConfigRequest request) {
            dataStoreService.create(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode());
        } else if (message instanceof DeleteEditConfigRequest request) {
            dataStoreService.delete(request.getPath());
        } else if (message instanceof RemoveEditConfigRequest request) {
            dataStoreService.remove(request.getPath());
        } else if (message instanceof CommitRequest) {
            submit(sender(), self());
        } else if (message instanceof CancelChangesRequest) {
            invokeRpcCall(dataStoreService::cancel, sender(), self());
        } else if (message instanceof ReceiveTimeout) {
            LOG.warn("Haven't received any message for {} seconds, cancelling transaction and stopping actor",
                idleTimeout);
            invokeRpcCall(dataStoreService::cancel, sender(), self());
            context().stop(self());
        } else {
            unhandled(message);
        }
    }

    private void submit(final ActorRef requester, final ActorRef self) {
        Futures.addCallback(dataStoreService.commit(), new FutureCallback<DOMRpcResult>() {
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
