/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class KeepaliveSalFacadeTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private RemoteDeviceHandler<NetconfSessionPreferences> underlyingSalFacade;

    private ScheduledExecutorService executorServiceSpy;

    @Mock
    private NetconfDeviceCommunicator listener;
    @Mock
    private DOMRpcService deviceRpc;

    private DOMRpcService proxyRpc;

    @Mock
    private ScheduledFuture<?> currentKeepalive;

    private KeepaliveSalFacade keepaliveSalFacade;

    @Before
    public void setUp() throws Exception {
        executorServiceSpy = Executors.newScheduledThreadPool(1);

        doNothing().when(listener).disconnect();
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(DOMRpcService.class), isNull());

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorServiceSpy = Mockito.spy(executorService);

        doAnswer(invocationOnMock -> {
            invocationOnMock.callRealMethod();
            return currentKeepalive;
        }).when(executorServiceSpy).schedule(Mockito.<Runnable>any(), Mockito.anyLong(), any());

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
        final DOMRpcResult result = new DefaultDOMRpcResult(Builders.containerBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME)).build());

        doReturn(FluentFutures.immediateFluentFuture(result))
                .when(deviceRpc).invokeRpc(any(QName.class), any(ContainerNode.class));

        keepaliveSalFacade.onDeviceConnected(null, null, deviceRpc);

        verify(underlyingSalFacade).onDeviceConnected(
                isNull(), isNull(), any(DOMRpcService.class), isNull());

        verify(deviceRpc, timeout(15000).times(5)).invokeRpc(any(QName.class), any(ContainerNode.class));
    }

    @Test
    public void testKeepaliveRpcFailure() {

        doReturn(FluentFutures.immediateFailedFluentFuture(new IllegalStateException("illegal-state")))
                .when(deviceRpc).invokeRpc(any(QName.class), any(ContainerNode.class));

        keepaliveSalFacade.onDeviceConnected(null, null, deviceRpc);

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(DOMRpcService.class), isNull());

        // Should disconnect the session
        verify(listener, timeout(15000).times(1)).disconnect();
        verify(deviceRpc, times(1)).invokeRpc(any(QName.class), any(ContainerNode.class));
    }

    @Test
    public void testKeepaliveSuccessWithRpcError() {

        final DOMRpcResult rpcSuccessWithError = new DefaultDOMRpcResult(mock(RpcError.class));

        doReturn(FluentFutures.immediateFluentFuture(rpcSuccessWithError))
                .when(deviceRpc).invokeRpc(any(QName.class), any(ContainerNode.class));

        keepaliveSalFacade.onDeviceConnected(null, null, deviceRpc);

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(DOMRpcService.class), isNull());


        // Shouldn't disconnect the session
        verify(listener, times(0)).disconnect();
        verify(deviceRpc, timeout(15000).times(1)).invokeRpc(any(QName.class), any(ContainerNode.class));
    }

    @Test
    public void testNonKeepaliveRpcFailure() throws Exception {
        doAnswer(invocation -> proxyRpc = invocation.getArgument(2))
                .when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(DOMRpcService.class), isNull());

        doReturn(FluentFutures.immediateFailedFluentFuture(new IllegalStateException("illegal-state")))
                .when(deviceRpc).invokeRpc(any(QName.class), any(ContainerNode.class));

        keepaliveSalFacade =
                new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, executorServiceSpy, 100L, 1L);
        keepaliveSalFacade.setListener(listener);

        keepaliveSalFacade.onDeviceConnected(null, null, deviceRpc);

        proxyRpc.invokeRpc(QName.create("foo", "bar"), mock(ContainerNode.class));

        verify(listener, times(1)).disconnect();
    }
}
