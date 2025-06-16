/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CommitRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.CreateEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.DeleteEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.GetRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.MergeEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.PutEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.netconf.RemoveEditConfigRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyReadResponse;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class ActorProxyNetconfServiceFacade implements ProxyNetconfServiceFacade {
    private static final Logger LOG = LoggerFactory.getLogger(ActorProxyNetconfServiceFacade.class);

    private final ActorRef masterActor;
    private final RemoteDeviceId id;
    private final ExecutionContext executionContext;
    private final Timeout askTimeout;

    public ActorProxyNetconfServiceFacade(final ActorRef masterActor, final RemoteDeviceId id,
                                          final ExecutionContext executionContext, final Timeout askTimeout) {
        this.masterActor = Objects.requireNonNull(masterActor);
        this.id = Objects.requireNonNull(id);
        this.executionContext = Objects.requireNonNull(executionContext);
        this.askTimeout = Objects.requireNonNull(askTimeout);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> createData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Create {} {} via actor {}", id, path, masterActor);
        masterActor.tell(new CreateEditConfigRequest(
            new NormalizedNodeMessage(path, data)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(DatabindPath.Data path) {
        LOG.debug("{}: Delete {} via actor {}", id, path, masterActor);
        masterActor.tell(new DeleteEditConfigRequest(path), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(DatabindPath.Data path) {
        LOG.debug("{}: Remove {} via actor {}", id, path, masterActor);
        masterActor.tell(new RemoveEditConfigRequest(path), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Merge {} via actor {}", id, path, masterActor);
        masterActor.tell(new MergeEditConfigRequest(
            new NormalizedNodeMessage(path, data)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Put {} via actor {}", id, path, masterActor);
        masterActor.tell(new PutEditConfigRequest(new NormalizedNodeMessage(path, data)),
            ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(DatabindPath.Data path, DataGetParams params) {
        ContentParam content = params.content();
        LOG.debug("{}: GetConfig {} {} via actor {}", id, content, path, masterActor);
        final Future<Object> future = Patterns.ask(masterActor, new GetRequest(path, params), askTimeout);
        return read(future, content, path.instance());
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit via actor {}", id, masterActor);

        final Future<Object> future = Patterns.ask(masterActor, new CommitRequest(), askTimeout);
        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Commit failed", id, failure);
                    settableFuture.setException(newNetconfServiceFailedException(processFailure(failure)));
                } else if (response instanceof InvokeRpcMessageReply) {
                    LOG.debug("{}: Commit succeeded", id);
                    settableFuture.set(mapInvokeRpcMessageReplyToDOMRpcResult((InvokeRpcMessageReply) response));
                } else {
                    settableFuture.setException(
                        new ClusteringRpcException("Commit operation returned unexpected type"));
                    LOG.error("{}: Commit via actor {} returned unexpected type", id, masterActor);
                }
            }

            private NetconfServiceFailedException newNetconfServiceFailedException(final Throwable failure) {
                return new NetconfServiceFailedException(String.format("%s: Commit of operation failed",
                    id), failure);
            }
        }, executionContext);
        return settableFuture;
    }

    private SettableFuture<Optional<NormalizedNode>> read(final Future<Object> future, final ContentParam store,
                                                          final YangInstanceIdentifier path) {
        final SettableFuture<Optional<NormalizedNode>> settableFuture = SettableFuture.create();
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Read {} {} failed", id, store, path, failure);

                    final Throwable processedFailure = processFailure(failure);
                    if (processedFailure instanceof ReadFailedException) {
                        settableFuture.setException(processedFailure);
                    } else {
                        settableFuture.setException(new ReadFailedException("Read of store " + store + " path " + path
                            + " failed", processedFailure));
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
            }
        }, executionContext);

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
        if (reply.getNormalizedNodeMessage() == null) {
            return new DefaultDOMRpcResult(new ArrayList<>(reply.getRpcErrors()));
        } else {
            return new DefaultDOMRpcResult((ContainerNode) reply.getNormalizedNodeMessage().getNode(),
                reply.getRpcErrors());
        }
    }
}
