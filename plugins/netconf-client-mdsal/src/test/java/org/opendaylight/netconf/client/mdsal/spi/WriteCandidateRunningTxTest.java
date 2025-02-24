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
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_TARGET_NODEID;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toId;

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
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class WriteCandidateRunningTxTest extends AbstractTestModelTest {
    @Mock
    private Rpcs.Normalized rpc;
    private NetconfBaseOps netconfOps;
    private RemoteDeviceId id;

    @BeforeEach
    void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpc).invokeNetconf(any(), any());
        netconfOps = new NetconfBaseOps(TEST_DATABIND, rpc);
        id = new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));
    }

    @Test
    void testSubmit() throws Exception {
        final WriteCandidateRunningTx tx = new WriteCandidateRunningTx(id, netconfOps, true, true);
        tx.init();

        //check, if lock is called
        final ContainerNode candidateLock =
                getLockContent(Lock.QNAME, NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID);
        final ContainerNode runningLock =
                getLockContent(Lock.QNAME, NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verify(rpc).invokeNetconf(Lock.QNAME, runningLock);
        verify(rpc).invokeNetconf(Lock.QNAME, candidateLock);
        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if both edits are called
        verify(rpc, times(2)).invokeNetconf(eq(EditConfig.QNAME), any());
        tx.commit().get();
        //check, if unlock is called
        verify(rpc).invokeNetconf(Commit.QNAME, NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        final ContainerNode candidateUnlock = getLockContent(Unlock.QNAME,
                NetconfMessageTransformUtil.NETCONF_RUNNING_NODEID);
        final ContainerNode runningUnlock = getLockContent(Unlock.QNAME,
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_NODEID);
        verify(rpc).invokeNetconf(Unlock.QNAME, candidateUnlock);
        verify(rpc).invokeNetconf(Unlock.QNAME, runningUnlock);
    }

    private static ContainerNode getLockContent(final QName op, final NodeIdentifier datastore) {
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(toId(op))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(NETCONF_TARGET_NODEID)
                .withChild(ImmutableNodes.newChoiceBuilder()
                    .withNodeIdentifier(toId(ConfigTarget.QNAME))
                    .withChild(ImmutableNodes.leafNode(datastore, Empty.value()))
                    .build())
                .build())
            .build();
    }

}
