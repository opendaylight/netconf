/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.netconf.AbstractTestModelTest;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.TxTestUtils;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDataTreeServiceImplTest extends AbstractTestModelTest {
    @Mock
    private DOMRpcService rpcService;
    private AbstractNetconfDataTreeService netconService;
    private NetconfMessageTransformer netconfMessageTransformer;
    ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);

    @Before
    public void setUp() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(rpcService)
                .invokeRpc(any(), any());
        netconService = getNetconService();
        final EffectiveModelContext model = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class,
                NetconfState.class);
        netconfMessageTransformer = new NetconfMessageTransformer(new EmptyMountPointContext(model), true,
                BASE_SCHEMAS.getBaseSchema());
    }

    @Test
    public void lock() {
        netconService.lock();
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_LOCK_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void unlock() {
        netconService.unlock();
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_UNLOCK_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void discardChanges() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(rpcService)
                .invokeRpc(any(SchemaPath.class), isNull());
        netconService.discardChanges();
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_DISCARD_CHANGES_QNAME)), isNull());
    }

    @Test
    public void get() {
        netconService.get(null);
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void getConfig() {
        netconService.getConfig(null);
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_GET_CONFIG_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void merge() {
        netconService.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeRpc(eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)),
                captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.getValue());
        Assert.assertTrue(netconfMessage.toString().contains("operation=\"merge\""));
    }

    @Test
    public void replace() {
        netconService.replace(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeRpc(
                eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.getValue());
        Assert.assertTrue(netconfMessage.toString().contains("operation=\"replace\""));
    }

    @Test
    public void create() {
        netconService.create(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeRpc(
                eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.getValue());
        Assert.assertTrue(netconfMessage.toString().contains("operation=\"create\""));
    }

    @Test
    public void delete() {
        netconService.delete(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeRpc(
                eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.getValue());
        Assert.assertTrue(netconfMessage.toString().contains("operation=\"delete\""));
    }

    @Test
    public void remove() {
        netconService.remove(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeRpc(
                eq(SchemaPath.create(true, NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME)), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                toPath(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.getValue());
        Assert.assertTrue(netconfMessage.toString().contains("operation=\"remove\""));
    }

    @Test
    public void commit() {
        List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();
        netconService.commit(resultsFutures);
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_COMMIT_QNAME)), any(ContainerNode.class));
    }

    private AbstractNetconfDataTreeService getNetconService() {
        NetconfSessionPreferences prefs = NetconfSessionPreferences.fromStrings(
                Collections.singletonList(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString()));
        final RemoteDeviceId id =
                new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return AbstractNetconfDataTreeService.of(id, new EmptyMountPointContext(SCHEMA_CONTEXT), rpcService, prefs);
    }
}