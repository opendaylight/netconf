/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_LOCK_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDataTreeServiceImplTest extends AbstractTestModelTest {
    @Mock
    private Rpcs.Normalized rpcService;
    @Captor
    private ArgumentCaptor<ContainerNode> captor;

    private AbstractNetconfDataTreeService netconService;
    private NetconfMessageTransformer netconfMessageTransformer;

    @Before
    public void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpcService).invokeNetconf(any(), any());
        netconService = getNetconService();
        final var model = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class, NetconfState.class);
        netconfMessageTransformer = new NetconfMessageTransformer(MountPointContext.of(model), true,
                BASE_SCHEMAS.getBaseSchema());
    }

    @Test
    public void lock() {
        netconService.lock();
        verify(rpcService).invokeNetconf(eq(NETCONF_LOCK_QNAME), any());
    }

    @Test
    public void unlock() {
        netconService.lock();
        netconService.unlock();
        verify(rpcService).invokeNetconf(eq(NETCONF_LOCK_QNAME), any());
        verify(rpcService).invokeNetconf(eq(NETCONF_UNLOCK_QNAME), any());
    }

    @Test
    public void discardChanges() {
        netconService.discardChanges();
        verify(rpcService).invokeNetconf(eq(NETCONF_DISCARD_CHANGES_QNAME), any());
    }

    @Test
    public void get() {
        netconService.get(null);
        verify(rpcService).invokeNetconf(eq(NETCONF_GET_QNAME), any());
    }

    @Test
    public void getConfig() {
        netconService.getConfig(null);
        verify(rpcService).invokeNetconf(eq(NETCONF_GET_CONFIG_QNAME), any());
    }

    @Test
    public void merge() {
        netconService.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"merge\""));
    }

    @Test
    public void replace() {
        netconService.replace(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"replace\""));
    }

    @Test
    public void create() {
        netconService.create(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"create\""));
    }

    @Test
    public void delete() {
        netconService.delete(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"delete\""));
    }

    @Test
    public void remove() {
        netconService.remove(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeNetconf(eq(NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
                NetconfMessageTransformUtil.NETCONF_EDIT_CONFIG_QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"remove\""));
    }

    @Test
    public void commit() {
        netconService.commit();
        verify(rpcService).invokeNetconf(eq(NETCONF_COMMIT_QNAME), any());
    }

    private AbstractNetconfDataTreeService getNetconService() {
        NetconfSessionPreferences prefs = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final RemoteDeviceId id =
                new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return AbstractNetconfDataTreeService.of(id, MountPointContext.of(SCHEMA_CONTEXT), rpcService, prefs,
            true);
    }
}
