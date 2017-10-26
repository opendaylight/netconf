/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class NetconfDeviceSalProviderTest {

    @Mock
    private Broker.ProviderSession session;
    @Mock
    private DOMMountPointService mountpointService;
    @Mock
    private BindingAwareBroker.ProviderContext context;
    @Mock
    private WriteTransaction tx;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private BindingTransactionChain chain;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private WriteTransaction writeTx;
    @Mock
    private ReadOnlyTransaction readTx;
    private NetconfDeviceSalProvider provider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(chain).when(dataBroker).createTransactionChain(any(TransactionChainListener.class));
        doReturn(writeTx).when(chain).newWriteOnlyTransaction();
        doNothing().when(writeTx).merge(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn("Some object").when(writeTx).getIdentifier();
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        provider = new NetconfDeviceSalProvider(new RemoteDeviceId("device1",
                InetSocketAddress.createUnresolved("localhost", 17830)), mountPointService, dataBroker);
        when(session.getService(DOMMountPointService.class)).thenReturn(mountpointService);
        when(context.getSALService(DataBroker.class)).thenReturn(dataBroker);
        when(chain.newWriteOnlyTransaction()).thenReturn(tx);
        when(tx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        when(tx.getIdentifier()).thenReturn(tx);
        // TODO: Split up tests which test presence of previous master (both same and different one).
        when(chain.newReadOnlyTransaction()).thenReturn(readTx);
        when(readTx.read(any(), any()))
                .thenReturn(Futures.immediateCheckedFuture(Optional.absent()));
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