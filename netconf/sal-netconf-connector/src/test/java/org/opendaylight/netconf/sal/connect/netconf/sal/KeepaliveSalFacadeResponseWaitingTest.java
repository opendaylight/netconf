/*
 * Copyright (c) 2019 Lumina Networks, Inc. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps.getSourceNode;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME;

import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

public class KeepaliveSalFacadeResponseWaitingTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    private static final ContainerNode KEEPALIVE_PAYLOAD = NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID,
            getSourceNode(NETCONF_RUNNING_QNAME), NetconfMessageTransformUtil.EMPTY_FILTER);

    private KeepaliveSalFacade keepaliveSalFacade;
    private ScheduledExecutorService executorService;

    private LocalNetconfSalFacade underlyingSalFacade;

    @Mock
    private DOMRpcService deviceRpc;

    @Mock
    private DOMMountPointService mountPointService;

    @Mock
    private DataBroker dataBroker;

    @Mock
    private NetconfDeviceCommunicator listener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        executorService = Executors.newScheduledThreadPool(2);

        underlyingSalFacade = new LocalNetconfSalFacade();
        doNothing().when(listener).disconnect();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, executorService, 2L, 10000L);
        keepaliveSalFacade.setListener(listener);
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    /**
     * Not sending keepalive rpc test while the repsonse is processing.
     */
    @Test
    public void testKeepaliveSalResponseWaiting() {
        //This settable future object will be never set to any value. The test wants to simulate waiting for the result
        //of the future object.
        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();
        doReturn(settableFuture).when(deviceRpc).invokeRpc(null, null);

        //This settable future will be used to check the invokation of keepalive RPC. Should be never invoked.
        final SettableFuture<DOMRpcResult> keepaliveSettableFuture = SettableFuture.create();
        doReturn(keepaliveSettableFuture).when(deviceRpc).invokeRpc(NETCONF_GET_CONFIG_QNAME, KEEPALIVE_PAYLOAD);
        final DOMRpcResult keepaliveResult = new DefaultDOMRpcResult(Builders.containerBuilder().withNodeIdentifier(
                new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME)).build());
        keepaliveSettableFuture.set(keepaliveResult);

        keepaliveSalFacade.onDeviceConnected(null, null, deviceRpc);

        //Invoke general RPC on simulated local facade without args (or with null args). Will be returned
        //settableFuture variable without any set value. WaitingShaduler in keepalive sal facade should wait for any
        //result from the RPC and reset keepalive scheduler.
        underlyingSalFacade.invokeNullRpc();

        //Invoking of general RPC.
        verify(deviceRpc, after(2000).times(1)).invokeRpc(null, null);

        //verify the keepalive RPC invoke. Should be never happen.
        verify(deviceRpc, after(2000).never()).invokeRpc(NETCONF_GET_CONFIG_QNAME, KEEPALIVE_PAYLOAD);
    }

    private final class LocalNetconfSalFacade implements RemoteDeviceHandler<NetconfSessionPreferences> {

        private DOMRpcService localDeviceRpc;

        @Override
        public void onDeviceConnected(final MountPointContext remoteSchemaContext,
                final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService currentDeviceRpc,
                final DOMActionService deviceAction) {
            localDeviceRpc = currentDeviceRpc;
        }

        @Override
        public void onDeviceDisconnected() {
        }

        @Override
        public void onDeviceFailed(final Throwable throwable) {
        }

        @Override
        public void onNotification(final DOMNotification domNotification) {
        }

        @Override
        public void close() {
        }

        public void invokeNullRpc() {
            if (localDeviceRpc != null) {
                localDeviceRpc.invokeRpc(null, null);
            }
        }
    }
}

