/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ReadOnlyTxTest {
    @Mock
    private Rpcs.Normalized rpc;
    @Mock
    private ContainerNode mockedNode;

    private NetconfBaseOps netconfOps;

    @Before
    public void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc).invokeNetconf(any(), any());
        netconfOps = new NetconfBaseOps(rpc, mock(MountPointContext.class));
    }

    @Test
    public void testRead() throws Exception {
        try (var readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME),
                any(ContainerNode.class));
            readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME), any());
        }
    }

    @Test
    public void testExists() throws Exception {
        try (var readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            readOnlyTx.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME), any());
            readOnlyTx.exists(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(NetconfMessageTransformUtil.NETCONF_GET_QNAME), any());
        }
    }

    @Test
    public void testIdentifier() throws Exception {
        try (var tx1 = new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            try (var tx2 = new ReadOnlyTx(netconfOps, new RemoteDeviceId("a",
                    new InetSocketAddress("localhost", 196)))) {
                assertNotEquals(tx1.getIdentifier(), tx2.getIdentifier());
            }
        }
    }
}