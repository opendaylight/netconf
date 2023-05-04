/*
 * Copyright (c) 2019 Lumina Networks, Inc. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.getSourceNode;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID;

import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class KeepaliveSalFacadeResponseWaitingTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    private static final @NonNull ContainerNode KEEPALIVE_PAYLOAD =
        NetconfMessageTransformUtil.wrap(NETCONF_GET_CONFIG_NODEID,
            getSourceNode(NETCONF_RUNNING_NODEID), NetconfMessageTransformUtil.EMPTY_FILTER);

    private KeepaliveSalFacade keepaliveSalFacade;
    private ScheduledExecutorService executorService;

    private LocalNetconfSalFacade underlyingSalFacade;

    @Mock
    private Rpcs.Normalized deviceRpc;

    @Mock
    private NetconfDeviceCommunicator listener;

    @Before
    public void setUp() throws Exception {
        executorService = Executors.newScheduledThreadPool(2);

        underlyingSalFacade = new LocalNetconfSalFacade();
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
        verify(deviceRpc, after(2000).never()).invokeRpc(NETCONF_GET_CONFIG_QNAME, KEEPALIVE_PAYLOAD);
    }

    private static final class LocalNetconfSalFacade implements RemoteDeviceHandler {
        private volatile Rpcs.Normalized rpcs;

        @Override
        public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
                final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
            final var newRpcs = services.rpcs();
            assertThat(newRpcs, instanceOf(Rpcs.Normalized.class));
            rpcs = (Rpcs.Normalized) newRpcs;
        }

        @Override
        public void onDeviceDisconnected() {
            rpcs = null;
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
            final var local = rpcs;
            if (local != null) {
                local.invokeRpc(null, null);
            }
        }
    }
}

