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
import akka.util.Timeout;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.cluster.common.actor.AbstractUntypedActor;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteOperationTxProcessor;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMRpcService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyYangTextSourceProvider;
import org.opendaylight.netconf.topology.singleton.impl.RemoteOperationTxProcessorImpl;
import org.opendaylight.netconf.topology.singleton.impl.SlaveSalFacade;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.CancelRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.DeleteRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ExistsRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.MergeRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.OpenTransaction;
import org.opendaylight.netconf.topology.singleton.messages.transactions.PutRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.ReadRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.SubmitRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.TransactionRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfNodeActor extends AbstractUntypedActor {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeActor.class);

    private NetconfTopologySetup setup;
    private RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final SchemaRepository schemaRepository;

    private RemoteOperationTxProcessor operationsProcessor;
    private List<SourceIdentifier> sourceIdentifiers;
    private DOMRpcService deviceRpc;
    private SlaveSalFacade slaveSalManager;
    private final Timeout actorResponseWaitTime;

    public static Props props(final NetconfTopologySetup setup,
                              final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                              final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime) {
        return Props.create(NetconfNodeActor.class, () ->
                new NetconfNodeActor(setup, id, schemaRegistry, schemaRepository, actorResponseWaitTime));
    }

    private NetconfNodeActor(final NetconfTopologySetup setup,
                             final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                             final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = schemaRegistry;
        this.schemaRepository = schemaRepository;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public void postStop() throws Exception {
        LOG.info("{}: Stopping NetconfNodeActor", id);
    }

    @Override
    public void handleReceive(final Object message) throws Exception {
        if (message instanceof CreateInitialMasterActorData) {
            // master
            onCreateInitialMasterActorData((CreateInitialMasterActorData) message);
        } else if (message instanceof  RefreshSetupMasterActorData) {
            onRefreshSetupMasterActorData((RefreshSetupMasterActorData) message);
        } else if (message instanceof AskForMasterMountPoint) {
            // master
            onAskForMasterMountPoint();
        } else if (message instanceof TransactionRequest) {
            // master
            resolveProxyCalls((TransactionRequest) message, sender(), getSelf());
        } else if (message instanceof YangTextSchemaSourceRequest) {
            // master
            onYangTextSchemaSource((YangTextSchemaSourceRequest) message);
        } else if (message instanceof InvokeRpcMessage) {
            onInvokeRpc((InvokeRpcMessage) message);
        } else if (message instanceof RegisterMountPoint) {
            //slaves
            onRegisterMountPoint((RegisterMountPoint) message);
        } else if (message instanceof UnregisterSlaveMountPoint) {
            //slaves
            onUnregisterSlaveMountPoint();
        } else {
            unknownMessage(message);
        }
    }

    private void onUnregisterSlaveMountPoint() {
        LOG.debug("{}: onUnregisterSlaveMountPoint", id);

        if (slaveSalManager != null) {
            slaveSalManager.close();
            slaveSalManager = null;
        }
    }

    private void onRegisterMountPoint(final RegisterMountPoint registerMountPoint) {
        LOG.debug("{}: RegisterMountPoint message received: {}", id, registerMountPoint);

        sourceIdentifiers = registerMountPoint.getSourceIndentifiers();
        registerSlaveMountPoint(getSender());
    }

    private void onCreateInitialMasterActorData(final CreateInitialMasterActorData initialMasterActorData) {
        LOG.debug("{}: CreateInitialMasterActorData message received: {}", id, initialMasterActorData);

        sourceIdentifiers = initialMasterActorData.getSourceIndentifiers();
        operationsProcessor =
                new RemoteOperationTxProcessorImpl(initialMasterActorData.getDeviceDataBroker(), id);
        deviceRpc = initialMasterActorData.getDeviceRpc();

        sender().tell(new MasterActorDataInitialized(), self());

        LOG.info("{}: Master is ready", id);
    }

    private void onRefreshSetupMasterActorData(final RefreshSetupMasterActorData refreshSetupMasterActorData) {
        LOG.debug("{}: RefreshSetupMasterActorData message received: {}", id, refreshSetupMasterActorData);

        setup = refreshSetupMasterActorData.getNetconfTopologyDeviceSetup();
        id = refreshSetupMasterActorData.getRemoteDeviceId();
        sender().tell(new MasterActorDataInitialized(), self());
    }

    private void onAskForMasterMountPoint() {
        LOG.debug("{}: onAskForMasterMountPoint", id);

        // only master contains reference to operations processor
        if (operationsProcessor != null) {
            getSender().tell(new RegisterMountPoint(sourceIdentifiers), getSelf());
        }
    }

    private void onYangTextSchemaSource(final YangTextSchemaSourceRequest sourceRequest) {
        LOG.debug("{}: YangTextSchemaSourceRequest message received: {}", id, sourceRequest);

        final ActorRef sender = sender();
        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> yangTextSchemaSource =
                schemaRepository.getSchemaSource(sourceRequest.getSourceIdentifier(), YangTextSchemaSource.class);

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
        });
    }

    private void onInvokeRpc(final InvokeRpcMessage invokeRpcMessage) {
        LOG.debug("{}: InvokeRpcMessage received: {}", id, invokeRpcMessage);

        final ActorRef recipient = sender();
        final CheckedFuture<DOMRpcResult, DOMRpcException> rpcResult =
                deviceRpc.invokeRpc(invokeRpcMessage.getSchemaPath(), invokeRpcMessage.getNormalizedNodeMessage().getNode());

        Futures.addCallback(rpcResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(@Nullable final DOMRpcResult domRpcResult) {
                if (domRpcResult == null) {
                    recipient.tell(new EmptyResultResponse(), getSelf());
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
        });
    }

    private void resolveProxyCalls(final TransactionRequest txOperationRequest,
                                   final ActorRef replyTo, final ActorRef futureSender) {
        LOG.debug("{}: TransactionRequest message received: {}", id, txOperationRequest);

        if (txOperationRequest instanceof OpenTransaction) {
            operationsProcessor.doOpenTransaction(replyTo, futureSender);
        } else if (txOperationRequest instanceof ReadRequest) {

            final ReadRequest readRequest = (ReadRequest) txOperationRequest;
            operationsProcessor.doRead(readRequest.getStore(), readRequest.getPath(), replyTo, futureSender);

        } else if (txOperationRequest instanceof ExistsRequest) {

            final ExistsRequest readRequest = (ExistsRequest) txOperationRequest;
            operationsProcessor.doExists(readRequest.getStore(), readRequest.getPath(), replyTo, futureSender);

        } else if (txOperationRequest instanceof MergeRequest) {

            final MergeRequest mergeRequest = (MergeRequest) txOperationRequest;
            operationsProcessor.doMerge(mergeRequest.getStore(), mergeRequest.getNormalizedNodeMessage());

        } else if (txOperationRequest instanceof PutRequest) {

            final PutRequest putRequest = (PutRequest) txOperationRequest;
            operationsProcessor.doPut(putRequest.getStore(), putRequest.getNormalizedNodeMessage());

        } else if (txOperationRequest instanceof DeleteRequest) {

            final DeleteRequest deleteRequest = (DeleteRequest) txOperationRequest;
            operationsProcessor.doDelete(deleteRequest.getStore(), deleteRequest.getPath());

        } else if (txOperationRequest instanceof CancelRequest) {

            operationsProcessor.doCancel(replyTo, futureSender);

        } else if (txOperationRequest instanceof SubmitRequest) {

            operationsProcessor.doSubmit(replyTo, futureSender);
        }
    }

    private void registerSlaveMountPoint(final ActorRef masterReference) {
        if (this.slaveSalManager != null) {
            slaveSalManager.close();
        }
        slaveSalManager = new SlaveSalFacade(id, setup.getDomBroker(), setup.getActorSystem(), actorResponseWaitTime);

        final CheckedFuture<SchemaContext, SchemaResolutionException> remoteSchemaContext =
                getSchemaContext(masterReference);
        final DOMRpcService deviceRpc = getDOMRpcService(masterReference);

        Futures.addCallback(remoteSchemaContext, new FutureCallback<SchemaContext>() {
            @Override
            public void onSuccess(final SchemaContext result) {
                LOG.info("{}: Schema context resolved: {}", id, result.getModules());
                slaveSalManager.registerSlaveMountPoint(result, deviceRpc, masterReference);
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                LOG.error("{}: Failed to register mount point: {}", id, throwable);
            }
        });
    }

    private DOMRpcService getDOMRpcService(final ActorRef masterReference) {
        return new ProxyDOMRpcService(setup.getActorSystem(), masterReference, id, actorResponseWaitTime);
    }

    private CheckedFuture<SchemaContext, SchemaResolutionException> getSchemaContext(final ActorRef masterReference) {

        final RemoteYangTextSourceProvider remoteYangTextSourceProvider =
                new ProxyYangTextSourceProvider(masterReference, getContext(), actorResponseWaitTime);
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider,
                getContext().dispatcher());

        sourceIdentifiers.forEach(sourceId ->
                schemaRegistry.registerSchemaSource(remoteProvider, PotentialSchemaSource.create(sourceId,
                        YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())));

        final SchemaContextFactory schemaContextFactory
                = schemaRepository.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);

        return schemaContextFactory.createSchemaContext(sourceIdentifiers);
    }

}
