/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceTopologyAdapterTest {
    private static final KeyedInstanceIdentifier<Topology, TopologyKey> TEST_TOPOLOGY_ID =
        // FIXME: do not use this constant
        NetconfNodeUtils.DEFAULT_TOPOLOGY_IID;
    private final RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private DataBroker mockBroker;
    @Mock
    private TransactionChain mockChain;
    @Mock
    private WriteTransaction mockTx;
    @Captor
    private ArgumentCaptor<FutureCallback<Empty>> listeners;

    private NetconfDeviceTopologyAdapter adapter;

    @Before
    public void before() {
        doReturn(mockTx).when(mockChain).newWriteOnlyTransaction();
        // FIXME: exact match
        doNothing().when(mockTx).put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class),
            any(Node.class));
        doReturn("test transaction").when(mockTx).getIdentifier();
        doReturn(CommitInfo.emptyFluentFuture()).when(mockTx).commit();

        doReturn(mockChain).when(mockBroker).createMergingTransactionChain();
        doNothing().when(mockChain).addCallback(listeners.capture());
        adapter = new NetconfDeviceTopologyAdapter(mockBroker, TEST_TOPOLOGY_ID, id);
    }

    @Test
    public void replaceChainIfFailed() {
        doNothing().when(mockChain).close();
        doReturn("mockChain").when(mockChain).toString();
        adapter.onFailure(new Exception("chain failed"));
        verify(mockBroker, times(2)).createMergingTransactionChain();
    }

    @Test
    public void testFailedDevice() {
        // FIXME: exact match
        doNothing().when(mockTx).put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class),
            any(NetconfNode.class));

        adapter.setDeviceAsFailed(null);

        verify(mockChain, times(2)).newWriteOnlyTransaction();
        // FIXME: LogicationDataStoreStype, concrete identifier, concreate (or captured/asserted) data
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class),
            any(Node.class));
    }

    @Test
    public void testDeviceUpdate() throws Exception {
        // FIXME: exact match
        doNothing().when(mockTx).put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class),
            any(NetconfNode.class));
        adapter.updateDeviceData(true, NetconfDeviceCapabilities.empty(), new SessionIdType(Uint32.ONE));

        verify(mockChain, times(2)).newWriteOnlyTransaction();
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        // FIXME: exact match
        doNothing().when(mockTx).delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        doNothing().when(mockChain).close();

        final var future = adapter.shutdown();
        verify(mockChain, times(2)).newWriteOnlyTransaction();
        verify(mockTx).delete(LogicalDatastoreType.OPERATIONAL,
            TEST_TOPOLOGY_ID.child(Node.class, new NodeKey(new NodeId(id.name()))));
        verify(mockTx, times(2)).commit();
        verify(mockChain).close();

        assertFalse(future.isDone());

        // Idempotent
        assertSame(future, adapter.shutdown());

        // future completes
        listeners.getValue().onSuccess(Empty.value());
        assertSame(Empty.value(), Futures.getDone(future));
    }

    @Test
    public void testShutdownCompletion() throws Exception {
        // FIXME: exact match
        doNothing().when(mockTx).delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        doNothing().when(mockChain).close();

        final var future = adapter.shutdown();
        verify(mockChain, times(2)).newWriteOnlyTransaction();
        verify(mockTx).delete(LogicalDatastoreType.OPERATIONAL,
            TEST_TOPOLOGY_ID.child(Node.class, new NodeKey(new NodeId(id.name()))));
        verify(mockTx, times(2)).commit();
        verify(mockChain).close();

        assertFalse(future.isDone());

        // Idempotent
        assertSame(future, adapter.shutdown());

        // future completes
        final var cause = new Throwable();
        listeners.getValue().onFailure(cause);
        final var ex = assertThrows(ExecutionException.class, () -> Futures.getDone(future));
        assertSame(cause, ex.getCause());
    }
}
