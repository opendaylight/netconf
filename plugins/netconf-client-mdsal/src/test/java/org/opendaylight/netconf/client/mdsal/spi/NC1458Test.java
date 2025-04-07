/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.spi.KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NC1458Test {
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);
    private static final int RPC_MESSAGE_LIMIT = 10;
    private static final RemoteDeviceId REMOTE_DEVICE_ID =
        new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));
    // Separate executor to avoid inheriting parent thread properties. This prevents access to synchronized method
    // locks created by parent thread.
    private static final ExecutorService DISCONNECT_EXECUTOR = Executors.newSingleThreadExecutor(
        runnable -> new Thread(runnable, "disconnect-task-thread")
    );

    @Mock
    private RemoteDevice<NetconfDeviceCommunicator> mockDevice;
    @Mock
    private RemoteDeviceHandler underlyingSalFacade;
    @Mock
    private RemoteDeviceServices.Rpcs.Normalized deviceRpc;
    @Mock
    private DOMRpcService deviceDomRpc;
    @Mock
    private DOMRpcResult domRpcResult;

    private NetconfDeviceCommunicator communicator;
    private KeepaliveSalFacade keepaliveSalFacade;

    @BeforeEach
    void beforeEach() {
        // Prepare NetconfDeviceCommunicator and KeepaliveSalFacade.
        communicator = new NetconfDeviceCommunicator(new RemoteDeviceId("test-device",
            InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, RPC_MESSAGE_LIMIT);

        final var timer = new DefaultNetconfTimer();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, timer, 1L, 10000L);
        keepaliveSalFacade.setListener(communicator);
        doReturn(deviceDomRpc).when(deviceRpc).domRpcService();

        // Set session into NetconfDeviceCommunicator instance.
        doNothing().when(mockDevice).onRemoteSessionUp(any(NetconfSessionPreferences.class),
            any(NetconfDeviceCommunicator.class));
        final var session = new NetconfClientSession(mock(NetconfClientSessionListener.class), mock(Channel.class),
                SESSION_ID, Set.of());
        communicator.onSessionUp(session);

        // Do nothing when KeepaliveTask call onDeviceConnected in scheduled sendKeepalive method.
        doNothing().when(underlyingSalFacade).onDeviceConnected(isNull(), isNull(), any());
    }

    @AfterEach
    void afterEach() {
        keepaliveSalFacade.close();
        communicator.close();
    }

    @AfterAll
    static void after() {
        DISCONNECT_EXECUTOR.shutdown();
    }

    @Test
    void testDeadlockWithSessionLockAndKeepaliveTaskSynchronization() throws Exception {
        // Create a test keepalive netconf message.
        final var doc = UntrustedXML.newDocumentBuilder().newDocument();
        final var element = doc.createElement("request");
        element.setAttribute("message-id", "keepalive-messageID");
        doc.appendChild(element);
        final var message = new NetconfMessage(doc);
        final var ret = SettableFuture.<DOMRpcResult>create();
        // Mock invokeNetconf method when sendKeepalive method is invoked in KeepaliveTask.
        doAnswer(invocation -> {
            // Run session tearDown in a new separate thread, which acquires the sessionLock.
            CompletableFuture.runAsync(() -> communicator.onSessionTerminated(null,
                new NetconfTerminationReason("Session closed")), DISCONNECT_EXECUTOR);
            // Wait until onRemoteSessionDown is called to ensure the lock is acquired in the other thread.
            verify(mockDevice, timeout(2000)).onRemoteSessionDown();
            // Send a test keepalive message, which should be blocked until session tearDown release sessionLock.
            final var delegateFuture = communicator.sendRequest(message);
            // When sendRequest method finishes with a result, wrap it up to successfully finalize sendKeepalive
            // method in KeepaliveTask, which will allow other threads to call other KeepaliveTask methods.
            Futures.addCallback(delegateFuture, new FutureCallback<>() {
                @Override
                public void onSuccess(final RpcResult<NetconfMessage> result) {
                    if (!result.isSuccessful()) {
                        ret.set(new DefaultDOMRpcResult(result.getErrors()));
                    } else {
                        ret.set(domRpcResult);
                    }
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    ret.setException(throwable);
                }
            }, MoreExecutors.directExecutor());
            return ret;
        }).when(deviceRpc).invokeNetconf(eq(GetConfig.QNAME), eq(KEEPALIVE_PAYLOAD));

        // When the device receives onRemoteSessionDown, it calls the KeepaliveSalFacade onDeviceDisconnected method,
        // the same as it would with a real device. This call will try to stop keepalive messages, which can cause
        // a deadlock with the current keepalive message in progress.
        doAnswer(invocationOnMock -> {
            keepaliveSalFacade.onDeviceDisconnected();
            return null;
        }).when(mockDevice).onRemoteSessionDown();

        // Invoke onDeviceConnected in KeepaliveSalFacade to create KeepaliveTask with a scheduled start.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Verify that after prepared environment the RemoteDeviceHandler will receive calls for onDeviceDisconnected,
        // which is done after successful stop of keepalive task.
        verify(underlyingSalFacade, timeout(10000)).onDeviceDisconnected();

        // Verify that sendRequest method fails on session disconnected error.
        final var sendRequestResult = ret.get(2, TimeUnit.SECONDS);
        final var firstError = sendRequestResult.errors().iterator().next();
        assertNotNull(firstError);
        assertEquals("The netconf session to test-device is disconnected", firstError.getMessage());
    }
}
