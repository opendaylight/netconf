/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class ReadOnlyTxTest {
    @Mock
    private Rpcs.Normalized rpc;
    @Mock
    private ContainerNode mockedNode;
    @Mock
    private EffectiveModelContext modelContext;

    private NetconfBaseOps netconfOps;

    @BeforeEach
    void setUp() {
        netconfOps = new NetconfBaseOps(DatabindContext.ofModel(modelContext), rpc);
    }

    @Test
    void testRead() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc).invokeNetconf(any(), any());

        try (var readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(GetConfig.QNAME), any(ContainerNode.class));
            readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(Get.QNAME), any());
        }
    }

    @Test
    void testExists() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc).invokeNetconf(any(), any());
        try (var readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            readOnlyTx.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(GetConfig.QNAME), any());
            readOnlyTx.exists(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(Mockito.eq(Get.QNAME), any());
        }
    }

    @Test
    void testIdentifier() {
        try (var tx1 = new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {
            try (var tx2 = new ReadOnlyTx(netconfOps, new RemoteDeviceId("a",
                    new InetSocketAddress("localhost", 196)))) {
                assertNotEquals(tx1.getIdentifier(), tx2.getIdentifier());
            }
        }
    }
}
