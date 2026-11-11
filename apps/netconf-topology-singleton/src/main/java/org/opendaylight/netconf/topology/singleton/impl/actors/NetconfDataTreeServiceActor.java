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
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.actor.UntypedAbstractActor;
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

public final class NetconfDataTreeServiceActor extends UntypedAbstractActor {
    private final DataStoreService dataStoreService;

    private NetconfDataTreeServiceActor(final DataStoreService dataStoreService) {
        this.dataStoreService = dataStoreService;
    }

    static Props props(final DataStoreService netconfService) {
        return Props.create(NetconfDataTreeServiceActor.class, () -> new NetconfDataTreeServiceActor(netconfService));
    }

    @Override
    public void onReceive(final Object message) {
        switch (message) {
            case GetRequest getRequest -> {
                final YangInstanceIdentifier path = getRequest.path();
                final ListenableFuture<Optional<NormalizedNode>> future = dataStoreService.get(getRequest.store(),
                        getRequest.path(), getRequest.fields());
                sendResult(future, path, sender(), self());
            }
            case MergeEditConfigRequest request -> invokeRpcCall(() -> dataStoreService.merge(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode()), sender(), self());
            case ReplaceEditConfigRequest request -> invokeRpcCall(() -> dataStoreService.replace(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode()), sender(), self());
            case CreateEditConfigRequest request -> invokeRpcCall(() -> dataStoreService.create(
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode()), sender(), self());
            case DeleteEditConfigRequest request ->
                invokeRpcCall(() -> dataStoreService.delete(request.getPath()), sender(), self());
            case RemoveEditConfigRequest request ->
                invokeRpcCall(() -> dataStoreService.remove(request.getPath()), sender(), self());
            case CommitRequest unused -> invokeRpcCall(dataStoreService::commit, sender(), self());
            case CancelChangesRequest unused -> invokeRpcCall(dataStoreService::cancel, sender(), self());
            default -> {
                unhandled(message);
                return;
            }
        }
        // Each request gets its own actor, which is done once it has issued its operation. The reply is sent when
        // the operation completes, even though the actor is stopped by then.
        context().stop(self());
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
