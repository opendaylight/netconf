

package org.opendaylight.netconf.topology.singleton.impl.tx;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.NetconfDOMDataBroker;
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

    private ActorRef masterRef;
    private NetconfDOMDataBroker slaveDataBroker;
    private DOMDataBroker masterDataBroker;
    private List<SourceIdentifier> sourceIdentifiers;

    @Mock
    private DOMDataWriteTransaction writeTx;

    @Before
    public void setup() throws UnknownHostException {
        initMocks(this);

        system = ActorSystem.create();

        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("netconf-topology",
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 9999));

        final NetconfTopologySetup setup = mock(NetconfTopologySetup.class);
        final Props props = NetconfNodeActor.props(setup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                DEFAULT_SCHEMA_REPOSITORY);

        masterRef = TestActorRef.create(system, props, "master_read");

        sourceIdentifiers = Lists.newArrayList();

        // Create master data broker

        final DOMDataBroker delegateDataBroker = mock(DOMDataBroker.class);
        writeTx = mock(DOMDataWriteTransaction.class);
        final DOMDataReadOnlyTransaction readTx = mock(DOMDataReadOnlyTransaction.class);

        doReturn(writeTx).when(delegateDataBroker).newWriteOnlyTransaction();
        doReturn(readTx).when(delegateDataBroker).newReadOnlyTransaction();

        final NetconfDOMTransaction masterDOMTransactions =
                new NetconfMasterDOMTransaction(delegateDataBroker);

        masterDataBroker =
                new NetconfDOMDataBroker(system, remoteDeviceId, masterDOMTransactions);

        // Create slave data broker for testing proxy

        final NetconfDOMTransaction proxyDOMTransactions =
                new NetconfProxyDOMTransaction(system, masterRef);

        slaveDataBroker = new NetconfDOMDataBroker(system, remoteDeviceId, proxyDOMTransactions);


    }

    @After
    public void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testPutMergeDeleteCalls() throws Exception {

        /* Initialize data on master */

        initializeDataTest();

        final YangInstanceIdentifier instanceIdentifier = YangInstanceIdentifier.EMPTY;
        final LogicalDatastoreType storeType = LogicalDatastoreType.CONFIGURATION;
        final NormalizedNode<?, ?> testNode = ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("TestQname")))
                .withChild(ImmutableNodes.leafNode(QName.create("NodeQname"), "foo")).build();

        // Test of invoking put on master through slave proxy

        doNothing().when(writeTx).put(storeType, instanceIdentifier, testNode);
        slaveDataBroker.newWriteOnlyTransaction().put(storeType, instanceIdentifier, testNode);

        verify(writeTx, times(1)).put(storeType, instanceIdentifier, testNode);

        // Test of invoking merge on master through slave proxy

        doNothing().when(writeTx).merge(storeType, instanceIdentifier, testNode);
        slaveDataBroker.newWriteOnlyTransaction().merge(storeType, instanceIdentifier, testNode);

        verify(writeTx, times(1)).merge(storeType, instanceIdentifier, testNode);

        // Test of invoking delete on master through slave proxy

        doNothing().when(writeTx).delete(storeType, instanceIdentifier);
        slaveDataBroker.newWriteOnlyTransaction().delete(storeType, instanceIdentifier);

        verify(writeTx, times(1)).delete(storeType, instanceIdentifier);

    }

    @Test
    public void testSubmit() throws Exception {

        /* Initialize data on master */

        initializeDataTest();

        // Without Tx

        final CheckedFuture<Void,TransactionCommitFailedException> resultSubmit = Futures.immediateCheckedFuture(null);
        doReturn(resultSubmit).when(writeTx).submit();

        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmitResponse =
                slaveDataBroker.newWriteOnlyTransaction().submit();

        final Object result= resultSubmitResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertNull(result);

        // With Tx

        doNothing().when(writeTx).delete(any(), any());
        slaveDataBroker.newWriteOnlyTransaction().delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);

        final CheckedFuture<Void,TransactionCommitFailedException> resultSubmitTx = Futures.immediateCheckedFuture(null);
        doReturn(resultSubmitTx).when(writeTx).submit();

        final CheckedFuture<Void, TransactionCommitFailedException> resultSubmitTxResponse =
                slaveDataBroker.newWriteOnlyTransaction().submit();

        final Object resultTx = resultSubmitTxResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertNull(resultTx);

        slaveDataBroker.newWriteOnlyTransaction().delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);

        final TransactionCommitFailedException throwable = new TransactionCommitFailedException("Fail", null);
        final CheckedFuture<Void,TransactionCommitFailedException> resultThrowable =
                Futures.immediateFailedCheckedFuture(throwable);

        doReturn(resultThrowable).when(writeTx).submit();

        final CheckedFuture<Void, TransactionCommitFailedException> resultThrowableResponse =
                slaveDataBroker.newWriteOnlyTransaction().submit();

        exception.expect(TransactionCommitFailedException.class);
        resultThrowableResponse.checkedGet(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void testCancel() throws Exception {

        /* Initialize data on master */

        initializeDataTest();

        // Without Tx

        final Boolean resultFalseNoTx = slaveDataBroker.newWriteOnlyTransaction().cancel();
        assertEquals(false, resultFalseNoTx);

        // With Tx, readWriteTx test

        doNothing().when(writeTx).delete(any(), any());
        slaveDataBroker.newReadWriteTransaction().delete(LogicalDatastoreType.CONFIGURATION,
                YangInstanceIdentifier.EMPTY);

        doReturn(true).when(writeTx).cancel();

        final Boolean resultTrue = slaveDataBroker.newWriteOnlyTransaction().cancel();
        assertEquals(true, resultTrue);

        doReturn(false).when(writeTx).cancel();

        final Boolean resultFalse = slaveDataBroker.newWriteOnlyTransaction().cancel();
        assertEquals(false, resultFalse);

    }

    private void initializeDataTest() throws Exception {
        final Future<Object> initialDataToActor =
                Patterns.ask(masterRef, new CreateInitialMasterActorData(masterDataBroker, sourceIdentifiers),
                        TIMEOUT);

        final Object success = Await.result(initialDataToActor, TIMEOUT.duration());

        assertTrue(success instanceof MasterActorDataInitialized);
    }

}
