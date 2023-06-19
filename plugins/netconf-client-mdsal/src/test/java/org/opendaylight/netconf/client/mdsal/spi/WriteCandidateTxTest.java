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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class WriteCandidateTxTest extends AbstractTestModelTest {
    private final RemoteDeviceId id =
        new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));

    @Mock
    private Rpcs.Normalized rpc;
    private NetconfBaseOps netconfOps;

    @Before
    public void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpc).invokeNetconf(any(), any());
        netconfOps = new NetconfBaseOps(rpc, MountPointContext.of(SCHEMA_CONTEXT));
    }

    @Test
    public void testSubmit() throws Exception {
        final WriteCandidateTx tx = new WriteCandidateTx(id, netconfOps, true);
        //check, if lock is called
        verify(rpc).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), any());

        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if both edits are called
        verify(rpc, times(2)).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), any());
        tx.commit().get();
        //check, if unlock is called
        verify(rpc).invokeNetconf(NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME,
                NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        verify(rpc).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME), any());
    }
}