/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.tx;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
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
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
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

public class WriteOnlyTransactionTest {
    private static final Timeout TIMEOUT = new Timeout(Duration.create(5, "seconds"));
    private static final int TIMEOUT_SEC = 5;
    private static ActorSystem system;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Mock
    private DOMDataBroker deviceDataBroker;
    @Mock
    private DOMDataWriteTransaction writeTx;
    @Mock
    private DOMRpcService domRpcService;
    @Mock
    private DOMMountPointService mountPointService;
    private ActorRef masterRef;
    private ProxyDOMDataBroker slaveDataBroker;
    private List<SourceIdentifier> sourceIdentifiers;
    private NormalizedNode<?, ?> testNode;
    private YangInstanceIdentifier instanceIdentifier;
    private LogicalDatastoreType storeType;

    @Before
    public void setup() throws Exception {
        initMocks(this);

        system = ActorSystem.create();

        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddresses.forString("127.0.0.1"), 9999));

        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);
        doReturn(Duration.apply(0, TimeUnit.SECONDS)).when(setup).getIdleTimeout();
        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                DEFAULT_SCHEMA_REPOSITORY, TIMEOUT, mountPointService);

        masterRef = TestActorRef.create(system, props, "master_read");

        sourceIdentifiers = Lists.newArrayList();

        final DOMDataReadOnlyTransaction readTx = mock(DOMDataReadOnlyTransaction.class);

        doReturn(writeTx).when(deviceDataBroker).newWriteOnlyTransaction();
        doReturn(readTx).when(deviceDataBroker).newReadOnlyTransaction();
        doNothing().when(writeTx).put(storeType, instanceIdentifier, testNode);
        doNothing().when(writeTx).merge(storeType, instanceIdentifier, testNode);
        doNothing().when(writeTx).delete(storeType, instanceIdentifier);

        // Create slave data broker for testing proxy
        slaveDataBroker =
                new ProxyDOMDataBroker(system, remoteDeviceId, masterRef, Timeout.apply(5, TimeUnit.SECONDS));
        initializeDataTest();
        testNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("", "TestQname")))
                .withChild(ImmutableNodes.leafNode(QName.create("", "NodeQname"), "foo")).build();
        instanceIdentifier = YangInstanceIdentifier.EMPTY;
        storeType = LogicalDatastoreType.CONFIGURATION;
    }

    @After
    public void teardown() {
        JavaTestKit.shutdownActorSystem(system, null, true);
        system = null;
    }

    @Test
    public void testPut() throws Exception {
        // Test of invoking put on master through slave proxy
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.put(storeType, instanceIdentifier, testNode);

        verify(writeTx, timeout(2000)).put(storeType, instanceIdentifier, testNode);

        wTx.cancel();
    }

    @Test
    public void testMerge() throws Exception {
        // Test of invoking merge on master through slave proxy
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.merge(storeType, instanceIdentifier, testNode);

        verify(writeTx, timeout(2000)).merge(storeType, instanceIdentifier, testNode);

        wTx.cancel();
    }

    @Test
    public void testDelete() throws Exception {
        final YangInstanceIdentifier instanceIdentifier = YangInstanceIdentifier.EMPTY;
        final LogicalDatastoreType storeType = LogicalDatastoreType.CONFIGURATION;

        // Test of invoking delete on master through slave proxy
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.delete(storeType, instanceIdentifier);
        wTx.cancel();

        verify(writeTx, timeout(2000)).delete(storeType, instanceIdentifier);
    }

    @Test
    public void testSubmit() throws Exception {
        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmit = Futures.immediateCheckedFuture(null);
        doReturn(resultSubmit).when(writeTx).submit();

        // Without Tx
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();

        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmitResponse = wTx.submit();

        final Object result = resultSubmitResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertNull(result);
    }

    @Test
    public void testSubmitWithOperation() throws Exception {
        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmitTx =
                Futures.immediateCheckedFuture(null);
        doReturn(resultSubmitTx).when(writeTx).submit();
        // With Tx
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);

        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmitTxResponse = wTx.submit();

        final Object resultTx = resultSubmitTxResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertNull(resultTx);
    }

    @Test
    public void testSubmitFail() throws Exception {
        final TransactionCommitFailedException throwable = new TransactionCommitFailedException("Fail", null);
        final CheckedFuture<Void, TransactionCommitFailedException> resultThrowable =
                Futures.immediateFailedCheckedFuture(throwable);
        doReturn(resultThrowable).when(writeTx).submit();

        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);
        final CheckedFuture<Void, TransactionCommitFailedException> resultThrowableResponse =
                wTx.submit();
        exception.expect(TransactionCommitFailedException.class);
        resultThrowableResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void testCancel() throws Exception {
        doReturn(true).when(writeTx).cancel();

        // Without Tx
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        final Boolean resultFalseNoTx = wTx.cancel();
        assertEquals(true, resultFalseNoTx);
    }

    @Test
    public void testCancelWithOperation() throws Exception {
        doReturn(true).when(writeTx).cancel();

        // With Tx, readWriteTx test
        final DOMDataWriteTransaction wTx = slaveDataBroker.newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);

        final Boolean resultTrue = wTx.cancel();
        assertEquals(true, resultTrue);

        final Boolean resultFalse = wTx.cancel();
        assertEquals(false, resultFalse);
    }

    private void initializeDataTest() throws Exception {
        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(deviceDataBroker, sourceIdentifiers,
                                domRpcService), TIMEOUT);

        final Object success = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(success instanceof MasterActorDataInitialized);
    }

}
