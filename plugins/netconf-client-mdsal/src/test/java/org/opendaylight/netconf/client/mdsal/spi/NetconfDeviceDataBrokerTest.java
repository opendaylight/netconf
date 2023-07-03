/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMDataBrokerFieldsExtension;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadTransaction;
import org.opendaylight.netconf.dom.api.tx.NetconfDOMFieldsReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.monitoring.rev220718.NetconfTcp;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.class)
public class NetconfDeviceDataBrokerTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private Rpcs.Normalized rpcService;
    private NetconfDeviceDataBroker dataBroker;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = BindingRuntimeHelpers.createEffectiveModel(IetfNetconfService.class, NetconfTcp.class);
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult())).when(rpcService).invokeNetconf(any(), any());
        dataBroker = getDataBroker(CapabilityURN.CANDIDATE);
    }

    @Test
    public void testNewReadOnlyTransaction() {
        final DOMDataTreeReadTransaction tx = dataBroker.newReadOnlyTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeNetconf(eq(NETCONF_GET_QNAME), any());
    }

    @Test
    public void testNewReadWriteTransaction() {
        final DOMDataTreeReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeNetconf(eq(NETCONF_GET_QNAME), any());
    }

    @Test
    public void testWritableRunningCandidateWriteTransaction() {
        testWriteTransaction(WriteCandidateRunningTx.class, CapabilityURN.WRITABLE_RUNNING, CapabilityURN.CANDIDATE);
    }

    @Test
    public void testCandidateWriteTransaction() {
        testWriteTransaction(WriteCandidateTx.class, CapabilityURN.CANDIDATE);
    }

    @Test
    public void testRunningWriteTransaction() {
        testWriteTransaction(WriteRunningTx.class, CapabilityURN.WRITABLE_RUNNING);
    }

    @Test
    public void testDOMFieldsExtensions() {
        final NetconfDOMDataBrokerFieldsExtension fieldsExtension = dataBroker.getExtensions().getInstance(
                NetconfDOMDataBrokerFieldsExtension.class);
        assertNotNull(fieldsExtension);

        // read-only transaction
        final NetconfDOMFieldsReadTransaction roTx = fieldsExtension.newReadOnlyTransaction();
        roTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(),
                List.of(YangInstanceIdentifier.of()));
        verify(rpcService).invokeNetconf(Mockito.eq(NETCONF_GET_CONFIG_QNAME), any());

        // read-write transaction
        final NetconfDOMFieldsReadWriteTransaction rwTx = fieldsExtension.newReadWriteTransaction();
        rwTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of(),
                List.of(YangInstanceIdentifier.of()));
        verify(rpcService).invokeNetconf(Mockito.eq(NETCONF_GET_QNAME), any());
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
        return new NetconfDeviceDataBroker(id, MountPointContext.of(SCHEMA_CONTEXT), rpcService, prefs, true);
    }
}
