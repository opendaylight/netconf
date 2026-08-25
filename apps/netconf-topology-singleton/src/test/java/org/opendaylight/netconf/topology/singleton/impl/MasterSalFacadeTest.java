/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableMap;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.JavaDurationConverters;
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
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService.DOMMountPointBuilder;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.Credentials;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Verifies that master mount point registration and thus resolving the mount's
 * {@code NetconfDataTreeServiceActor} only happens after {@link CreateInitialMasterActorData} has actually
 * been processed by the master actor, closing a race where {@code NetconfDataTreeServiceRequest} could otherwise
 * reach the master actor while its {@code dataStoreService} field is still {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class MasterSalFacadeTest {
    private static final ActorSystem SYSTEM = ActorSystem.apply();

    @AfterAll
    static void staticTearDown() {
        TestKit.shutdownActorSystem(SYSTEM, true);
    }

    private TestProbe masterActor;
    private DOMMountPointBuilder mountPointBuilder;
    private TestableMasterSalFacade facade;
    private NetconfDeviceSchema deviceSchema;
    private NetconfSessionPreferences sessionPreferences;
    private RemoteDeviceServices services;

    @BeforeEach
    void beforeEach() {
        final var id = new RemoteDeviceId("dev1", InetSocketAddress.createUnresolved("localhost", 17830));
        masterActor = new TestProbe(SYSTEM);

        final var dataBroker = mock(DataBroker.class);
        final var txChain = mock(TransactionChain.class);
        final var writeTx = mock(WriteTransaction.class);
        final var mountPointService = mock(DOMMountPointService.class);
        mountPointBuilder = mock(DOMMountPointBuilder.class);
        doReturn(txChain).when(dataBroker).createMergingTransactionChain();
        doReturn(writeTx).when(txChain).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        lenient().doReturn(mountPointBuilder).when(mountPointService).createMountPoint(any());
        lenient().doReturn(mountPointBuilder).when(mountPointBuilder).addService(any(), any());
        lenient().doReturn(mock(ObjectRegistration.class)).when(mountPointBuilder).register();

        facade = new TestableMasterSalFacade(id, mock(Credentials.class), SYSTEM, masterActor.ref(),
            Duration.ofSeconds(5), mountPointService, dataBroker, false);
        final var modelContext = mock(EffectiveModelContext.class);
        final var databind = mock(DatabindContext.class);
        doReturn(modelContext).when(databind).modelContext();
        deviceSchema = new NetconfDeviceSchema(databind, NetconfDeviceCapabilities.empty());
        sessionPreferences = new NetconfSessionPreferences(ImmutableMap.of(), ImmutableMap.of(),
            new SessionIdType(Uint32.ONE));
        final var rpcs = mock(Rpcs.Normalized.class);
        lenient().doReturn(mock(DOMRpcService.class)).when(rpcs).domRpcService();
        services = new RemoteDeviceServices(rpcs, null);
    }

    @Test
    void testMountRegistrationWaitsForMasterInit() {
        connectDevice();

        // The master actor must initialize its data first.
        masterActor.expectMsgClass(CreateInitialMasterActorData.class);

        // Until data initialization, nothing about the mount, no ask for the mount's NetconfDataTreeServiceActor
        // may be sent to it. Otherwise, that ask could land while master actor's dataStoreService field is still null,
        // permanently sticking the mount with a null-backed actor.
        masterActor.expectNoMessage(JavaDurationConverters.asFiniteDuration(Duration.ofMillis(300)));

        masterActor.reply(new MasterActorDataInitialized());

        // Only now should the mount registration proceed and ask for the data-tree actor.
        masterActor.expectMsgClass(NetconfDataTreeServiceRequest.class);
    }

    @Test
    void testDisconnectDuringMasterInitSkipsRegistration() {
        connectDevice();
        masterActor.expectMsgClass(CreateInitialMasterActorData.class);

        // The device goes away while the master actor is still initializing, before anything was registered.
        facade.onDeviceDisconnected();
        masterActor.reply(new MasterActorDataInitialized());

        // The late initialization must neither register a mount nobody would unregister, nor ask for its
        // data-tree actor.
        masterActor.expectNoMessage(JavaDurationConverters.asFiniteDuration(Duration.ofMillis(500)));
        verify(mountPointBuilder, never()).register();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void connectDevice() {
        // mount.onDeviceConnected() builds a full RESTCONF server strategy that needs a real,
        // non-trivial EffectiveModelContext to fully succeed  unrelated to what these tests verify.
        // Whether it throws while doing so (from the incomplete model context mocked above) is not
        // their concern; what matters is what was already sent to masterActorRef by that point.
        try {
            facade.onDeviceConnected(deviceSchema, sessionPreferences, services, null);
        } catch (RuntimeException e) {
            // ignored, see above
        }
    }

    /**
     * Avoids constructing a real {@link org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceDataBroker} /
     * {@link org.opendaylight.netconf.client.mdsal.spi.AbstractDataStore}, which need a fully-wired NETCONF RPC
     * service; this test only cares about message ordering towards the master actor.
     */
    private static final class TestableMasterSalFacade extends MasterSalFacade {
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
            return mock(DOMDataBroker.class);
        }

        @Override
        protected DataStoreService newDataStoreService(final DatabindContext databind,
                final NetconfSessionPreferences preferences) {
            return mock(DataStoreService.class);
        }
    }
}
