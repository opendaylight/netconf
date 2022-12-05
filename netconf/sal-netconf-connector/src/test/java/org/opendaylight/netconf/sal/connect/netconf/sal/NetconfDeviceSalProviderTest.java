/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Mockito.doReturn;
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
    @Captor
    private ArgumentCaptor<TransactionChainListener> listeners;

    private NetconfDeviceSalProvider provider;

    @Before
    public void setUp() {
        doReturn(chain).when(dataBroker).createMergingTransactionChain(listeners.capture());
        doReturn(writeTx).when(chain).newWriteOnlyTransaction();
        doReturn("Some object").when(writeTx).getIdentifier();
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        provider = new NetconfDeviceSalProvider(new RemoteDeviceId("device1",
                InetSocketAddress.createUnresolved("localhost", 17830)), mountPointService, dataBroker);
        doReturn(tx).when(chain).newWriteOnlyTransaction();
        doReturn(CommitInfo.emptyFluentFuture()).when(tx).commit();
        doReturn(tx).when(tx).getIdentifier();
    }

    @Test
    public void close() {
        listeners.getValue().onTransactionChainSuccessful(chain);
        provider.close();
        verify(chain).close();
    }

    @Test
    public void closeWithoutNPE()  {
        close();

        // No further interations
        provider.close();
    }
}
