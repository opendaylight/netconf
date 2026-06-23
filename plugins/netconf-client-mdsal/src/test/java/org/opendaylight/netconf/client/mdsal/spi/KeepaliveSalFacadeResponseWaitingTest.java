/*
 * Copyright (c) 2019 Lumina Networks, Inc. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
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
import org.opendaylight.netconf.client.mdsal.api.NegotiatedSshAlg;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.RpcsTimeoutAndRecoveryHandler.NormalizedTimeoutRpcs;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class KeepaliveSalFacadeResponseWaitingTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private NormalizedTimeoutRpcs deviceRpc;
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
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 2L);
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
    void testKeepalive() {
        final var future = SettableFuture.<DefaultDOMRpcResult>create();
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
        verify(deviceRpc, timeout(4500).times(2)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Check if there are at least 3 created tasks and only the last one is active.
        assertKeepaliveTaskState(3);
    }

    /**
     * Check if invoking an RPC before keepalive delay is reached causes keepalive to not be called, but rescheduled.
     */
    @Test
    void testPostponedKeepalive() throws InterruptedException {
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

        // Check that keepalive is not called and there are 2 more tasks created
        // (rescheduled tasks, because keepalive delay was not reached)
        verify(deviceRpc, after(1500).never()).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        Assertions.assertEquals(3, timer.keepaliveTasks.size());

        // Check if Keepalive will be called again after keepalive delay was reached
        verify(deviceRpc, timeout(1000).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Check if there are at least 4 created tasks and only the last one is active.
        assertKeepaliveTaskState(4);
    }

    /**
     * In case that RPC takes longer to finish than is duration of keepalive delay, keepalive RPC will not be called
     * during RPC processing.
     */
    @Test
    void testRpcLongerThanKeepalive() {
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

        // Keepalive RPC is not invoked when RPC is in progress
        verify(deviceRpc, after(2500).never()).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        assertKeepaliveTaskState(2);

        // Set RPC result
        getSettableFuture.set(new DefaultDOMRpcResult());

        // Keepalive RPC is invoked after keepalive delay is reached
        verify(deviceRpc, timeout(3300).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        assertKeepaliveTaskState(3);
    }

    /**
     * Keepalive is blocked until all parallel RPCs have completed.
     */
    @Test
    void testParallelRpcsBlockKeepaliveUntilAllFinish() {
        final var firstGetFuture = SettableFuture.create();
        final var secondGetFuture = SettableFuture.create();
        doReturn(firstGetFuture, secondGetFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final var keepaliveFuture = SettableFuture.create();
        keepaliveFuture.set(new DefaultDOMRpcResult(node));
        doReturn(keepaliveFuture).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPCs
        underlyingSalFacade.invokeGetRpc();
        underlyingSalFacade.invokeGetRpc();

        // Keepalive RPC is not invoked when RPCs are in progress
        verify(deviceRpc, after(2500).never()).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Keepalive RPC is not invoked when one RPC is still in progress
        firstGetFuture.set(new DefaultDOMRpcResult());
        verify(deviceRpc, after(1200).never()).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Keepalive RPC is invoked after all RPCs finish and keepalive delay is reached
        secondGetFuture.set(new DefaultDOMRpcResult());
        verify(deviceRpc, timeout(3300).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        assertKeepaliveTaskState(4);
    }

    /**
     * Asynchronous RPC failure clears in-flight tracking.
     */
    @Test
    void testFailedRpcDoesNotLeaveKeepaliveBlocked() {
        final var getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final var keepaliveFuture = SettableFuture.create();
        keepaliveFuture.set(new DefaultDOMRpcResult(node));
        doReturn(keepaliveFuture).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC and fail it
        underlyingSalFacade.invokeGetRpc();
        getSettableFuture.setException(new IllegalStateException("illegal-state"));

        // Keepalive RPC is invoked after keepalive delay is reached
        verify(deviceRpc, timeout(2500).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
    }

    /**
     * Synchronous RPC invocation failure clears in-flight tracking.
     */
    @Test
    void testSynchronousRpcInvocationFailureDoesNotLeaveKeepaliveBlocked() {
        doThrow(new IllegalStateException("illegal-state")).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final var keepaliveFuture = SettableFuture.create();
        keepaliveFuture.set(new DefaultDOMRpcResult(node));
        doReturn(keepaliveFuture).when(deviceRpc).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC and verify the synchronous exception is propagated
        assertThrows(IllegalStateException.class, underlyingSalFacade::invokeGetRpc);

        // Keepalive RPC is invoked after keepalive delay is reached
        verify(deviceRpc, timeout(2500).times(1)).invokeNetconf(GetConfig.QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);
    }

    private void assertKeepaliveTaskState(final int minTaskCount) {
        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> hasKeepaliveTaskState(minTaskCount));

        final var size = timer.keepaliveTasks.size();
        Assertions.assertTrue(size >= minTaskCount,
            () -> "Expected at least " + minTaskCount + " keepalive tasks, but got " + size);
        for (int i = 0; i < size - 1; i++) {
            Assertions.assertTrue(timer.keepaliveTasks.get(i).isExpired(), "Expected keepalive task " + i
                + " to be expired");
        }
        Assertions.assertFalse(timer.keepaliveTasks.get(size - 1).isExpired(), "Expected latest keepalive task to be "
            + "active");
    }

    private boolean hasKeepaliveTaskState(final int minTaskCount) {
        final var size = timer.keepaliveTasks.size();
        if (size < minTaskCount || timer.keepaliveTasks.get(size - 1).isExpired()) {
            return false;
        }
        for (int i = 0; i < size - 1; i++) {
            if (!timer.keepaliveTasks.get(i).isExpired()) {
                return false;
            }
        }
        return true;
    }

    private static final class LocalNetconfSalFacade implements RemoteDeviceHandler {
        private volatile Rpcs.Normalized rpcs;

        @Override
        public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
                final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services,
                final NegotiatedSshAlg negotiatedSshAlg) {
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
