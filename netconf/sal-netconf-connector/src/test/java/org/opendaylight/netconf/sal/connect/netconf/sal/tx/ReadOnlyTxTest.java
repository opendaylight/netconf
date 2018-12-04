/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.InetSocketAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.impl.util.compat.DataNormalizationException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class ReadOnlyTxTest {

    private static final YangInstanceIdentifier PATH = YangInstanceIdentifier.create();

    @Mock
    private DOMRpcService rpc;
    @Mock
    private NormalizedNode<?, ?> mockedNode;

    @Before
    public void setUp() throws DataNormalizationException {
        MockitoAnnotations.initMocks(this);
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc)
                .invokeRpc(any(SchemaPath.class), any(NormalizedNode.class));
        doReturn("node").when(mockedNode).toString();
    }

    @Test
    public void testRead() throws Exception {
        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mock(SchemaContext.class));

        final ReadOnlyTx readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)));

        readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create());
        verify(rpc).invokeRpc(Mockito.eq(NetconfMessageTransformUtil.toPath(
                NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME)), any(NormalizedNode.class));
        readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, PATH);
        verify(rpc).invokeRpc(Mockito.eq(NetconfMessageTransformUtil.toPath(
                NetconfMessageTransformUtil.NETCONF_GET_QNAME)), any(NormalizedNode.class));
    }

    @Test
    public void testExists() throws Exception {
        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mock(SchemaContext.class));

        final ReadOnlyTx readOnlyTx =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)));

        readOnlyTx.exists(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create());
        verify(rpc).invokeRpc(Mockito.eq(NetconfMessageTransformUtil.toPath(
                NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME)), any(NormalizedNode.class));
        readOnlyTx.exists(LogicalDatastoreType.OPERATIONAL, PATH);
        verify(rpc).invokeRpc(Mockito.eq(NetconfMessageTransformUtil.toPath(
                NetconfMessageTransformUtil.NETCONF_GET_QNAME)), any(NormalizedNode.class));
    }

    @Test
    public void testIdentifier() throws Exception {
        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mock(SchemaContext.class));
        final ReadOnlyTx tx1 =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)));
        final ReadOnlyTx tx2 =
                new ReadOnlyTx(netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)));
        Assert.assertNotEquals(tx1.getIdentifier(), tx2.getIdentifier());
    }
}