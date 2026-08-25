/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static java.util.Objects.requireNonNull;

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
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
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
        return invokeRpc(new CreateEditConfigRequest(new NormalizedNodeMessage(path, data)), Operation.CREATE);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} via actor {}", id, path, masterActor);
        return invokeRpc(new DeleteEditConfigRequest(path), Operation.DELETE);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {} via actor {}", id, path, masterActor);
        return invokeRpc(new RemoveEditConfigRequest(path), Operation.REMOVE);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Merge {} via actor {}", id, path, masterActor);
        return invokeRpc(new MergeEditConfigRequest(new NormalizedNodeMessage(path, data)), Operation.MERGE);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        LOG.debug("{}: Replace {} via actor {}", id, path, masterActor);
        return invokeRpc(new ReplaceEditConfigRequest(new NormalizedNodeMessage(path, data)), Operation.REPLACE);
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
        return invokeRpc(new CommitRequest(), Operation.COMMIT);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        LOG.debug("{}: Discard changes via actor {}", id, masterActor);
        return invokeRpc(new CancelChangesRequest(), Operation.CANCEL);
    }

    private ListenableFuture<? extends DOMRpcResult> invokeRpc(final Object request, final Operation operation) {
        final var req = Patterns.ask(masterActor, request, askTimeout);
        final var settableFuture = SettableFuture.<DOMRpcResult>create();
        req.whenComplete((response, failure) -> {
            if (failure != null) {
                LOG.debug("{}: {} failed", id, operation, failure);
                settableFuture.setException(new NetconfServiceFailedException(
                    "%s: %s of operation failed".formatted(id, operation), processFailure(failure)));
                return;
            }
            switch (response) {
                case InvokeRpcMessageReply reply -> {
                    LOG.debug("{}: {} succeeded", id, operation);
                    settableFuture.set(mapInvokeRpcMessageReplyToDOMRpcResult(reply));
                }
                case EmptyResultResponse unused -> {
                    LOG.debug("{}: {} succeeded with no result", id, operation);
                    settableFuture.set(new DefaultDOMRpcResult());
                }
                // A null response cannot be matched by a pattern and would throw, so it shares the
                // unexpected-type path with everything else we do not recognize.
                case null, default -> {
                    settableFuture.setException(
                        new ClusteringRpcException("%s operation returned unexpected type".formatted(operation)));
                    LOG.error("{}: {} via actor {} returned unexpected type", id, operation, masterActor);
                }
            }
        });
        return settableFuture;
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

    private static DOMRpcResult mapInvokeRpcMessageReplyToDOMRpcResult(final InvokeRpcMessageReply reply) {
        final var message = reply.getNormalizedNodeMessage();
        if (message == null) {
            return new DefaultDOMRpcResult(new ArrayList<>(reply.getRpcErrors()));
        }
        return new DefaultDOMRpcResult((ContainerNode) message.getNode(), reply.getRpcErrors());
    }

    /**
     * Operations dispatched through {@link #invokeRpc(Object, Operation)}, each carrying the human-readable
     * label used in log messages and exception text.
     */
    private enum Operation {
        CREATE("Create"),
        DELETE("Delete"),
        REMOVE("Remove"),
        MERGE("Merge"),
        REPLACE("Replace"),
        COMMIT("Commit"),
        CANCEL("Discard changes");

        private final String label;

        Operation(final String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
