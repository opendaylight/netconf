/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActorProxyNetconfServiceFacade implements ProxyNetconfServiceFacade {
    private static final Logger LOG = LoggerFactory.getLogger(ActorProxyNetconfServiceFacade.class);

    private final ActorRef masterActor;
    private final RemoteDeviceId id;
    private final Duration askTimeout;

    public ActorProxyNetconfServiceFacade(final ActorRef masterActor, final RemoteDeviceId id,
            final Duration askTimeout) {
        this.masterActor = requireNonNull(masterActor);
        this.id = requireNonNull(id);
        this.askTimeout = requireNonNull(askTimeout);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Create {} via actor {}", id, path, masterActor);
        masterActor.tell(new CreateEditConfigRequest(new NormalizedNodeMessage(path, data)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} via actor {}", id, path, masterActor);
        masterActor.tell(new DeleteEditConfigRequest(path), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {} via actor {}", id, path, masterActor);
        masterActor.tell(new RemoveEditConfigRequest(path), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Merge {} via actor {}", id, path, masterActor);
        masterActor.tell(new MergeEditConfigRequest(new NormalizedNodeMessage(path, data)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Replace {} via actor {}", id, path, masterActor);
        masterActor.tell(new ReplaceEditConfigRequest(new NormalizedNodeMessage(path, data)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get {} {} via actor {}", id, store, path, masterActor);
        final var future = Patterns.ask(masterActor, new GetRequest(store, path, fields), askTimeout);
        return read(future, store, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit via actor {}", id, masterActor);

        final var req = Patterns.ask(masterActor, new CommitRequest(), askTimeout);
        final var settableFuture = SettableFuture.<DOMRpcResult>create();
        req.whenComplete((response, failure) -> {
            if (failure != null) {
                LOG.debug("{}: Commit failed", id, failure);
                settableFuture.setException(new NetconfServiceFailedException(
                    "%s: Commit of operation failed".formatted(id), processFailure(failure)));
            } else if (response instanceof InvokeRpcMessageReply) {
                LOG.debug("{}: Commit succeeded", id);
                settableFuture.set(mapInvokeRpcMessageReplyToDOMRpcResult((InvokeRpcMessageReply) response));
            } else {
                settableFuture.setException(new ClusteringRpcException("Commit operation returned unexpected type"));
                LOG.error("{}: Commit via actor {} returned unexpected type", id, masterActor);
            }
        });
        return settableFuture;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        LOG.debug("{}: Discard changes via actor {}", id, masterActor);
        final var cancelRequest = SettableFuture.<DOMRpcResult>create();
        final var req = Patterns.ask(masterActor, new CancelChangesRequest(), askTimeout);
        req.whenComplete((response, failure) -> {
            if (failure != null) {
                cancelRequest.setException(failure);
            } else if (response instanceof InvokeRpcMessageReply reply) {
                cancelRequest.set(mapInvokeRpcMessageReplyToDOMRpcResult(reply));
            } else {
                cancelRequest.setException(
                    new ClusteringRpcException("Discard changes operation returned unexpected type"));
                LOG.error("{}: Discard changes via actor {} returned unexpected type", id, masterActor);
            }
        });
        return cancelRequest;
    }


    private SettableFuture<Optional<NormalizedNode>> read(final CompletionStage<Object> future,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final var settableFuture = SettableFuture.<Optional<NormalizedNode>>create();
        future.whenComplete((response, failure) -> {
            if (failure != null) {
                LOG.debug("{}: Read {} {} failed", id, store, path, failure);

                final var processedFailure = processFailure(failure);
                if (processedFailure instanceof ReadFailedException) {
                    settableFuture.setException(processedFailure);
                } else {
                    settableFuture.setException(new ReadFailedException(
                        "Read of store " + store + " path " + path + " failed", processedFailure));
                }
                return;
            }

            LOG.debug("{}: Read {} {} succeeded: {}", id, store, path, response);
            if (response instanceof EmptyReadResponse) {
                settableFuture.set(Optional.empty());
                return;
            }
            if (response instanceof NormalizedNodeMessage data) {
                settableFuture.set(Optional.of(data.getNode()));
            }
        });
        return settableFuture;
    }

    private Throwable processFailure(final Throwable failure) {
        return failure instanceof AskTimeoutException
            ? NetconfTopologyUtils.createMasterIsDownException(id, (Exception) failure) : failure;
    }

    // FIXME: this is being used in contexts where we should be waiting for a reply.
    //        If editConfig fails, this override it reply with empty success future.
    private static ListenableFuture<? extends DOMRpcResult> createResult() {
        return Futures.immediateFuture(new DefaultDOMRpcResult());
    }

    private static DOMRpcResult mapInvokeRpcMessageReplyToDOMRpcResult(final InvokeRpcMessageReply reply) {
        final var message = reply.getNormalizedNodeMessage();
        if (message == null) {
            return new DefaultDOMRpcResult(new ArrayList<>(reply.getRpcErrors()));
        }
        return new DefaultDOMRpcResult((ContainerNode) message.getNode(), reply.getRpcErrors());
    }
}
