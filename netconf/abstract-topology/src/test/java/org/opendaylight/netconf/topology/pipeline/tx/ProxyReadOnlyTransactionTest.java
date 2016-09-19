/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline.tx;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import akka.actor.ActorSystem;
import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.pipeline.ProxyNetconfDeviceDataBroker;
import org.opendaylight.netconf.topology.util.messages.NormalizedNodeMessage;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class ProxyReadOnlyTransactionTest {
    private static final RemoteDeviceId REMOTE_DEVICE_ID = new RemoteDeviceId("testing-device", new InetSocketAddress(9999));
    private static final YangInstanceIdentifier path = YangInstanceIdentifier.create();

    @Mock
    private ProxyNetconfDeviceDataBroker mockedProxyDataBroker;

    @Mock
    private ActorSystem mockedActorSystem;

    @Mock
    private NormalizedNodeMessage mockedNodeMessage;

    @Mock
    private NormalizedNode mockedNode;

    private ProxyReadOnlyTransaction proxyReadOnlyTx;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockedActorSystem.dispatcher()).thenReturn(ExecutionContexts.fromExecutorService(MoreExecutors.newDirectExecutorService()));
        when(mockedNodeMessage.getNode()).thenReturn(mockedNode);

        proxyReadOnlyTx = new ProxyReadOnlyTransaction(mockedActorSystem, REMOTE_DEVICE_ID, mockedProxyDataBroker);
    }

    @Test
    public void testSuccessfulRead() throws ReadFailedException {
        when(mockedProxyDataBroker.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class)))
                .thenReturn(Futures.successful(Optional.of(mockedNodeMessage)));
        CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readResultFuture =  proxyReadOnlyTx.read(LogicalDatastoreType.CONFIGURATION, path);
        verify(mockedProxyDataBroker).read(eq(LogicalDatastoreType.CONFIGURATION), eq(path));
        assertTrue(readResultFuture.isDone());
        assertEquals(readResultFuture.checkedGet().get(), mockedNode);
    }

    @Test
    public void testFailedRead() {
        when(mockedProxyDataBroker.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class)))
                .thenReturn(Futures.<Optional<NormalizedNodeMessage>>failed(new ReadFailedException("Test read failed!")));
        CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readResultFuture =  proxyReadOnlyTx.read(LogicalDatastoreType.CONFIGURATION, path);
        verify(mockedProxyDataBroker).read(eq(LogicalDatastoreType.CONFIGURATION), eq(path));
        assertTrue(readResultFuture.isDone());
        try {
            readResultFuture.checkedGet();
            fail("Exception expected");
        } catch(Exception e) {
            assertTrue(e instanceof ReadFailedException);
        }
    }

    @Test
    public void testDataOnPathDoesNotExistPathRead() throws ReadFailedException {
        when(mockedProxyDataBroker.read(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class)))
                .thenReturn(Futures.successful(Optional.absent()));
        CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readResultFuture =  proxyReadOnlyTx.read(LogicalDatastoreType.CONFIGURATION, path);
        verify(mockedProxyDataBroker).read(eq(LogicalDatastoreType.CONFIGURATION), eq(path));
        assertTrue(readResultFuture.isDone());
        assertTrue(!readResultFuture.checkedGet().isPresent());
    }

    @Test
    public void testFailedExists() {
        when(mockedProxyDataBroker.exists(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class)))
                .thenReturn(Futures.<Boolean>failed(new ReadFailedException("Test read failed!")));
        CheckedFuture existsFuture = proxyReadOnlyTx.exists(LogicalDatastoreType.OPERATIONAL, path);
        verify(mockedProxyDataBroker).exists(eq(LogicalDatastoreType.OPERATIONAL), eq(path));
        assertTrue(existsFuture.isDone());
        try {
            existsFuture.checkedGet();
            fail("Exception expected");
        } catch(Exception e) {
            assertTrue(e instanceof ReadFailedException);
        }
    }

    @Test
    public void testExists() throws Exception {
        when(mockedProxyDataBroker.exists(any(LogicalDatastoreType.class), any(YangInstanceIdentifier.class)))
            .thenReturn(Futures.successful(true));
        CheckedFuture<Boolean, ReadFailedException> existsFuture = proxyReadOnlyTx.exists(LogicalDatastoreType.OPERATIONAL, path);
        verify(mockedProxyDataBroker).exists(eq(LogicalDatastoreType.OPERATIONAL), eq(path));
        assertTrue(existsFuture.isDone());
        assertTrue(existsFuture.checkedGet());
    }
}