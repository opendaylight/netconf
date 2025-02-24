/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMDataBrokerFieldsExtension;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceDataBrokerTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private Rpcs.Normalized rpcService;
    private NetconfDeviceDataBroker dataBroker;

    @BeforeAll
    static void beforeClass() {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfData.class, NetconfTcp.class);
    }

    @AfterAll
    static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @BeforeEach
    void setUp() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpcService).invokeNetconf(any(), any());
        dataBroker = getDataBroker(CapabilityURN.CANDIDATE);
    }

    @Test
    void testNewReadOnlyTransaction() {
        final DOMDataTreeReadTransaction tx = dataBroker.newReadOnlyTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeNetconf(eq(Get.QNAME), any());
    }

    @Test
    void testNewReadWriteTransaction() {
        final DOMDataTreeReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeNetconf(eq(Get.QNAME), any());
    }

    @Test
    void testWritableRunningCandidateWriteTransaction() {
        testWriteTransaction(WriteCandidateRunningTx.class, CapabilityURN.WRITABLE_RUNNING, CapabilityURN.CANDIDATE);
    }

    @Test
    void testCandidateWriteTransaction() {
        testWriteTransaction(WriteCandidateTx.class, CapabilityURN.CANDIDATE);
    }

    @Test
    void testRunningWriteTransaction() {
        testWriteTransaction(WriteRunningTx.class, CapabilityURN.WRITABLE_RUNNING);
    }

    @Test
    void testDOMFieldsExtensions() {
        final var fieldsExtension = dataBroker.extension(NetconfDOMDataBrokerFieldsExtension.class);
        assertNotNull(fieldsExtension);

        // read-only transaction
        final NetconfDOMFieldsReadTransaction roTx = fieldsExtension.newReadOnlyTransaction();
        roTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(),
                List.of(YangInstanceIdentifier.of()));
        verify(rpcService).invokeNetconf(Mockito.eq(GetConfig.QNAME), any());

        // read-write transaction
        final NetconfDOMFieldsReadWriteTransaction rwTx = fieldsExtension.newReadWriteTransaction();
        rwTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of(),
                List.of(YangInstanceIdentifier.of()));
        verify(rpcService).invokeNetconf(Mockito.eq(Get.QNAME), any());
    }

    private void testWriteTransaction(final Class<? extends AbstractWriteTx> transaction,
            final String... capabilities) {
        NetconfDeviceDataBroker db = getDataBroker(capabilities);
        assertEquals(transaction, db.newWriteOnlyTransaction().getClass());
    }

    private NetconfDeviceDataBroker getDataBroker(final String... caps) {
        NetconfSessionPreferences prefs = NetconfSessionPreferences.fromStrings(List.of(caps));
        final RemoteDeviceId id =
                new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return new NetconfDeviceDataBroker(id, DatabindContext.ofModel(SCHEMA_CONTEXT), rpcService, prefs, true);
    }
}
