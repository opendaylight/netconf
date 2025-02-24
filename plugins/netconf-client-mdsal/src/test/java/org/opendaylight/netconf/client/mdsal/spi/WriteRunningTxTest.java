/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;

@ExtendWith(MockitoExtension.class)
class WriteRunningTxTest extends AbstractTestModelTest {
    private final RemoteDeviceId id =
        new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));

    @Mock
    private Rpcs.Normalized rpc;
    private NetconfBaseOps netconfOps;

    @BeforeEach
    void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpc).invokeNetconf(any(), any());
        netconfOps = new NetconfBaseOps(TEST_DATABIND, rpc);
    }

    @Test
    void testSubmit() throws Exception {
        final WriteRunningTx tx = new WriteRunningTx(id, netconfOps, true, true);
        tx.init();

        //check, if lock is called
        verify(rpc).invokeNetconf(eq(Lock.QNAME), any());
        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if no edit-config is called before submit
        verify(rpc, never()).invokeNetconf(eq(EditConfig.QNAME), any());
        tx.commit().get();
        //check, if both edits are called
        verify(rpc, times(2)).invokeNetconf(eq(EditConfig.QNAME), any());
        //check, if unlock is called
        verify(rpc).invokeNetconf(eq(Unlock.QNAME), any());
    }
}
