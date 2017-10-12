/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.util.Timeout;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMRpcService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyYangTextSourceProvider;
import org.opendaylight.netconf.topology.singleton.impl.SlaveSalFacade;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSlaveActor;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public final class NetconfNodeActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeActor.class);

    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;

    private SchemaSourceRegistry schemaRegistry;
    private SchemaRepository schemaRepository;
    private Timeout actorResponseWaitTime;
    private RemoteDeviceId id;
    private NetconfTopologySetup setup;
    private List<SourceIdentifier> sourceIdentifiers;
    private DOMRpcService deviceRpc;
    private SlaveSalFacade slaveSalManager;
    private DOMDataBroker deviceDataBroker;
    //readTxActor can be shared
    private ActorRef readTxActor;
    private List<SchemaSourceRegistration<YangTextSchemaSource>> registeredSchemas;

    public static Props props(final NetconfTopologySetup setup,
                              final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                              final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime,
                              final DOMMountPointService mountPointService) {
        return Props.create(NetconfNodeActor.class, () ->
                new NetconfNodeActor(setup, id, schemaRegistry, schemaRepository, actorResponseWaitTime,
                        mountPointService));
    }

    private NetconfNodeActor(final NetconfTopologySetup setup,
                             final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                             final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime,
                             final DOMMountPointService mountPointService) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = schemaRegistry;
        this.schemaRepository = schemaRepository;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.writeTxIdleTimeout = setup.getIdleTimeout();
        this.mountPointService = mountPointService;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CreateInitialMasterActorData) { // master

            final CreateInitialMasterActorData masterActorData = (CreateInitialMasterActorData) message;
            sourceIdentifiers = masterActorData.getSourceIndentifiers();
            this.deviceDataBroker = masterActorData.getDeviceDataBroker();
            final DOMDataReadOnlyTransaction tx = deviceDataBroker.newReadOnlyTransaction();
            readTxActor = context().actorOf(ReadTransactionActor.props(tx));
            this.deviceRpc = masterActorData.getDeviceRpc();

            sender().tell(new MasterActorDataInitialized(), self());

            LOG.debug("{}: Master is ready.", id);

        } else if (message instanceof  RefreshSetupMasterActorData) {
            setup = ((RefreshSetupMasterActorData) message).getNetconfTopologyDeviceSetup();
            id = ((RefreshSetupMasterActorData) message).getRemoteDeviceId();
            sender().tell(new MasterActorDataInitialized(), self());
        } else if (message instanceof AskForMasterMountPoint) { // master
            // only master contains reference to deviceDataBroker
            if (deviceDataBroker != null) {
                getSender().tell(new RegisterMountPoint(sourceIdentifiers), getSelf());
            }

        } else if (message instanceof YangTextSchemaSourceRequest) { // master

            final YangTextSchemaSourceRequest yangTextSchemaSourceRequest = (YangTextSchemaSourceRequest) message;
            sendYangTextSchemaSourceProxy(yangTextSchemaSourceRequest.getSourceIdentifier(), sender());

        } else if (message instanceof NewReadTransactionRequest) { // master

            sender().tell(new NewReadTransactionReply(readTxActor), self());

        } else if (message instanceof NewWriteTransactionRequest) { // master
            try {
                final DOMDataWriteTransaction tx = deviceDataBroker.newWriteOnlyTransaction();
                final ActorRef txActor = context().actorOf(WriteTransactionActor.props(tx, writeTxIdleTimeout));
                sender().tell(new NewWriteTransactionReply(txActor), self());
            } catch (final Exception t) {
                sender().tell(t, self());
            }

        } else if (message instanceof NewReadWriteTransactionRequest) {
            try {
                final DOMDataReadWriteTransaction tx = deviceDataBroker.newReadWriteTransaction();
                final ActorRef txActor = context().actorOf(ReadWriteTransactionActor.props(tx, writeTxIdleTimeout));
                sender().tell(new NewReadWriteTransactionReply(txActor), self());
            } catch (final Exception t) {
                sender().tell(t, self());
            }
        } else if (message instanceof InvokeRpcMessage) { // master

            final InvokeRpcMessage invokeRpcMessage = (InvokeRpcMessage) message;
            invokeSlaveRpc(invokeRpcMessage.getSchemaPath(), invokeRpcMessage.getNormalizedNodeMessage(), sender());

        } else if (message instanceof RegisterMountPoint) { //slaves

            sourceIdentifiers = ((RegisterMountPoint) message).getSourceIndentifiers();
            registerSlaveMountPoint(getSender());

        } else if (message instanceof UnregisterSlaveMountPoint) { //slaves
            if (slaveSalManager != null) {
                slaveSalManager.close();
                slaveSalManager = null;
            }
        } else if (message instanceof RefreshSlaveActor) { //slave
            actorResponseWaitTime = ((RefreshSlaveActor) message).getActorResponseWaitTime();
            id = ((RefreshSlaveActor) message).getId();
            schemaRegistry = ((RefreshSlaveActor) message).getSchemaRegistry();
            setup = ((RefreshSlaveActor) message).getSetup();
            schemaRepository = ((RefreshSlaveActor) message).getSchemaRepository();
        }

    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        closeSchemaSourceRegistrations();
    }

    private void sendYangTextSchemaSourceProxy(final SourceIdentifier sourceIdentifier, final ActorRef sender) {
        final ListenableFuture<@NonNull YangTextSchemaSource> yangTextSchemaSource =
                schemaRepository.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        Futures.addCallback(yangTextSchemaSource, new FutureCallback<YangTextSchemaSource>() {
            @Override
            public void onSuccess(final YangTextSchemaSource yangTextSchemaSource) {
                try {
                    sender.tell(new YangTextSchemaSourceSerializationProxy(yangTextSchemaSource), getSelf());
                } catch (final IOException exception) {
                    sender.tell(exception.getCause(), getSelf());
                }
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                sender.tell(throwable, getSelf());
            }
        }, MoreExecutors.directExecutor());
    }

    private void invokeSlaveRpc(final SchemaPath schemaPath, final NormalizedNodeMessage normalizedNodeMessage,
                                final ActorRef recipient) {

        final CheckedFuture<DOMRpcResult, DOMRpcException> rpcResult =
                deviceRpc.invokeRpc(schemaPath, normalizedNodeMessage.getNode());

        Futures.addCallback(rpcResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(@Nullable final DOMRpcResult domRpcResult) {
                if (domRpcResult == null) {
                    recipient.tell(new EmptyResultResponse(), getSender());
                    return;
                }
                NormalizedNodeMessage nodeMessageReply = null;
                if (domRpcResult.getResult() != null) {
                    nodeMessageReply = new NormalizedNodeMessage(YangInstanceIdentifier.EMPTY,
                            domRpcResult.getResult());
                }
                recipient.tell(new InvokeRpcMessageReply(nodeMessageReply, domRpcResult.getErrors()), getSelf());
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                recipient.tell(throwable, getSelf());
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerSlaveMountPoint(final ActorRef masterReference) {
        if (this.slaveSalManager != null) {
            slaveSalManager.close();
        }
        closeSchemaSourceRegistrations();
        slaveSalManager = new SlaveSalFacade(id, setup.getActorSystem(), actorResponseWaitTime,
                mountPointService);

        final ListenableFuture<SchemaContext> remoteSchemaContext = getSchemaContext(masterReference);
        final DOMRpcService deviceRpcService = getDOMRpcService(masterReference);

        Futures.addCallback(remoteSchemaContext, new FutureCallback<SchemaContext>() {
            @Override
            public void onSuccess(final SchemaContext result) {
                LOG.info("{}: Schema context resolved: {}", id, result.getModules());
                slaveSalManager.registerSlaveMountPoint(result, deviceRpcService, masterReference);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                LOG.error("{}: Failed to register mount point: {}", id, throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private DOMRpcService getDOMRpcService(final ActorRef masterReference) {
        return new ProxyDOMRpcService(setup.getActorSystem(), masterReference, id, actorResponseWaitTime);
    }

    private ListenableFuture<SchemaContext> getSchemaContext(final ActorRef masterReference) {

        final RemoteYangTextSourceProvider remoteYangTextSourceProvider =
                new ProxyYangTextSourceProvider(masterReference, getContext(), actorResponseWaitTime);
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider,
                getContext().dispatcher());

        registeredSchemas = sourceIdentifiers.stream()
                .map(sourceId ->
                        schemaRegistry.registerSchemaSource(remoteProvider, PotentialSchemaSource.create(sourceId,
                                YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())))
                .collect(Collectors.toList());

        final SchemaContextFactory schemaContextFactory
                = schemaRepository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);

        return schemaContextFactory.createSchemaContext(sourceIdentifiers);
    }

    private void closeSchemaSourceRegistrations() {
        if (registeredSchemas != null) {
            registeredSchemas.forEach(SchemaSourceRegistration::close);
            registeredSchemas = null;
        }
    }

}
