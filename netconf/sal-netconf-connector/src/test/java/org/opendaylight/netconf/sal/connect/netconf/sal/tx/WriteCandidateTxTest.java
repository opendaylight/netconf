/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.netconf.AbstractTestModelTest;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class WriteCandidateTxTest extends AbstractTestModelTest {
    @Mock
    private DOMRpcService rpc;
    private NetconfBaseOps netconfOps;
    private RemoteDeviceId id;

    @Before
    public void setUp() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(rpc).invokeRpc(any(), any());
        netconfOps = new NetconfBaseOps(rpc, new EmptyMountPointContext(SCHEMA_CONTEXT));
        id = new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));
    }

    @Test
    public void testSubmit() throws Exception {
        final WriteCandidateTx tx = new WriteCandidateTx(id, netconfOps, true);
        //check, if lock is called
        verify(rpc).invokeRpc(eq(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), any());

        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if both edits are called
        verify(rpc, times(2)).invokeRpc(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), any());
        tx.commit().get();
        //check, if unlock is called
        verify(rpc).invokeRpc(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME,
                NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        verify(rpc).invokeRpc(eq(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), any());
    }
}