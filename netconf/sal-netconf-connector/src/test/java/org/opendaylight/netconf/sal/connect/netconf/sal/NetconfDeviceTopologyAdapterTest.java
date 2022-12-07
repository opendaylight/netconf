/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceTopologyAdapterTest {
    private final RemoteDeviceId id = new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private DataBroker mockBroker;
    @Mock
    private TransactionChain mockChain;
    @Mock
    private WriteTransaction mockTx;
    @Captor
    private ArgumentCaptor<TransactionChainListener> listeners;

    private NetconfDeviceTopologyAdapter adapter;

    @Before
    public void before() {
        doReturn(mockTx).when(mockChain).newWriteOnlyTransaction();
        // FIXME: exact match
        doNothing().when(mockTx).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        doReturn("test transaction").when(mockTx).getIdentifier();
        doReturn(CommitInfo.emptyFluentFuture()).when(mockTx).commit();

        doReturn(mockChain).when(mockBroker).createMergingTransactionChain(listeners.capture());
        adapter = new NetconfDeviceTopologyAdapter(mockBroker, id);
    }

    @Test
    public void replaceChainIfFailed() {
        adapter.onTransactionChainFailed(mockChain, mockTx, new Exception("chain failed"));
        verify(mockBroker, times(2)).createMergingTransactionChain(any());
    }

    @Test
    public void testFailedDevice() {
        adapter.setDeviceAsFailed(null);

        verify(mockChain, times(2)).newWriteOnlyTransaction();
        // FIXME: LogicationDataStoreStype, concrete identifier, concreate (or captured/asserted) data
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class),
            any(Node.class));
    }

    @Test
    public void testDeviceUpdate() throws Exception {
        adapter.updateDeviceData(true, NetconfDeviceCapabilities.empty());

        verify(mockChain, times(2)).newWriteOnlyTransaction();
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
        verify(mockTx, times(1)).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Node.class));
    }

    @Test
    public void testRemoveDeviceConfiguration() throws Exception {
        listeners.getValue().onTransactionChainSuccessful(mockChain);
        adapter.close();

        verify(mockChain, times(2)).newWriteOnlyTransaction();
        verify(mockTx).delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        verify(mockTx, times(2)).commit();
    }
}
