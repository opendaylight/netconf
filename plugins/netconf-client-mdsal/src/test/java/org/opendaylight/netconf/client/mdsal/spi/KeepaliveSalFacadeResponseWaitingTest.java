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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
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
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChanges;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;

@ExtendWith(MockitoExtension.class)
class KeepaliveSalFacadeResponseWaitingTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    @Mock
    private Rpcs.Normalized deviceRpc;
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
     * Not sending keepalive rpc test while the repsonse is processing.
     */
    @Test
    void testKeepaliveSalResponseWaiting() {
        // This settable future object will be never set to any value. The test wants to simulate waiting for the result
        // of the future object.
        final var settableFuture = SettableFuture.<DOMRpcResult>create();
        doReturn(settableFuture).when(deviceDomRpc).invokeRpc(null, null);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke general RPC on simulated local facade with null args. Sending of general RPC suppresses sending
        // of keepalive in KeepaliveTask run.
        // Request timeout (10 sec) is scheduled for general RPC in keepalive sal facade. It should wait for any result
        // from the RPC, and then it disables suppression of sending keepalive RPC.
        // Variable "settableFuture" which is never completed (RPC result is never set) will be returned.
        underlyingSalFacade.invokeNullRpc();

        // Verify invocation of general RPC.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(null, null);

        // Verify the keepalive RPC invocation never happened because it was suppressed by sending of general RPC.
        verify(deviceDomRpc, after(2500).never()).invokeRpc(GetConfig.QNAME,
                KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Verify there was only one KeepaliveTask scheduled (next KeepaliveTask would be scheduled if general RPC
        // result was received).
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * Key point is that RPC unlock is sent in callback of RPC commit.
     */
    @Test
    public void testKeepaliveSalWithRpcCommitAndRpcUnlock() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> commitSettableFuture = SettableFuture.create();
        doReturn(commitSettableFuture).when(deviceDomRpc).invokeRpc(Commit.QNAME, null);
        final SettableFuture<DOMRpcResult> unlockSettableFuture = SettableFuture.create();
        doReturn(unlockSettableFuture).when(deviceDomRpc).invokeRpc(Unlock.QNAME, null);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC commit on simulated local facade, and it adds callback which invokes RPC unlock on RPC commit
        // result.
        underlyingSalFacade.performCommit();

        // Wait 0.5sec and verify RPC commit is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Commit.QNAME, null);
        // Set RPC commit result and it calls callback invoking RPC unlock
        commitSettableFuture.set(new DefaultDOMRpcResult());

        // Wait 0.5sec and verify RPC unlock is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Unlock.QNAME, null);
        // Set RPC unlock result.
        unlockSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run has kicked in so verify there was only one keepalive
        // task scheduled.
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertFalse(timer.keepaliveTasks.get(0).isExpired());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * RPC discard-changes and RPC unlock are sent asynchronously in callback of RPC commit.
     */
    @Test
    public void testKeepaliveSalWithRpcCommitErrorRpcDiscardChangesRpcUnlock() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> commitSettableFuture = SettableFuture.create();
        doReturn(commitSettableFuture).when(deviceDomRpc).invokeRpc(Commit.QNAME, null);
        final SettableFuture<DOMRpcResult> discardChangesSettableFuture = SettableFuture.create();
        doReturn(discardChangesSettableFuture).when(deviceDomRpc).invokeRpc(DiscardChanges.QNAME, null);
        final SettableFuture<DOMRpcResult> unlockSettableFuture = SettableFuture.create();
        doReturn(unlockSettableFuture).when(deviceDomRpc).invokeRpc(Unlock.QNAME, null);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC commit on simulated local facade, and it adds callback which invokes RPC discard-changes and RPC
        // unlock on RPC commit error result.
        underlyingSalFacade.performCommitWithError();

        // Wait 0.5sec and verify RPC commit is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Commit.QNAME, null);
        // Set RPC commit result, and it calls callback invoking RPC discard-changes and RPC unlock
        commitSettableFuture.set(new DefaultDOMRpcResult());

        // Wait 0.5sec and verify RPC discard-changes is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(DiscardChanges.QNAME, null);
        // Wait 0.5sec and verify RPC unlock is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Unlock.QNAME, null);
        // Set RPC discard-changes result.
        discardChangesSettableFuture.set(new DefaultDOMRpcResult());
        // Set RPC unlock result.
        unlockSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run so verify there was only one keepalive task scheduled.
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertFalse(timer.keepaliveTasks.get(0).isExpired());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * RPC get and RPC get-config are sent in parallel.
     */
    @Test
    public void testKeepaliveSalWithParallelRpcGetRpcGetConfig() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final SettableFuture<DOMRpcResult> getConfigSettableFuture = SettableFuture.create();
        doReturn(getConfigSettableFuture).when(deviceDomRpc).invokeRpc(GetConfig.QNAME, null);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC get and RPC get-config on simulated local facade.
        underlyingSalFacade.invokeGetRpc();
        underlyingSalFacade.invokeGetConfigRpc();

        // Wait 0.5sec and verify RPC get is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Get.QNAME, null);
        // Wait 0.5sec and verify RPC get-config is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(GetConfig.QNAME, null);

        // Set RPC get result.
        getSettableFuture.set(new DefaultDOMRpcResult());
        // Set RPC get-config result.
        getConfigSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run so verify there was only one keepalive task scheduled.
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertFalse(timer.keepaliveTasks.get(0).isExpired());
    }

    /**
     * Scheduling another keepalive rpc test after all responses are processed.
     * RPC get and RPC get-config are sent in parallel, and it takes a while to receive reply.
     */
    @Test
    public void testKeepaliveSalWithParallelRpcGetRpcGetConfigAndLongerWaitForReply() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceDomRpc).invokeRpc(Get.QNAME, null);
        final SettableFuture<DOMRpcResult> getConfigSettableFuture = SettableFuture.create();
        doReturn(getConfigSettableFuture).when(deviceDomRpc).invokeRpc(GetConfig.QNAME, null);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC get and RPC get-config on simulated local facade.
        underlyingSalFacade.invokeGetRpc();
        underlyingSalFacade.invokeGetConfigRpc();

        // Wait 0.5sec and verify RPC get is invoked.
        verify(deviceDomRpc, after(500).times(1)).invokeRpc(Get.QNAME, null);
        // Wait 5sec and verify RPC get-config is invoked.
        verify(deviceDomRpc, after(5000).times(1)).invokeRpc(GetConfig.QNAME, null);

        // After 5.5sec (keepalive is 2sec) we should see that the KeepaliveTask is done (no keepalive is sent)
        // and no other KeepaliveTask is scheduled because we are waiting for RPC get, get-config replies
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());

        // Set RPC get result.
        getSettableFuture.set(new DefaultDOMRpcResult());
        // RPC reply for RPC get is received, but it should not schedule another KeepaliveTask because we are still
        // waiting for RPC get-config reply.
        Assertions.assertEquals(1, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());

        // Set RPC get-config result.
        getConfigSettableFuture.set(new DefaultDOMRpcResult());
        // RPC reply for RPC get-config is received, and it should schedule another KeepaliveTask because no other
        // RPC replies are expected.
        Assertions.assertEquals(2, timer.keepaliveTasks.size());
        Assertions.assertTrue(timer.keepaliveTasks.get(0).isExpired());
        Assertions.assertFalse(timer.keepaliveTasks.get(1).isExpired());
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
                local.domRpcService().invokeRpc(null, null);
            }
        }

        /**
         * This is simplified version of {@link WriteCandidateTx#performCommit()} but the key point
         * is that RPC unlock is invoked in callback of RPC commit.
         */
        public void performCommit() {
            final var local = rpcs;
            if (local != null) {
                final var commitResult = local.domRpcService().invokeRpc(Commit.QNAME, null);
                Futures.addCallback(commitResult, new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult domRpcResult) {
                        local.domRpcService().invokeRpc(Unlock.QNAME, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                }, MoreExecutors.directExecutor());
            }
        }

        /**
         * This is simplified version of {@link WriteCandidateTx#performCommit()} but the key point
         * is that RPC discard-changes and RPC unlock are invoked asynchronously in callback of RPC commit.
         */
        public void performCommitWithError() {
            final var local = rpcs;
            if (local != null) {
                final var commitResult = local.domRpcService().invokeRpc(Commit.QNAME, null);
                Futures.addCallback(commitResult, new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult domRpcResult) {
                        local.domRpcService().invokeRpc(DiscardChanges.QNAME, null);
                        local.domRpcService().invokeRpc(Unlock.QNAME, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                }, MoreExecutors.directExecutor());
            }
        }

        public void invokeGetRpc() {
            final var local = rpcs;
            if (local != null) {
                local.domRpcService().invokeRpc(Get.QNAME, null);
            }
        }

        public void invokeGetConfigRpc() {
            final var local = rpcs;
            if (local != null) {
                local.domRpcService().invokeRpc(GetConfig.QNAME, null);
            }
        }
    }

    private static final class DefaultNetconfTimerWrapper implements NetconfTimer, AutoCloseable {
        private final DefaultNetconfTimer timer = new DefaultNetconfTimer();
        public List<Timeout> keepaliveTasks = new CopyOnWriteArrayList<>();

        @Override
        public void close() {
            timer.close();
        }

        @Override
        public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
            var timeout = timer.newTimeout(task, delay, unit);
            if (task instanceof KeepaliveSalFacade.KeepaliveTask) {
                keepaliveTasks.add(timeout);
            }
            return timeout;
        }
    }
}

