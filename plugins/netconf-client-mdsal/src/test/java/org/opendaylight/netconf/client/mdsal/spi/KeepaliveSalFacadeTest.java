/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class KeepaliveSalFacadeTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private RemoteDeviceHandler underlyingSalFacade;
    @Mock
    private NetconfDeviceCommunicator listener;
    @Mock
    private Rpcs.Normalized deviceRpc;

    private ScheduledExecutorService executorServiceSpy;
    private KeepaliveSalFacade keepaliveSalFacade;
    private Rpcs proxyRpc;

    @Before
    public void setUp() throws Exception {
        executorServiceSpy = Executors.newScheduledThreadPool(1);

        doNothing().when(listener).disconnect();
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        keepaliveSalFacade =
                new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, executorServiceSpy, 1L, 1L);
        keepaliveSalFacade.setListener(listener);
    }

    @After
    public void tearDown() throws Exception {
        executorServiceSpy.shutdownNow();
    }

    @Test
    public void testKeepaliveSuccess() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(Builders.containerBuilder()
            .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID)
            .build()))).when(deviceRpc).invokeNetconf(any(), any());

        final var services = new RemoteDeviceServices(deviceRpc, null);
        keepaliveSalFacade.onDeviceConnected(null, null, services);

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        verify(deviceRpc, timeout(15000).times(5)).invokeNetconf(any(), any());
    }

    @Test
    public void testKeepaliveRpcFailure() {
        doReturn(Futures.immediateFailedFuture(new IllegalStateException("illegal-state")))
                .when(deviceRpc).invokeNetconf(any(), any());

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        // Should disconnect the session
        verify(listener, timeout(15000).times(1)).disconnect();
        verify(deviceRpc, times(1)).invokeNetconf(any(), any());
    }

    @Test
    public void testKeepaliveSuccessWithRpcError() {

        final var rpcSuccessWithError = new DefaultDOMRpcResult(mock(RpcError.class));

        doReturn(Futures.immediateFuture(rpcSuccessWithError)).when(deviceRpc).invokeNetconf(any(), any());

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        // Shouldn't disconnect the session
        verify(listener, times(0)).disconnect();
        verify(deviceRpc, timeout(15000).times(1)).invokeNetconf(any(), any());
    }

    @Test
    public void testNonKeepaliveRpcFailure() throws Exception {
        doAnswer(invocation -> proxyRpc = invocation.getArgument(2, RemoteDeviceServices.class).rpcs())
                .when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        doReturn(Futures.immediateFailedFuture(new IllegalStateException("illegal-state")))
                .when(deviceRpc).invokeRpc(any(), any());

        keepaliveSalFacade =
                new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, executorServiceSpy, 100L, 1L);
        keepaliveSalFacade.setListener(listener);

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        assertThat(proxyRpc, instanceOf(Rpcs.Normalized.class));
        ((Rpcs.Normalized) proxyRpc).invokeRpc(QName.create("foo", "bar"), mock(ContainerNode.class));

        verify(listener, times(1)).disconnect();
    }
}
