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
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.COMMIT_RPC_CONTENT;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
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
import org.opendaylight.netconf.databind.RequestException;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@ExtendWith(MockitoExtension.class)
class NetconfDataTreeServiceImplTest extends AbstractTestModelTest {
    @Mock
    private Rpcs.Normalized rpcService;
    @Captor
    private ArgumentCaptor<ContainerNode> captor;

    private DataStoreService dataStoreService;
    private NetconfMessageTransformer netconfMessageTransformer;

    @BeforeEach
    void setUp() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpcService).invokeNetconf(any(), any());
        dataStoreService = getDataStoreService();
        final var model = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class, NetconfState.class);
        netconfMessageTransformer = new NetconfMessageTransformer(DatabindContext.ofModel(model), true,
            BASE_SCHEMAS.baseSchemaForCapabilities(NetconfSessionPreferences.fromStrings(Set.of())));
    }

    @Test
    void get() {
        dataStoreService.get(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of(), List.of());
        verify(rpcService).invokeNetconf(eq(Get.QNAME), any());
    }

    @Test
    void getConfig() {
        dataStoreService.get(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(), List.of());
        verify(rpcService).invokeNetconf(eq(GetConfig.QNAME), any());
    }

    @Test
    void merge() {
        dataStoreService.merge(TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"merge\""));
    }

    @Test
    void replace() {
        dataStoreService.replace(TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"replace\""));
    }

    @Test
    void create() {
        dataStoreService.create(TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"create\""));
    }

    @Test
    void delete() {
        dataStoreService.delete(TxTestUtils.getLeafId().getParent());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"delete\""));
    }

    @Test
    void remove() {
        dataStoreService.remove(TxTestUtils.getLeafId().getParent());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"remove\""));
    }

    @Test
    void commit() {
        dataStoreService.create(TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"create\""));

        dataStoreService.commit();
        verify(rpcService).invokeNetconf(eq(Commit.QNAME), any());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(Unlock.QNAME), any());
        verify(rpcService, after(2000).never()).invokeNetconf(eq(DiscardChanges.QNAME), any());
    }

    @Test
    void discard() {
        dataStoreService.create(TxTestUtils.getLeafId(), TxTestUtils.getLeafNode());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(EditConfig.QNAME), captor.capture());
        verify(rpcService).invokeNetconf(eq(Lock.QNAME), any());

        final NetconfMessage netconfMessage = netconfMessageTransformer.toRpcRequest(
            EditConfig.QNAME, captor.getValue());
        assertThat(netconfMessage.toString(), containsString("operation=\"create\""));

        doReturn(Futures.immediateFailedFuture(new RequestException("Test Exception"))).when(rpcService)
            .invokeNetconf(Commit.QNAME, COMMIT_RPC_CONTENT);

        dataStoreService.commit();
        verify(rpcService).invokeNetconf(eq(Commit.QNAME), any());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(Unlock.QNAME), any());
        verify(rpcService, timeout(2000)).invokeNetconf(eq(DiscardChanges.QNAME), any());
    }

    private DataStoreService getDataStoreService() {
        final var prefs = NetconfSessionPreferences.fromStrings(List.of(CapabilityURN.CANDIDATE));
        final var id = new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return AbstractDataStore.of(id, TEST_DATABIND, rpcService, prefs, true);
    }
}
