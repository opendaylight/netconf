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
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class KeepaliveSalFacadeResponseWaitingTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private Rpcs.Normalized deviceRpc;
    @Mock
    private ContainerNode node;
    @Mock
    private DOMRpcService deviceDomRpc;
    @Mock
    private NetconfDeviceCommunicator listener;

    private DefaultNetconfTimerWrapper timer;
    private KeepaliveSalFacade keepaliveSalFacade;
    private LocalNetconfSalFacade underlyingSalFacade;

    @BeforeEach
    void beforeEach() {
        timer = new DefaultNetconfTimerWrapper();

        underlyingSalFacade = new LocalNetconfSalFacade();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 2L, 10000L);
        keepaliveSalFacade.setListener(listener);
    }

    @AfterEach
    void afterEach() {
        timer.close();
    }

    /**
     * Scheduling another keepalive task after successful keepalive rpc.
     */
    @Test
    public void testKeepalive() {
        final var future = SettableFuture.create();
        future.set(new DefaultDOMRpcResult(node));
        doReturn(future).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Check if there is just one active task
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertFalse(timer.keepaliveTasks.get(0).isExpired());

        // Check if Keepalive will be called 2 more times
        verify(deviceRpc, after(4500).times(2)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Check if there are exactly 3 created tasks and only the last one is active
        Assertions.assertEquals(3, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
        Assertions.assertTrue(timer.keepaliveTasks.get(1).isExpired());
        Assertions.assertFalse(timer.keepaliveTasks.get(2).isExpired());
    }

    /**
     * Check if invoking an RPC before keepalive delay is reached causes keepalive to not be called, but rescheduled.
     */
    @Test
    public void testPostponedKeepalive() throws InterruptedException {
        final var getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final var keepaliveFuture = SettableFuture.create();
        keepaliveFuture.set(new DefaultDOMRpcResult(node));
        doReturn(keepaliveFuture).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Check if there is just one active task
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertFalse(timer.keepaliveTasks.get(0).isExpired());

        TimeUnit.MILLISECONDS.sleep(1500);
        underlyingSalFacade.invokeGetRpc();
        TimeUnit.MILLISECONDS.sleep(1500);
        getSettableFuture.set(new DefaultDOMRpcResult());

        // Check if no keepalive is not called and there are 2 more tasks created
        // (rescheduled tasks, because keepalive delay was not reached)
        verify(deviceRpc, after(1500).never()).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        Assertions.assertEquals(3, timer.keepaliveTasks.size());

        // Check if Keepalive will be called again after keepalive delay was reached
        verify(deviceRpc, after(1000).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Check if there are exactly 4 created tasks and only the last one is active
        Assertions.assertEquals(4, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
        Assertions.assertTrue(timer.keepaliveTasks.get(1).isExpired());
        Assertions.assertTrue(timer.keepaliveTasks.get(2).isExpired());
        Assertions.assertFalse(timer.keepaliveTasks.get(3).isExpired());
    }

    /**
     * In case that RPC takes longer to finish than is duration of keepalive delay, keepalive RPC will be called even
     * during RPC processing.
     */
    @Test
    public void testRpcLongerThanKeepalive() {
        final var getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final var keepaliveFuture = SettableFuture.create();
        keepaliveFuture.set(new DefaultDOMRpcResult(node));
        doReturn(keepaliveFuture).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC
        underlyingSalFacade.invokeGetRpc();

        // Keepalive RPC is invoked even when RPC is in progress
        verify(deviceRpc, after(2300).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        Assertions.assertEquals(2, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
        Assertions.assertFalse(timer.keepaliveTasks.get(1).isExpired());

        // Set RPC result
        getSettableFuture.set(new DefaultDOMRpcResult());

        Assertions.assertEquals(2, timer.keepaliveTasks.size());
        // Keepalive RPC is invoked again after keepalive delay is reached
        verify(deviceRpc, after(2300).times(2)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        Assertions.assertEquals(4, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
        Assertions.assertTrue(timer.keepaliveTasks.get(1).isExpired());
        Assertions.assertTrue(timer.keepaliveTasks.get(2).isExpired());
        Assertions.assertFalse(timer.keepaliveTasks.get(3).isExpired());
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

        public void invokeGetRpc() {
            final var local = rpcs;
            if (local != null) {
                local.domRpcService().invokeRpc(Get.QNAME, null);
            }
        }
    }

    private static final class DefaultNetconfTimerWrapper implements NetconfTimer, AutoCloseable {
        private final DefaultNetconfTimer timer = new DefaultNetconfTimer();
        private final List<Timeout> keepaliveTasks = new CopyOnWriteArrayList<>();

        @Override
        public void close() {
            timer.close();
        }

        @Override
        public Timeout newTimeout(final TimerTask task, final long delay, final TimeUnit unit) {
            var timeout = timer.newTimeout(task, delay, unit);
            if (task instanceof KeepaliveSalFacade.KeepaliveTask) {
                keepaliveTasks.add(timeout);
            }
            return timeout;
        }
    }
}

