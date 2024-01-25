/*
 * Copyright (c) 2019 Lumina Networks, Inc. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import java.net.InetSocketAddress;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@ExtendWith(MockitoExtension.class)
class KeepaliveSalFacadeResponseWaitingTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    private static final @NonNull ContainerNode KEEPALIVE_PAYLOAD = NetconfMessageTransformUtil.wrap(
        NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID,
        NetconfBaseOps.getSourceNode(NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID),
        NetconfMessageTransformUtil.EMPTY_FILTER);

    @Mock
    private Rpcs.Normalized deviceRpc;
    @Mock
    private NetconfDeviceCommunicator listener;
    private Timer timer;

    private KeepaliveSalFacade keepaliveSalFacade;
    private LocalNetconfSalFacade underlyingSalFacade;

    @BeforeEach
    void beforeEach() {
        timer = new HashedWheelTimer();

        underlyingSalFacade = new LocalNetconfSalFacade();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 2L, 10000L);
        keepaliveSalFacade.setListener(listener);
    }

    @AfterEach
    void afterEach() {
        timer.stop();
    }

    /**
     * Not sending keepalive rpc test while the repsonse is processing.
     */
    @Test
    void testKeepaliveSalResponseWaiting() {
        //This settable future object will be never set to any value. The test wants to simulate waiting for the result
        //of the future object.
        final var settableFuture = SettableFuture.<DOMRpcResult>create();
        doReturn(settableFuture).when(deviceRpc).invokeRpc(null, null);

        //This settable future will be used to check the invokation of keepalive RPC. Should be never invoked.
        final var keepaliveSettableFuture = SettableFuture.<DOMRpcResult>create();
        keepaliveSettableFuture.set(new DefaultDOMRpcResult(Builders.containerBuilder()
            .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID)
            .build()));

        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        //Invoke general RPC on simulated local facade without args (or with null args). Will be returned
        //settableFuture variable without any set value. WaitingShaduler in keepalive sal facade should wait for any
        //result from the RPC and reset keepalive scheduler.
        underlyingSalFacade.invokeNullRpc();

        //Invoking of general RPC.
        verify(deviceRpc, after(2000).times(1)).invokeRpc(null, null);

        //verify the keepalive RPC invoke. Should be never happen.
        verify(deviceRpc, after(2000).never()).invokeRpc(GetConfig.QNAME, KEEPALIVE_PAYLOAD);
    }

    private static final class LocalNetconfSalFacade implements RemoteDeviceHandler {
        private volatile Rpcs.Normalized rpcs;

        @Override
        public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
                final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
            rpcs = assertInstanceOf(Rpcs.Normalized.class, services.rpcs());
        }

        @Override
        public void onDeviceDisconnected() {
            rpcs = null;
        }

        @Override
        public void onDeviceFailed(final Throwable throwable) {
            // No-op
        }

        @Override
        public void onNotification(final DOMNotification domNotification) {
            // No-op
        }

        @Override
        public void close() {
            // No-op
        }

        public void invokeNullRpc() {
            final var local = rpcs;
            if (local != null) {
                local.invokeRpc(null, null);
            }
        }
    }
}

