package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.NormalizedNodeMessage;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.YangTextSchemaSourceRequest;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessage;
import org.opendaylight.netconf.topology.singleton.messages.rpc.InvokeRpcMessageReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.EmptyResultResponse;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewReadTransactionRequest;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionReply;
import org.opendaylight.netconf.topology.singleton.messages.transactions.NewWriteTransactionRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class NetconfNodeMasterActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeMasterActor.class);

    private final SchemaRepository schemaRepository;
    private final Duration writeTxIdleTimeout;

    private RemoteDeviceId id;
    private List<SourceIdentifier> sourceIdentifiers;
    private DOMRpcService deviceRpc;
    private DOMDataBroker deviceDataBroker;
    private NetconfTopologySetup setup;
    //readTxActor can be shared
    private ActorRef readTxActor;

    private NetconfNodeMasterActor(final RemoteDeviceId id,
                                   final SchemaRepository schemaRepository, final NetconfTopologySetup setup) {
        this.id = id;
        this.schemaRepository = schemaRepository;
        this.setup = setup;
        this.writeTxIdleTimeout = setup.getIdleTimeout();
    }

    public static Props props(final RemoteDeviceId id, final NetconfTopologySetup setup,
                              final SchemaRepository schemaRepository) {
        return Props.create(NetconfNodeMasterActor.class, () ->
                new NetconfNodeMasterActor(id, schemaRepository, setup));
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CreateInitialMasterActorData) {
            // Initialization of master actor and creating readTx actor to which master actor will have
            // reference through his whole life cycle

            final CreateInitialMasterActorData masterActorData = (CreateInitialMasterActorData) message;
            sourceIdentifiers = masterActorData.getSourceIndentifiers();
            this.deviceDataBroker = masterActorData.getDeviceDataBroker();
            final DOMDataReadOnlyTransaction tx = deviceDataBroker.newReadOnlyTransaction();
            readTxActor = context().actorOf(ReadTransactionActor.props(tx));
            this.deviceRpc = masterActorData.getDeviceRpc();

            sender().tell(new MasterActorDataInitialized(), self());

            LOG.debug("{}: Master is ready.", id);

        } else if (message instanceof RefreshSetupMasterActorData) {
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
            } catch (final Throwable t) {
                sender().tell(t, self());
            }

        } else if (message instanceof InvokeRpcMessage) { // master

            final InvokeRpcMessage invokeRpcMessage = ((InvokeRpcMessage) message);
            invokeSlaveRpc(invokeRpcMessage.getSchemaPath(), invokeRpcMessage.getNormalizedNodeMessage(), sender());

        }
    }


    private void sendYangTextSchemaSourceProxy(final SourceIdentifier sourceIdentifier, final ActorRef sender) {
        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> yangTextSchemaSource =
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
        });
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
        });
    }

}