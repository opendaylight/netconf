/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService.DOMMountPointBuilder;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.Credentials;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Verifies that the master mount point is only registered after {@link CreateInitialMasterActorData} has been
 * processed by the master actor, so that no request on it can reach the master actor while its
 * {@code dataStoreService} is still {@code null}, and that no mount point stays registered for a connection
 * which went away in the meantime.
 */
@ExtendWith(MockitoExtension.class)
class MasterSalFacadeTest {
    private static final ActorSystem SYSTEM = ActorSystem.apply();
    private static final DatabindContext DATABIND = DatabindContext.ofModel(YangParserTestUtils.parseYang("""
        module foo {
          namespace "urn:foo";
          prefix foo;
        }"""));

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(SYSTEM, true);
    }

    private RemoteDeviceId id;
    private DataBroker dataBroker;
    private TestProbe masterActor;
    private DOMMountPointService mountPointService;
    private DOMMountPointBuilder mountPointBuilder;
    private ObjectRegistration<DOMMountPoint> mountPointRegistration;
    private TestableMasterSalFacade facade;
    private NetconfDeviceSchema deviceSchema;
    private NetconfSessionPreferences sessionPreferences;
    private RemoteDeviceServices services;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void beforeEach() {
        id = new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
        masterActor = new TestProbe(SYSTEM);

        dataBroker = mock(DataBroker.class);
        final var txChain = mock(TransactionChain.class);
        final var writeTx = mock(WriteTransaction.class);
        mountPointService = mock(DOMMountPointService.class);
        mountPointBuilder = mock(DOMMountPointBuilder.class);
        mountPointRegistration = mock(ObjectRegistration.class);
        doReturn(txChain).when(dataBroker).createMergingTransactionChain();
        lenient().doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        lenient().doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        lenient().doReturn(mountPointBuilder).when(mountPointService).createMountPoint(any());
        lenient().doReturn(mountPointBuilder).when(mountPointBuilder).addService(any(), any());
        lenient().doReturn(mountPointRegistration).when(mountPointBuilder).register();

        facade = new TestableMasterSalFacade(id, mock(Credentials.class), SYSTEM, masterActor.ref(),
            Duration.ofSeconds(5), mountPointService, dataBroker, false);
        deviceSchema = new NetconfDeviceSchema(DATABIND, NetconfDeviceCapabilities.empty());
        sessionPreferences = new NetconfSessionPreferences(ImmutableMap.of(), ImmutableMap.of(),
            new SessionIdType(Uint32.ONE));
        final var rpcs = mock(Rpcs.Normalized.class);
        lenient().doReturn(mock(DOMRpcService.class)).when(rpcs).domRpcService();
        services = new RemoteDeviceServices(rpcs, null);
    }

    @Test
    void testMountRegistrationWaitsForMasterInit() {
        facade.onDeviceConnected(deviceSchema, sessionPreferences, services, null);

        // The master actor must initialize its data first, nothing may be registered until then.
        masterActor.expectMsgClass(CreateInitialMasterActorData.class);
        verify(mountPointService, after(300).never()).createMountPoint(any());

        masterActor.reply(new MasterActorDataInitialized());

        // Only now should the mount point be registered.
        verify(mountPointBuilder, timeout(1000)).register();
    }

    @Test
    void testDisconnectDuringMasterInitSkipsRegistration() {
        facade.onDeviceConnected(deviceSchema, sessionPreferences, services, null);
        masterActor.expectMsgClass(CreateInitialMasterActorData.class);

        // The device goes away while the master actor is still initializing, before anything was registered.
        facade.onDeviceDisconnected();
        masterActor.reply(new MasterActorDataInitialized());

        // The late initialization must not register a mount point nobody would unregister.
        verify(mountPointService, after(500).never()).createMountPoint(any());
    }

    @Test
    void testDisconnectDuringRegistrationUnregisters() {
        // The device goes away while the mount point is being registered, when there is nothing to unregister yet.
        doAnswer(invocation -> {
            facade.onDeviceDisconnected();
            return mountPointBuilder;
        }).when(mountPointService).createMountPoint(any());

        facade.onDeviceConnected(deviceSchema, sessionPreferences, services, null);
        masterActor.expectMsgClass(CreateInitialMasterActorData.class);
        masterActor.reply(new MasterActorDataInitialized());

        // The registration must notice it has been superseded and undo itself.
        verify(mountPointBuilder, timeout(1000)).register();
        verify(mountPointRegistration, timeout(1000)).close();
    }

    @Test
    void testGetRightAfterRegistration() throws Exception {
        // A real master actor, which is where a request racing CreateInitialMasterActorData used to fail
        final var masterNode = SYSTEM.actorOf(NetconfNodeActor.props(NetconfTopologySetup.builder()
            .setActorSystem(SYSTEM)
            .setIdleTimeout(Duration.ZERO)
            .setDeviceSchemaProvider(mock(DeviceNetconfSchemaProvider.class))
            .setBaseSchemaProvider(AbstractBaseSchemasTest.BASE_SCHEMAS)
            .build(), id, Duration.ofSeconds(5), mountPointService));
        facade = new TestableMasterSalFacade(id, mock(Credentials.class), SYSTEM, masterNode,
            Duration.ofSeconds(5), mountPointService, dataBroker, false);
        doReturn(Futures.immediateFuture(Optional.of(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(SchemaContext.NAME))
            .build()))).when(facade.dataStoreService).get(any(), any(), any());

        // Issue a GET the moment the mount point becomes visible, so it reaches the master actor before anything
        // the facade might still send to it.
        final var strategy = new AtomicReference<DOMServerStrategy>();
        doAnswer(invocation -> {
            if (invocation.getArgument(1) instanceof DOMServerStrategy serverStrategy) {
                strategy.set(serverStrategy);
            }
            return mountPointBuilder;
        }).when(mountPointBuilder).addService(any(), any());
        final var request = new CompletingServerRequest<DataGetResult>();
        doAnswer(invocation -> {
            strategy.get().serverStrategy().dataGET(request);
            return mountPointRegistration;
        }).when(mountPointBuilder).register();

        facade.onDeviceConnected(deviceSchema, sessionPreferences, services, null);

        assertNotNull(request.getResult());
    }

    /**
     * Avoids constructing a real {@link org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceDataBroker} /
     * {@link org.opendaylight.netconf.client.mdsal.spi.AbstractDataStore}, which need a fully-wired NETCONF RPC
     * service; this test only cares about mount point registration.
     */
    private static final class TestableMasterSalFacade extends MasterSalFacade {
        final DataStoreService dataStoreService = mock(DataStoreService.class);

        TestableMasterSalFacade(final RemoteDeviceId id, final Credentials credentials,
                final ActorSystem actorSystem, final ActorRef masterActorRef,
                final Duration actorResponseWaitTime, final DOMMountPointService mountService,
                final DataBroker dataBroker, final boolean lockDatastore) {
            super(id, credentials, actorSystem, masterActorRef, actorResponseWaitTime, mountService, dataBroker,
                lockDatastore);
        }

        @Override
        protected DOMDataBroker newDeviceDataBroker(final DatabindContext databind,
                final NetconfSessionPreferences preferences) {
            final var broker = mock(DOMDataBroker.class);
            lenient().doReturn(mock(DOMDataTreeReadTransaction.class)).when(broker).newReadOnlyTransaction();
            return broker;
        }

        @Override
        protected DataStoreService newDataStoreService(final DatabindContext databind,
                final NetconfSessionPreferences preferences) {
            return dataStoreService;
        }
    }
}
