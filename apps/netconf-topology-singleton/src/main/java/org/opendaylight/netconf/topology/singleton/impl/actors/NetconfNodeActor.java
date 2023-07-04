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
import akka.actor.Status.Failure;
import akka.actor.Status.Success;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.controller.cluster.common.actor.AbstractUntypedActor;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMActionService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMRpcService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyYangTextSourceProvider;
import org.opendaylight.netconf.topology.singleton.impl.SlaveSalFacade;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.ContainerNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.NotMasterException;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSlaveActor;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessage;
import org.opendaylight.netconf.topology.singleton.messages.action.InvokeActionMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadWriteTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

public class NetconfNodeActor extends AbstractUntypedActor {
    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;

    private SchemaSourceRegistry schemaRegistry;
    private SchemaRepository schemaRepository;
    private Timeout actorResponseWaitTime;
    private RemoteDeviceId id;
    private NetconfTopologySetup setup;
    private List<SourceIdentifier> sourceIdentifiers;
    private DOMRpcService deviceRpc;
    private DOMActionService deviceAction;
    private SlaveSalFacade slaveSalManager;
    private DOMDataBroker deviceDataBroker;
    private NetconfDataTreeService netconfService;
    //readTxActor can be shared
    private ActorRef readTxActor;
    private List<SchemaSourceRegistration<YangTextSchemaSource>> registeredSchemas;

    public static Props props(final NetconfTopologySetup setup, final RemoteDeviceId id,
            final Timeout actorResponseWaitTime, final DOMMountPointService mountPointService) {
        return Props.create(NetconfNodeActor.class, () ->
                new NetconfNodeActor(setup, id, actorResponseWaitTime, mountPointService));
    }

