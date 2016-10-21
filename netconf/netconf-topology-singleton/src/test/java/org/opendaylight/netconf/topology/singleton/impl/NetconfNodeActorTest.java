/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.cluster.schema.provider.impl.YangTextSchemaSourceSerializationProxy;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.RegisterMountPoint;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class NetconfNodeActorTest {

    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));
    private static ActorSystem system;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private ActorRef masterRef;
    private RemoteDeviceId remoteDeviceId;

    @Before
    public void setup() throws UnknownHostException {

        remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9999));

        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);

        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                DEFAULT_SCHEMA_REPOSITORY);

        system = ActorSystem.create();

        masterRef = TestActorRef.create(system, props, "master_messages");
    }

    @After
    public void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testInitDataMessages() throws Exception {

        final DOMDataBroker domDataBroker = mock(DOMDataBroker.class);
        final List<SourceIdentifier> sourceIdentifiers = Lists.newArrayList();

        /* Test init master data */

        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(domDataBroker, sourceIdentifiers),
                        TIMEOUT);

        final Object success = Await.result(initialDataToActor, TIMEOUT.duration());
        assertTrue(success instanceof MasterActorDataInitialized);


        /* Test refresh master data */

        final RemoteDeviceId remoteDeviceId2 = new RemoteDeviceId("netconf-topology2",
                new InetSocketAddress(InetAddress.getByName("127.0.0.2"), 9999));

        final NetconfTopologySetup setup2 = mock(NetconfTopologySetup.class);

        final Future<Object> refreshDataToActor =
                Patterns.ask(masterRef, new RefreshSetupMasterActorData(setup2, remoteDeviceId2),
                        TIMEOUT);

        final Object success2 = Await.result(refreshDataToActor, TIMEOUT.duration());
        assertTrue(success2 instanceof MasterActorDataInitialized);

    }

    @Test
    public void testRegisterMountPointMessage() throws Exception {

        final DOMDataBroker domDataBroker = mock(DOMDataBroker.class);
        final List<SourceIdentifier> sourceIdentifiers =
                Lists.newArrayList(SourceIdentifier.create("testID", Optional.absent()));

        // init master data

        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(domDataBroker, sourceIdentifiers),
                        TIMEOUT);

        final Object successInit = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(successInit instanceof MasterActorDataInitialized);

        // test if slave get right identifiers from master

        final Future<Object> registerMountPointFuture =
                Patterns.ask(masterRef, new AskForMasterMountPoint(),
                        TIMEOUT);

        final RegisterMountPoint success =
                (RegisterMountPoint) Await.result(registerMountPointFuture, TIMEOUT.duration());

        assertEquals(sourceIdentifiers, success.getSourceIndentifiers());

    }

    @Test
    public void testYangTextSchemaSourceRequestMessage() throws Exception {
        final SchemaRepository schemaRepository = mock(SchemaRepository.class);
        final SourceIdentifier sourceIdentifier = SourceIdentifier.create("testID", Optional.absent());
        final Props props = NetconfNodeActor.props(mock(NetconfTopologySetup.class), remoteDeviceId,
                DEFAULT_SCHEMA_REPOSITORY, schemaRepository);

        final ActorRef actorRefSchemaRepo = TestActorRef.create(system, props, "master_mocked_schema_repository");
        final ActorContext actorContext = mock(ActorContext.class);
        doReturn(system.dispatcher()).when(actorContext).dispatcher();

        final ProxyYangTextSourceProvider proxyYang =
                new ProxyYangTextSourceProvider(actorRefSchemaRepo, actorContext);
        // test if asking for source is resolved and sended back

        final YangTextSchemaSource yangTextSchemaSource = new YangTextSchemaSource(sourceIdentifier) {
            @Override
            protected MoreObjects.ToStringHelper addToStringAttributes(MoreObjects.ToStringHelper toStringHelper) {
                return null;
            }

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream("YANG".getBytes());
            }
        };


        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> result =
                Futures.immediateCheckedFuture(yangTextSchemaSource);

        doReturn(result).when(schemaRepository).getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        final Future<YangTextSchemaSourceSerializationProxy> resolvedSchema =
                proxyYang.getYangTextSchemaSource(sourceIdentifier);

        final YangTextSchemaSourceSerializationProxy success = Await.result(resolvedSchema, TIMEOUT.duration());

        assertEquals(sourceIdentifier, success.getRepresentation().getIdentifier());
        assertEquals("YANG", convertStreamToString(success.getRepresentation().openStream()));


        // test if asking for source is missing
        exception.expect(MissingSchemaSourceException.class);

        final SchemaSourceException schemaSourceException =
                new MissingSchemaSourceException("Fail", sourceIdentifier);

        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> resultFail =
                Futures.immediateFailedCheckedFuture(schemaSourceException);

        doReturn(resultFail).when(schemaRepository).getSchemaSource(sourceIdentifier, YangTextSchemaSource.class);

        final Future<YangTextSchemaSourceSerializationProxy> failedSchema =
                proxyYang.getYangTextSchemaSource(sourceIdentifier);

        Await.result(failedSchema, TIMEOUT.duration());

    }

    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

}
