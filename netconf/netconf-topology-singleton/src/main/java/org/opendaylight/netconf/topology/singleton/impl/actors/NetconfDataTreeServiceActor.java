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
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.CommitInfo;
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
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public final class NetconfDataTreeServiceActor extends UntypedAbstractActor {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDataTreeServiceActor.class);

    private final NetconfDataTreeService netconfService;
    private final long idleTimeout;

    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();

    private NetconfDataTreeServiceActor(final NetconfDataTreeService netconfService, final Duration idleTimeout) {
        this.netconfService = netconfService;
        this.idleTimeout = idleTimeout.toSeconds();
        if (this.idleTimeout > 0) {
            context().setReceiveTimeout(idleTimeout);
        }
    }

    static Props props(final NetconfDataTreeService netconfService, final Duration idleTimeout) {
        return Props.create(NetconfDataTreeServiceActor.class, () ->
            new NetconfDataTreeServiceActor(netconfService, idleTimeout));
    }

    @Override
    public void onReceive(final Object message) {
        if (message instanceof GetWithFieldsRequest) {
            final GetWithFieldsRequest getRequest = (GetWithFieldsRequest) message;
            final YangInstanceIdentifier path = getRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode<?, ?>>> future = netconfService.get(
                    getRequest.getPath(), getRequest.getFields());
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetRequest) {
            final GetRequest getRequest = (GetRequest) message;
            final YangInstanceIdentifier path = getRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode<?, ?>>> future = netconfService.get(path);
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetConfigWithFieldsRequest) {
            final GetConfigWithFieldsRequest getConfigRequest = (GetConfigWithFieldsRequest) message;
            final YangInstanceIdentifier path = getConfigRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode<?, ?>>> future = netconfService.getConfig(
                    path, getConfigRequest.getFields());
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof GetConfigRequest) {
            final GetConfigRequest getConfigRequest = (GetConfigRequest) message;
            final YangInstanceIdentifier path = getConfigRequest.getPath();
            final ListenableFuture<Optional<NormalizedNode<?, ?>>> future = netconfService.getConfig(path);
            context().stop(self());
            sendResult(future, path, sender(), self());
        } else if (message instanceof LockRequest) {
            resultsFutures.addAll(netconfService.lock());
        } else if (message instanceof MergeEditConfigRequest) {
            final MergeEditConfigRequest request = (MergeEditConfigRequest) message;
            resultsFutures.add(netconfService.merge(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation())));
        } else if (message instanceof ReplaceEditConfigRequest) {
            final ReplaceEditConfigRequest request = (ReplaceEditConfigRequest) message;
            resultsFutures.add(netconfService.replace(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation())));
        } else if (message instanceof CreateEditConfigRequest) {
            final CreateEditConfigRequest request = (CreateEditConfigRequest) message;
            resultsFutures.add(netconfService.create(
                request.getStore(),
                request.getNormalizedNodeMessage().getIdentifier(),
                request.getNormalizedNodeMessage().getNode(),
                Optional.ofNullable(request.getDefaultOperation())));
        } else if (message instanceof DeleteEditConfigRequest) {
            final DeleteEditConfigRequest request = (DeleteEditConfigRequest) message;
            resultsFutures.add(netconfService.delete(request.getStore(), request.getPath()));
        } else if (message instanceof RemoveEditConfigRequest) {
            final RemoveEditConfigRequest request = (RemoveEditConfigRequest) message;
            resultsFutures.add(netconfService.remove(request.getStore(), request.getPath()));
        } else if (message instanceof CommitRequest) {
            context().stop(self());
            submit(sender(), self());
        } else if (message instanceof DiscardChangesRequest) {
            netconfService.discardChanges();
        } else if (message instanceof UnlockRequest) {
            context().stop(self());
            netconfService.unlock();
        } else if (message instanceof ReceiveTimeout) {
            LOG.warn("Haven't received any message for {} seconds, cancelling transaction and stopping actor",
                idleTimeout);
            netconfService.discardChanges();
            netconfService.unlock();
            context().stop(self());
        } else {
            unhandled(message);
        }
    }

    private void submit(final ActorRef requester, final ActorRef self) {
        final ListenableFuture<? extends CommitInfo> submitFuture = netconfService.commit(resultsFutures);
        FluentFuture.from(submitFuture).addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                requester.tell(new Status.Success(null), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                requester.tell(new Status.Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }

    private void sendResult(final ListenableFuture<Optional<NormalizedNode<?, ?>>> feature,
                            final YangInstanceIdentifier path,
                            final ActorRef sender, final ActorRef self) {
        FluentFuture.from(feature).addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> result) {
                if (!result.isPresent()) {
                    sender.tell(new EmptyReadResponse(), self);
                    return;
                }
                sender.tell(new NormalizedNodeMessage(path, result.get()), self);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                sender.tell(new Status.Failure(throwable), self);
            }
        }, MoreExecutors.directExecutor());
    }
}
