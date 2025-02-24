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

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Commit;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.DiscardChanges;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Lock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Unlock;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class NetconfDataTreeServiceImplTest extends AbstractTestModelTest {
    @Mock
    private Rpcs.Normalized rpcService;
    @Captor
    private ArgumentCaptor<ContainerNode> captor;

    private AbstractNetconfDataTreeService netconService;
    private NetconfMessageTransformer netconfMessageTransformer;

    @BeforeEach
    void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpcService).invokeNetconf(any(), any());
        netconService = getNetconService();
        final var model = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class, NetconfState.class);
        netconfMessageTransformer = new NetconfMessageTransformer(DatabindContext.ofModel(model), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
    }

    @Test
    void lock() {
        netconService.lock();
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());
    }

    @Test
    void unlock() {
        netconService.lock();
        netconService.unlock();
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());
        verify(rpcService).invokeNetconf(eq(Unlock.QNAME), any());
    }

    @Test
    void discardChanges() {
        netconService.discardChanges();
        verify(rpcService).invokeNetconf(eq(DiscardChanges.QNAME), any());
    }

    @Test
    void get() {
        netconService.get(null);
        verify(rpcService).invokeNetconf(eq(Get.QNAME), any());
    }

    @Test
    void getConfig() {
        netconService.getConfig(null);
        verify(rpcService).invokeNetconf(eq(GetConfig.QNAME), any());
    }

    @Test
    void merge() {
        netconService.merge(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeNetconf(eq(EditConfig.QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"merge\""));
    }

    @Test
    void replace() {
        netconService.replace(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
                Optional.empty());
        verify(rpcService).invokeNetconf(eq(EditConfig.QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"replace\""));
    }

    @Test
    void create() {
        netconService.create(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId(), TxTestUtils.getLeafNode(),
            Optional.empty());
        verify(rpcService).invokeNetconf(eq(EditConfig.QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"create\""));
    }

    @Test
    void delete() {
        netconService.delete(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeNetconf(eq(EditConfig.QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"delete\""));
    }

    @Test
    void remove() {
        netconService.remove(LogicalDatastoreType.CONFIGURATION, TxTestUtils.getLeafId().getParent());
        verify(rpcService).invokeNetconf(eq(EditConfig.QNAME), captor.capture());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"remove\""));
    }

    @Test
    void commit() {
        netconService.commit();
        verify(rpcService).invokeNetconf(eq(Commit.QNAME), any());
    }

    private AbstractNetconfDataTreeService getNetconService() {
        final var prefs = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var id = new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return AbstractNetconfDataTreeService.of(id, TEST_DATABIND, rpcService, prefs, true);
    }
}
