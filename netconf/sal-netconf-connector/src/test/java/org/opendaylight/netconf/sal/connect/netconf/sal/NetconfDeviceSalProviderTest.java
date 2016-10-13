/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
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
    private NetconfDeviceSalProvider provider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        provider = new NetconfDeviceSalProvider(new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("localhost", 17830)));
        when(session.getService(DOMMountPointService.class)).thenReturn(mountpointService);
        when(context.getSALService(DataBroker.class)).thenReturn(dataBroker);
        when(dataBroker.createTransactionChain(any())).thenReturn(chain);
        when(chain.newWriteOnlyTransaction()).thenReturn(tx);
        when(tx.submit()).thenReturn(Futures.immediateCheckedFuture(null));
        when(tx.getIdentifier()).thenReturn(tx);
    }

    @Test
    public void onSessionInitiated() throws Exception {
        provider.onSessionInitiated(session);
        provider.onSessionInitiated(context);
        Assert.assertNotNull(provider.getMountInstance());
        Assert.assertNotNull(provider.getTopologyDatastoreAdapter());
    }

    @Test
    public void getProviderFunctionality() throws Exception {
        Assert.assertTrue(provider.getProviderFunctionality().isEmpty());
    }

    @Test
    public void replaceChainIfFailed() throws Exception {
        provider.onSessionInitiated(session);
        provider.onSessionInitiated(context);
        Assert.assertNotNull(provider.getMountInstance());
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
        provider.onSessionInitiated(session);
        provider.onSessionInitiated(context);
        provider.close();
        verify(chain).close();
    }

}