    protected NetconfNodeActor(final NetconfTopologySetup setup,
                               final RemoteDeviceId id, final Timeout actorResponseWaitTime,
                               final DOMMountPointService mountPointService) {
        this.setup = setup;
        this.id = id;
        schemaRegistry = setup.getSchemaResourcesDTO().getSchemaRegistry();
        schemaRepository = setup.getSchemaResourcesDTO().getSchemaRepository();
        this.actorResponseWaitTime = actorResponseWaitTime;
        writeTxIdleTimeout = setup.getIdleTimeout();
        this.mountPointService = mountPointService;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void handleReceive(final Object message) {
        LOG.debug("{}:  received message {}", id, message);

        if (message instanceof CreateInitialMasterActorData masterActorData) { // master
            sourceIdentifiers = masterActorData.getSourceIndentifiers();
            deviceDataBroker = masterActorData.getDeviceDataBroker();
            netconfService = masterActorData.getNetconfDataTreeService();
            final DOMDataTreeReadTransaction tx = deviceDataBroker.newReadOnlyTransaction();
            readTxActor = context().actorOf(ReadTransactionActor.props(tx));

            final var deviceServices = masterActorData.getDeviceServices();
            deviceRpc = deviceServices.rpcs() instanceof Rpcs.Normalized normalized ? normalized : null;
            deviceAction = deviceServices.actions() instanceof Actions.Normalized normalized ? normalized : null;

            sender().tell(new MasterActorDataInitialized(), self());
            LOG.debug("{}: Master is ready.", id);
        } else if (message instanceof RefreshSetupMasterActorData) {
            setup = ((RefreshSetupMasterActorData) message).getNetconfTopologyDeviceSetup();
            id = ((RefreshSetupMasterActorData) message).getRemoteDeviceId();
            sender().tell(new MasterActorDataInitialized(), self());
        } else if (message instanceof AskForMasterMountPoint askForMasterMountPoint) { // master
            // only master contains reference to deviceDataBroker
            if (deviceDataBroker != null) {
                LOG.debug("{}: Sending RegisterMountPoint reply to {}", id, askForMasterMountPoint.getSlaveActorRef());
                askForMasterMountPoint.getSlaveActorRef().tell(new RegisterMountPoint(sourceIdentifiers, self()),
                    sender());
            } else {
                LOG.warn("{}: Received {} but we don't appear to be the master", id, askForMasterMountPoint);
                sender().tell(new Failure(new NotMasterException(self())), self());
            }
        } else if (message instanceof YangTextSchemaSourceRequest yangTextSchemaSourceRequest) { // master
            sendYangTextSchemaSourceProxy(yangTextSchemaSourceRequest.getSourceIdentifier(), sender());
        } else if (message instanceof NewReadTransactionRequest) { // master
            sender().tell(new Success(readTxActor), self());
        } else if (message instanceof NewWriteTransactionRequest) { // master
            try {
                final DOMDataTreeWriteTransaction tx = deviceDataBroker.newWriteOnlyTransaction();
                final ActorRef txActor = context().actorOf(WriteTransactionActor.props(tx, writeTxIdleTimeout));
                sender().tell(new Success(txActor), self());
            } catch (final Exception t) {
                sender().tell(new Failure(t), self());
            }
        } else if (message instanceof NewReadWriteTransactionRequest) {
            try {
                final DOMDataTreeReadWriteTransaction tx = deviceDataBroker.newReadWriteTransaction();
                final ActorRef txActor = context().actorOf(ReadWriteTransactionActor.props(tx, writeTxIdleTimeout));
                sender().tell(new Success(txActor), self());
            } catch (final Exception t) {
                sender().tell(new Failure(t), self());
            }
        } else if (message instanceof InvokeRpcMessage invokeRpcMessage) { // master
            invokeSlaveRpc(invokeRpcMessage.getSchemaPath().lastNodeIdentifier(),
                invokeRpcMessage.getNormalizedNodeMessage(), sender());
        } else if (message instanceof InvokeActionMessage invokeActionMessage) { // master
            LOG.info("InvokeActionMessage Details : {}", invokeActionMessage.toString());
            invokeSlaveAction(invokeActionMessage.getSchemaPath(), invokeActionMessage.getContainerNodeMessage(),
                invokeActionMessage.getDOMDataTreeIdentifier(), sender());
        } else if (message instanceof RegisterMountPoint registerMountPoint) { //slaves
            sourceIdentifiers = registerMountPoint.getSourceIndentifiers();
            registerSlaveMountPoint(registerMountPoint.getMasterActorRef());
            sender().tell(new Success(null), self());
        } else if (message instanceof UnregisterSlaveMountPoint) { //slaves
            unregisterSlaveMountPoint();
        } else if (message instanceof RefreshSlaveActor refreshSlave) { //slave
            actorResponseWaitTime = refreshSlave.getActorResponseWaitTime();
            id = refreshSlave.getId();
            schemaRegistry = refreshSlave.getSchemaRegistry();
            setup = refreshSlave.getSetup();
            schemaRepository = refreshSlave.getSchemaRepository();
        } else if (message instanceof NetconfDataTreeServiceRequest) {
            ActorRef netconfActor = context()
                .actorOf(NetconfDataTreeServiceActor.props(netconfService, writeTxIdleTimeout));
            sender().tell(new Success(netconfActor), self());
        }
    }

    @Override
    public void postStop() throws Exception {
        try {
            super.postStop();
        } finally {
            unregisterSlaveMountPoint();
        }
    }

    private void unregisterSlaveMountPoint() {
        if (slaveSalManager != null) {
            slaveSalManager.close();
            slaveSalManager = null;
        }

        closeSchemaSourceRegistrations();
    }

    private void sendYangTextSchemaSourceProxy(final SourceIdentifier sourceIdentifier, final ActorRef sender) {
        final ListenableFuture<YangTextSchemaSource> schemaSourceFuture =
                schemaRepository.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        Futures.addCallback(schemaSourceFuture, new FutureCallback<YangTextSchemaSource>() {
            @Override
            public void onSuccess(final YangTextSchemaSource yangTextSchemaSource) {
                try {
                    LOG.debug("{}: getSchemaSource for {} succeeded", id, sourceIdentifier);
                    sender.tell(new YangTextSchemaSourceSerializationProxy(yangTextSchemaSource), getSelf());
                } catch (IOException e) {
                    sender.tell(new Failure(e), getSelf());
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.debug("{}: getSchemaSource for {} failed", id, sourceIdentifier, throwable);
                sender.tell(new Failure(throwable), getSelf());
            }
        }, MoreExecutors.directExecutor());
    }

    private void invokeSlaveRpc(final QName qname, final NormalizedNodeMessage normalizedNodeMessage,
                                final ActorRef recipient) {
        LOG.debug("{}: invokeSlaveRpc for {}, input: {} on rpc service {}", id, qname, normalizedNodeMessage,
                deviceRpc);

        final ListenableFuture<? extends DOMRpcResult> rpcResult = deviceRpc.invokeRpc(qname,
                normalizedNodeMessage != null ? (ContainerNode) normalizedNodeMessage.getNode() : null);

        Futures.addCallback(rpcResult, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult domRpcResult) {
                LOG.debug("{}: invokeSlaveRpc for {}, domRpcResult: {}", id, qname, domRpcResult);

                if (domRpcResult == null) {
                    recipient.tell(new EmptyResultResponse(), getSender());
                    return;
                }
                NormalizedNodeMessage nodeMessageReply = null;
                if (domRpcResult.value() != null) {
                    nodeMessageReply = new NormalizedNodeMessage(YangInstanceIdentifier.of(), domRpcResult.value());
                }
                recipient.tell(new InvokeRpcMessageReply(nodeMessageReply, domRpcResult.errors()), getSelf());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                recipient.tell(new Failure(throwable), getSelf());
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Invoking Action on Slave Node in Odl Cluster Environment.
     *
     * @param schemaPath {@link Absolute}
     * @param containerNodeMessage {@link ContainerNodeMessage}
     * @param domDataTreeIdentifier {@link DOMDataTreeIdentifier}
     * @param recipient {@link ActorRef}
     */
    private void invokeSlaveAction(final Absolute schemaPath, final ContainerNodeMessage containerNodeMessage,
            final DOMDataTreeIdentifier domDataTreeIdentifier, final ActorRef recipient) {
        LOG.info("{}: invokeSlaveAction for {}, input: {}, identifier: {} on action service {}", id, schemaPath,
            containerNodeMessage, domDataTreeIdentifier, deviceAction);

        final ListenableFuture<? extends DOMActionResult> actionResult = deviceAction.invokeAction(schemaPath,
            domDataTreeIdentifier, containerNodeMessage != null ? containerNodeMessage.getNode() : null);

        Futures.addCallback(actionResult, new FutureCallback<DOMActionResult>() {

            @Override
            public void onSuccess(final DOMActionResult domActionResult) {
                LOG.debug("{}: invokeSlaveAction for {}, domActionResult: {}", id, schemaPath, domActionResult);
                if (domActionResult == null) {
                    recipient.tell(new EmptyResultResponse(), getSender());
                    return;
                }

                //Check DomActionResult containing Ok onSuccess pass empty nodeMessageReply
                ContainerNodeMessage nodeMessageReply = domActionResult.getOutput().map(ContainerNodeMessage::new)
                        .orElse(null);
                recipient.tell(new InvokeActionMessageReply(nodeMessageReply, domActionResult.getErrors()), getSelf());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                recipient.tell(new Failure(throwable), getSelf());
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerSlaveMountPoint(final ActorRef masterReference) {
        unregisterSlaveMountPoint();

        slaveSalManager = new SlaveSalFacade(id, setup.getActorSystem(), actorResponseWaitTime, mountPointService);

        resolveSchemaContext(createSchemaContextFactory(masterReference), slaveSalManager, masterReference, 1);
    }

    private EffectiveModelContextFactory createSchemaContextFactory(final ActorRef masterReference) {
        final RemoteYangTextSourceProvider remoteYangTextSourceProvider =
                new ProxyYangTextSourceProvider(masterReference, getContext().dispatcher(), actorResponseWaitTime);
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider,
                getContext().dispatcher());

        registeredSchemas = sourceIdentifiers.stream()
                .map(sourceId ->
                        schemaRegistry.registerSchemaSource(remoteProvider, PotentialSchemaSource.create(sourceId,
                                YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())))
                .collect(Collectors.toList());

        return schemaRepository.createEffectiveModelContextFactory();
    }

    private void resolveSchemaContext(final EffectiveModelContextFactory schemaContextFactory,
            final SlaveSalFacade localSlaveSalManager, final ActorRef masterReference, final int tries) {
        final ListenableFuture<EffectiveModelContext> schemaContextFuture =
                schemaContextFactory.createEffectiveModelContext(sourceIdentifiers);
        Futures.addCallback(schemaContextFuture, new FutureCallback<EffectiveModelContext>() {
            @Override
            public void onSuccess(final EffectiveModelContext result) {
                executeInSelf(() -> {
                    // Make sure the slaveSalManager instance hasn't changed since we initiated the schema context
                    // resolution.
                    if (slaveSalManager == localSlaveSalManager) {
                        LOG.info("{}: Schema context resolved: {} - registering slave mount point",
                                id, result.getModules());
                        final var actorSystem = setup.getActorSystem();
                        slaveSalManager.registerSlaveMountPoint(result, masterReference, new RemoteDeviceServices(
                            new ProxyDOMRpcService(actorSystem, masterReference, id, actorResponseWaitTime),
                            new ProxyDOMActionService(actorSystem, masterReference, id, actorResponseWaitTime)));
                    }
                });
            }

            @Override
            public void onFailure(final Throwable throwable) {
                executeInSelf(() -> {
                    if (slaveSalManager == localSlaveSalManager) {
                        final Throwable cause = Throwables.getRootCause(throwable);
                        if (cause instanceof AskTimeoutException) {
                            if (tries <= 5 || tries % 10 == 0) {
                                LOG.warn("{}: Failed to resolve schema context - retrying...", id, throwable);
                            }

                            resolveSchemaContext(schemaContextFactory, localSlaveSalManager,
                                    masterReference, tries + 1);
                        } else {
                            LOG.error("{}: Failed to resolve schema context - unable to register slave mount point",
                                    id, throwable);
                            closeSchemaSourceRegistrations();
                        }
                    }
                });
            }
        }, MoreExecutors.directExecutor());
    }

    private void closeSchemaSourceRegistrations() {
        if (registeredSchemas != null) {
            registeredSchemas.forEach(SchemaSourceRegistration::close);
            registeredSchemas = null;
        }
    }
}
