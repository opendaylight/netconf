/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class KeepaliveSalFacadeTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private RemoteDeviceHandler underlyingSalFacade;
    @Mock
    private NetconfDeviceCommunicator listener;
    @Mock
    private Rpcs.Normalized deviceRpc;
    @Mock
    private DOMRpcService deviceDomRpc;

    private DefaultNetconfTimer timer;
    private KeepaliveSalFacade keepaliveSalFacade;
    private Rpcs proxyRpc;

    @BeforeEach
    void beforeEach() {
        timer = new DefaultNetconfTimer();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 1L, 1L);
        keepaliveSalFacade.setListener(listener);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();
    }

    @AfterEach
    void afterEach() {
        timer.close();
    }

    @Test
    void testKeepaliveSuccess() {
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID)
            .build()))).when(deviceRpc).invokeNetconf(any(), any());

        final var services = new RemoteDeviceServices(deviceRpc, null);
        keepaliveSalFacade.onDeviceConnected(null, null, services);

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
        verify(deviceRpc, timeout(15000).times(5)).invokeNetconf(any(), any());
    }

    @Test
    void testKeepaliveRpcFailure() {
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
        doReturn(Futures.immediateFailedFuture(new IllegalStateException("illegal-state")))
                .when(deviceRpc).invokeNetconf(any(), any());
        doNothing().when(listener).disconnect();

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());

        // Should disconnect the session
        verify(listener, timeout(15000).times(1)).disconnect();
        verify(deviceRpc, times(1)).invokeNetconf(any(), any());
    }

    @Test
    void testKeepaliveSuccessWithRpcError() {
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mock(RpcError.class)))).when(deviceRpc)
            .invokeNetconf(any(), any());

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());

        // Shouldn't disconnect the session
        verify(listener, never()).disconnect();
        verify(deviceRpc, timeout(15000).times(1)).invokeNetconf(any(), any());
    }

    @Test
    void testNonKeepaliveRpcFailure() {
        doAnswer(invocation -> proxyRpc = invocation.getArgument(2, RemoteDeviceServices.class).rpcs())
                .when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));
        doReturn(Futures.immediateFailedFuture(new IllegalStateException("illegal-state")))
                .when(deviceDomRpc).invokeRpc(any(), any());

        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 100L, 1L);
        keepaliveSalFacade.setListener(listener);

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        assertInstanceOf(Rpcs.Normalized.class, proxyRpc).domRpcService()
            .invokeRpc(QName.create("foo", "bar"), mock(ContainerNode.class));

        verify(listener, times(1)).disconnect();
    }

    @Test
    void testKeepaliveRpcResponseTimeout() {
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
        doNothing().when(listener).disconnect();
        doReturn(SettableFuture.create()).when(deviceRpc).invokeNetconf(any(), any());

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        verify(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any(RemoteDeviceServices.class));

        // Should disconnect the session because RPC result future is never resolved and keepalive delay is 1 sec
        verify(listener, timeout(115000).times(1)).disconnect();
        verify(deviceRpc, times(1)).invokeNetconf(any(), any());
    }
}
