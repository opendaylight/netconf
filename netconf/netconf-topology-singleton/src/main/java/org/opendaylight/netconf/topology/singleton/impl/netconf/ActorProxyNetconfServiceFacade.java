/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.ClusteringRpcException;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
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
    public ListenableFuture<DOMRpcResult> lock() {
        LOG.debug("{}: Lock via actor {}", id, masterActor);
        final SettableFuture<DOMRpcResult> lockResult = SettableFuture.create();
        // TODO: check AskPattern
        final Future<Object> future = Patterns.ask(masterActor, new LockRequest(), askTimeout);
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    lockResult.setException(failure);
                } else if (response instanceof DOMRpcResult) {
                    lockResult.set((DOMRpcResult) response);
                } else {
                    lockResult.setException(new ClusteringRpcException("Lock operation returned unexpected type"));
                    LOG.error("{}: Lock via actor {} returned unexpected type", id, masterActor);
                }
            }
        }, executionContext);
        return lockResult;
    }

    @Override
    public ListenableFuture<DOMRpcResult> unlock() {
        LOG.debug("{}: Unlock via actor {}", id, masterActor);
        final SettableFuture<DOMRpcResult> unlockResult = SettableFuture.create();
        final Future<Object> future = Patterns.ask(masterActor, new UnlockRequest(), askTimeout);
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    unlockResult.setException(failure);
                } else if (response instanceof DOMRpcResult) {
                    unlockResult.set((DOMRpcResult) response);
                } else {
                    unlockResult.setException(new ClusteringRpcException("Unlock operation returned unexpected type"));
                    LOG.error("{}: Unlock via actor {} returned unexpected type", id, masterActor);
                }
            }
        }, executionContext);
        return unlockResult;
    }

    @Override
    public ListenableFuture<DOMRpcResult> discardChanges() {
        LOG.debug("{}: Discard changes via actor {}", id, masterActor);
        final SettableFuture<DOMRpcResult> discardChangesResult = SettableFuture.create();
        final Future<Object> future = Patterns.ask(masterActor, new DiscardChangesRequest(), askTimeout);
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    discardChangesResult.setException(failure);
                } else if (response instanceof DOMRpcResult) {
                    discardChangesResult.set((DOMRpcResult) response);
                } else {
                    discardChangesResult.setException(
                        new ClusteringRpcException("Discard changes operation returned unexpected type"));
                    LOG.error("{}: Discard changes  via actor {} returned unexpected type", id, masterActor);
                }
            }
        }, executionContext);
        return discardChangesResult;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(final YangInstanceIdentifier path) {
        LOG.debug("{}: Get {} {} via actor {}", id, OPERATIONAL, path, masterActor);
        final Future<Object> future = Patterns.ask(masterActor, new GetRequest(path), askTimeout);
        return read(future, OPERATIONAL, path);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get {} {} with fields {} via actor {}", id, OPERATIONAL, path, fields, masterActor);
        final Future<Object> future = Patterns.ask(masterActor, new GetWithFieldsRequest(path, fields), askTimeout);
        return read(future, OPERATIONAL, path);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final YangInstanceIdentifier path) {
        LOG.debug("{}: GetConfig {} {} via actor {}", id, CONFIGURATION, path, masterActor);
        final Future<Object> future = Patterns.ask(masterActor, new GetConfigRequest(path), askTimeout);
        return read(future, CONFIGURATION, path);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: GetConfig {} {} with fields {} via actor {}", id, CONFIGURATION, path, fields, masterActor);
        final Future<Object> future = Patterns.ask(masterActor,
                new GetConfigWithFieldsRequest(path, fields), askTimeout);
        return read(future, CONFIGURATION, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        LOG.debug("{}: Merge {} {} via actor {}", id, store, path, masterActor);
        masterActor.tell(new MergeEditConfigRequest(
            store, new NormalizedNodeMessage(path, data), defaultOperation.orElse(null)), ActorRef.noSender());
        return createResult();

    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        LOG.debug("{}: Replace {} {} via actor {}", id, store, path, masterActor);

        masterActor.tell(new ReplaceEditConfigRequest(
            store, new NormalizedNodeMessage(path, data), defaultOperation.orElse(null)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final Optional<ModifyAction> defaultOperation) {
        LOG.debug("{}: Create {} {} via actor {}", id, store, path, masterActor);
        masterActor.tell(new CreateEditConfigRequest(
            store, new NormalizedNodeMessage(path, data), defaultOperation.orElse(null)), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {} via actor {}", id, store, path, masterActor);
        masterActor.tell(new DeleteEditConfigRequest(store, path), ActorRef.noSender());
        return createResult();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {} {} via actor {}", id, store, path, masterActor);
        masterActor.tell(new RemoveEditConfigRequest(store, path), ActorRef.noSender());
        return createResult();
    }

    // TODO: replace with DOMRpcResult
    @Override
    public ListenableFuture<? extends CommitInfo> commit(
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        LOG.debug("{}: Commit via actor {}", id, masterActor);

        final Future<Object> future = Patterns.ask(masterActor, new CommitRequest(), askTimeout);
        final SettableFuture<CommitInfo> settableFuture = SettableFuture.create();
        future.onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure != null) {
                    LOG.debug("{}: Commit failed", id, failure);
                    settableFuture.setException(newNetconfServiceFailedException(processFailure(failure)));
                    return;
                }

                LOG.debug("{}: Commit succeeded", id);
                settableFuture.set(CommitInfo.empty());
            }

            private NetconfServiceFailedException newNetconfServiceFailedException(final Throwable failure) {
                return new NetconfServiceFailedException(String.format("%s: Commit of operation failed",
                    getDeviceId()), failure);
            }
        }, executionContext);
        return settableFuture;
    }

    @Override
    public @NonNull Object getDeviceId() {
        return id;
    }

    private SettableFuture<Optional<NormalizedNode<?, ?>>> read(final Future<Object> future,
                                                                final LogicalDatastoreType store,
                                                                final YangInstanceIdentifier path) {
        final SettableFuture<Optional<NormalizedNode<?, ?>>> settableFuture = SettableFuture.create();
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

                if (response instanceof NormalizedNodeMessage) {
                    final NormalizedNodeMessage data = (NormalizedNodeMessage) response;
                    settableFuture.set(Optional.of(data.getNode()));
                }
            }
        }, executionContext);

        return settableFuture;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private Throwable processFailure(final Throwable failure) {
        return failure instanceof AskTimeoutException
            ? NetconfTopologyUtils.createMasterIsDownException(id, (Exception) failure) : failure;
    }

    private ListenableFuture<? extends DOMRpcResult> createResult() {
        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();
        settableFuture.set(new DefaultDOMRpcResult());
        return settableFuture;
    }
}
