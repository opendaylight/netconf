/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.ProxyDOMDataBroker;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.singleton.messages.MasterActorDataInitialized;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class ReadOnlyTransactionTest {
    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));
    private static final int TIMEOUT_SEC = 5;
    private static ActorSystem system;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private ActorRef masterRef;
    private ProxyDOMDataBroker slaveDataBroker;
    private List<SourceIdentifier> sourceIdentifiers;
    private YangInstanceIdentifier instanceIdentifier;
    private LogicalDatastoreType storeType;
    @Mock
    private DOMDataBroker deviceDataBroker;
    @Mock
    private DOMDataReadOnlyTransaction readTx;
    @Mock
    private DOMRpcService domRpcService;
    @Mock
    private DOMMountPointService mountPointService;


    @Before
    public void setup() throws Exception {
        initMocks(this);

        system = ActorSystem.create();

        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);
        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                DEFAULT_SCHEMA_REPOSITORY, TIMEOUT, mountPointService);

        masterRef = TestActorRef.create(system, props, "master_read");

        sourceIdentifiers = Lists.newArrayList();

        //device read tx
        doReturn(readTx).when(deviceDataBroker).newReadOnlyTransaction();

        // Create slave data broker for testing proxy
        slaveDataBroker =
                new ProxyDOMDataBroker(system, remoteDeviceId, masterRef, Timeout.apply(5, TimeUnit.SECONDS));
        initializeDataTest();
        instanceIdentifier = YangInstanceIdentifier.EMPTY;
        storeType = LogicalDatastoreType.CONFIGURATION;
    }

    @After
    public void teardown() {
        JavaTestKit.shutdownActorSystem(system, null, true);
        system = null;
    }

    @Test
    public void testRead() throws Exception {
        // Message: NormalizedNodeMessage
        final NormalizedNode<?, ?> outputNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "TestQname")))
                .withChild(ImmutableNodes.leafNode(QName.create("", "NodeQname"), "foo")).build();
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultNormalizedNodeMessage =
                Futures.immediateCheckedFuture(Optional.of(outputNode));
        doReturn(resultNormalizedNodeMessage).when(readTx).read(storeType, instanceIdentifier);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultNodeMessageResponse =
                slaveDataBroker.newReadOnlyTransaction().read(storeType, instanceIdentifier);

        final Optional<NormalizedNode<?, ?>> resultNodeMessage =
                resultNodeMessageResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertTrue(resultNodeMessage.isPresent());
        assertEquals(resultNodeMessage.get(), outputNode);
    }

    @Test
    public void testReadEmpty() throws Exception {
        // Message: EmptyReadResponse
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultEmpty =
                Futures.immediateCheckedFuture(Optional.absent());
        doReturn(resultEmpty).when(readTx).read(storeType, instanceIdentifier);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultEmptyResponse =
                slaveDataBroker.newReadOnlyTransaction().read(storeType,
                        instanceIdentifier);

        final Optional<NormalizedNode<?, ?>> resultEmptyMessage =
                resultEmptyResponse.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertEquals(resultEmptyMessage, Optional.absent());
    }

    @Test
    public void testReadFail() throws Exception {
        // Message: Throwable
        final ReadFailedException readFailedException = new ReadFailedException("Fail", null);
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultThrowable =
                Futures.immediateFailedCheckedFuture(readFailedException);

        doReturn(resultThrowable).when(readTx).read(storeType, instanceIdentifier);

        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> resultThrowableResponse =
                slaveDataBroker.newReadOnlyTransaction().read(storeType, instanceIdentifier);

        exception.expect(ReadFailedException.class);
        resultThrowableResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void testExist() throws Exception {
        // Message: True
        final CheckedFuture<Boolean, ReadFailedException> resultTrue =
                Futures.immediateCheckedFuture(true);
        doReturn(resultTrue).when(readTx).exists(storeType, instanceIdentifier);

        final CheckedFuture<Boolean, ReadFailedException> trueResponse =
                slaveDataBroker.newReadOnlyTransaction().exists(storeType, instanceIdentifier);

        final Boolean trueMessage = trueResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertEquals(true, trueMessage);
    }

    @Test
    public void testExistsNull() throws Exception {
        // Message: False, result null
        final CheckedFuture<Boolean, ReadFailedException> resultNull = Futures.immediateCheckedFuture(null);
        doReturn(resultNull).when(readTx).exists(storeType, instanceIdentifier);

        final CheckedFuture<Boolean, ReadFailedException> nullResponse =
                slaveDataBroker.newReadOnlyTransaction().exists(storeType,
                        instanceIdentifier);

        final Boolean nullFalseMessage = nullResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertEquals(false, nullFalseMessage);
    }

    @Test
    public void testExistsFalse() throws Exception {
        // Message: False
        final CheckedFuture<Boolean, ReadFailedException> resultFalse = Futures.immediateCheckedFuture(false);
        doReturn(resultFalse).when(readTx).exists(storeType, instanceIdentifier);

        final CheckedFuture<Boolean, ReadFailedException> falseResponse =
                slaveDataBroker.newReadOnlyTransaction().exists(storeType,
                        instanceIdentifier);

        final Boolean falseMessage = falseResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertEquals(false, falseMessage);
    }

    @Test
    public void testExistsFail() throws Exception {
        // Message: Throwable
        final ReadFailedException readFailedException = new ReadFailedException("Fail", null);
        final CheckedFuture<Boolean, ReadFailedException> resultThrowable =
                Futures.immediateFailedCheckedFuture(readFailedException);
        doReturn(resultThrowable).when(readTx).exists(storeType, instanceIdentifier);

        final CheckedFuture<Boolean, ReadFailedException> resultThrowableResponse =
                slaveDataBroker.newReadOnlyTransaction().exists(storeType, instanceIdentifier);

        exception.expect(ReadFailedException.class);
        resultThrowableResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private void initializeDataTest() throws Exception {
        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(deviceDataBroker, sourceIdentifiers,
                                domRpcService), TIMEOUT);

        final Object success = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(success instanceof MasterActorDataInitialized);
    }
}
