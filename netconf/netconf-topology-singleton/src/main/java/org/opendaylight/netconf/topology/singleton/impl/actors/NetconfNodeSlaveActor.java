package org.opendaylight.netconf.topology.singleton.impl.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.util.Timeout;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.controller.cluster.schema.provider.RemoteYangTextSourceProvider;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMRpcService;
import org.opendaylight.netconf.topology.singleton.impl.ProxyYangTextSourceProvider;
import org.opendaylight.netconf.topology.singleton.impl.SlaveSalFacade;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfNodeSlaveActor extends UntypedActor {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeSlaveActor.class);

    private SlaveSalFacade slaveSalManager;
    private List<SourceIdentifier> sourceIdentifiers;
    private final Timeout actorResponseWaitTime;
    private final RemoteDeviceId id;
    private final NetconfTopologySetup setup;
    private final SchemaRepository schemaRepository;
    private final SchemaSourceRegistry schemaRegistry;

    public static Props props(final NetconfTopologySetup setup,
                              final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                              final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime) {
        return Props.create(NetconfNodeSlaveActor.class, () ->
                new NetconfNodeSlaveActor(setup, id, schemaRegistry, schemaRepository, actorResponseWaitTime));
    }

    private NetconfNodeSlaveActor(final NetconfTopologySetup setup,
                                  final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                                  final SchemaRepository schemaRepository, final Timeout actorResponseWaitTime) {
        this.setup = setup;
        this.id = id;
        this.schemaRepository = schemaRepository;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof RegisterMountPoint) { //slaves

            sourceIdentifiers = ((RegisterMountPoint) message).getSourceIndentifiers();
            registerSlaveMountPoint(getSender());

        } else if (message instanceof UnregisterSlaveMountPoint) { //slaves
            if (slaveSalManager != null) {
                slaveSalManager.close();
                slaveSalManager = null;
            }

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

    private DOMRpcService getDOMRpcService(final ActorRef masterReference) {
        return new ProxyDOMRpcService(setup.getActorSystem(), masterReference, id, actorResponseWaitTime);
    }
}
