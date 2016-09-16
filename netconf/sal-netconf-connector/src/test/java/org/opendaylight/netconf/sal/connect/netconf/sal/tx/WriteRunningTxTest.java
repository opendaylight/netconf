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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import java.io.InputStream;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;

public class WriteRunningTxTest {

    @Mock
    private DOMRpcService rpc;
    private NetconfBaseOps netconfOps;
    private RemoteDeviceId id;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final SchemaContext schemaContext = parseYangStreams(getClass().getResourceAsStream("/schemas/test-module.yang"));
        doReturn(Futures.immediateCheckedFuture(new DefaultDOMRpcResult())).when(rpc).invokeRpc(any(), any());
        netconfOps = new NetconfBaseOps(rpc, schemaContext);
        id = new RemoteDeviceId("device1", InetSocketAddress.createUnresolved("0.0.0.0", 17830));
    }

    @Test
    public void testSubmit() throws Exception {
        final WriteRunningTx tx = new WriteRunningTx(id, netconfOps, true);
        //check, if lock is called
        verify(rpc).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_LOCK_QNAME)), any());
        final QName qName1 = QName.create("test:namespace", "2013-07-22", "c");
        final YangInstanceIdentifier yid1 = YangInstanceIdentifier.builder()
                .node(qName1)
                .build();
        final QName qName2 = QName.create(qName1, "a");
        final YangInstanceIdentifier yid2 = YangInstanceIdentifier.builder()
                .node(qName1)
                .node(qName2)
                .build();
        final ContainerNode data1 = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(qName1))
                .build();
        final LeafNode<String> data2 = Builders.<String>leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(qName2))
                .withValue("data")
                .build();
        tx.put(LogicalDatastoreType.CONFIGURATION, yid1, data1);
        tx.merge(LogicalDatastoreType.CONFIGURATION, yid2, data2);
        //check, if no edit-config is called before submit
        verify(rpc, never()).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), any());
        tx.submit().get();
        //check, if both edits are called
        verify(rpc, times(2)).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), any());
        //check, if unlock is called
        verify(rpc).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME)), any());
    }

    private static SchemaContext parseYangStreams(final InputStream... streams) {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        final SchemaContext schemaContext;
        try {
            schemaContext = reactor.buildEffective(ImmutableList.copyOf(streams));
        } catch (final ReactorException e) {
            throw new RuntimeException("Unable to build schema context from " + streams, e);
        }
        return schemaContext;
    }
}