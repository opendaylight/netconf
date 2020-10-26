/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceSalProviderTest {

    @Mock
    private WriteTransaction tx;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private TransactionChain chain;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private WriteTransaction writeTx;

    private NetconfDeviceSalProvider provider;

    @Before
    public void setUp() throws Exception {
        doReturn(chain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTx).when(chain).newWriteOnlyTransaction();
        doNothing().when(writeTx).merge(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn("Some object").when(writeTx).getIdentifier();
        doReturn(emptyFluentFuture()).when(writeTx).commit();
        provider = new NetconfDeviceSalProvider(new RemoteDeviceId("device1",
                InetSocketAddress.createUnresolved("localhost", 17830)), mountPointService, dataBroker);
        when(chain.newWriteOnlyTransaction()).thenReturn(tx);
        doReturn(emptyFluentFuture()).when(tx).commit();
        when(tx.getIdentifier()).thenReturn(tx);
    }

    @Test
    public void replaceChainIfFailed() throws Exception {
        final ArgumentCaptor<TransactionChainListener> captor = ArgumentCaptor.forClass(TransactionChainListener.class);
        verify(dataBroker).createTransactionChain(captor.capture());
        try {
            captor.getValue().onTransactionChainFailed(chain, tx, new Exception("chain failed"));
        } catch (final IllegalStateException e) {
            //expected
        }
        verify(dataBroker, times(2)).createTransactionChain(any());
    }

    @Test
    public void close() throws Exception {
        provider.close();
        verify(chain).close();
    }

    @Test
    public void closeWithoutNPE() throws Exception {
        provider.close();
        provider.close();
        verify(chain, times(2)).close();
    }
}
