/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_TARGET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toId;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.copy.config.input.target.ConfigTarget;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class WriteCandidateRunningTxTest {
    @Mock
    private DOMRpcService rpc;
    private NetconfBaseOps netconfOps;
    private RemoteDeviceId id;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final SchemaContext schemaContext =
                YangParserTestUtils.parseYangResource("/schemas/test-module.yang");
        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult())).when(rpc).invokeRpc(any(), any());
        netconfOps = new NetconfBaseOps(rpc, schemaContext);
        id = new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));
    }

    @Test
    public void testSubmit() throws Exception {
        final WriteCandidateRunningTx tx = new WriteCandidateRunningTx(id, netconfOps, true);
        //check, if lock is called
        final ContainerNode candidateLock =
                getLockContent(NETCONF_LOCK_QNAME, NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME);
        final ContainerNode runningLock =
                getLockContent(NETCONF_LOCK_QNAME, NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
        verify(rpc).invokeRpc(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), runningLock);
        verify(rpc).invokeRpc(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_LOCK_QNAME), candidateLock);
        tx.put(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getContainerId(), TxTestUtils.getContainerNode());
        tx.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        //check, if both edits are called
        verify(rpc, times(2)).invokeRpc(
                eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), any());
        tx.submit().get();
        //check, if unlock is called
        verify(rpc).invokeRpc(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME),
                NetconfMessageTransformUtil.COMMIT_RPC_CONTENT);
        final ContainerNode candidateUnlock = getLockContent(NETCONF_UNLOCK_QNAME,
                NetconfMessageTransformUtil.NETCONF_RUNNING_QNAME);
        final ContainerNode runningUnlock = getLockContent(NETCONF_UNLOCK_QNAME,
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_QNAME);
        verify(rpc).invokeRpc(SchemaPath.create(true, NETCONF_UNLOCK_QNAME), candidateUnlock);
        verify(rpc).invokeRpc(SchemaPath.create(true, NETCONF_UNLOCK_QNAME), runningUnlock);
    }

    private static ContainerNode getLockContent(final QName op, final QName datastore) {
        final LeafNode<Object> datastoreLeaf = Builders.leafBuilder().withNodeIdentifier(toId(datastore)).build();
        final ChoiceNode choice = Builders.choiceBuilder()
                .withNodeIdentifier(toId(ConfigTarget.QNAME))
                .withChild(datastoreLeaf)
                .build();
        final ContainerNode target = Builders.containerBuilder()
                .withNodeIdentifier(toId(NETCONF_TARGET_QNAME))
                .withChild(choice).build();
        return Builders.containerBuilder()
                .withNodeIdentifier(toId(op))
                .withChild(target)
                .build();
    }

}