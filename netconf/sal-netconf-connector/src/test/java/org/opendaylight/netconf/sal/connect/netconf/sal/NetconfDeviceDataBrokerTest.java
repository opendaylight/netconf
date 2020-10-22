/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import java.net.InetSocketAddress;
import java.util.Arrays;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.AbstractWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteRunningTx;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.IetfNetconfService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.NetconfTcp;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@RunWith(MockitoJUnitRunner.class)
public class NetconfDeviceDataBrokerTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    @Mock
    private DOMRpcService rpcService;
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
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult())).when(rpcService)
            .invokeRpc(any(QName.class), any(ContainerNode.class));
        dataBroker = getDataBroker(NetconfSessionPreferences.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testNewReadOnlyTransaction() throws Exception {
        final DOMDataTreeReadTransaction tx = dataBroker.newReadOnlyTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeRpc(eq(NETCONF_GET_QNAME), any(ContainerNode.class));
    }

    @Test
    public void testNewReadWriteTransaction() throws Exception {
        final DOMDataTreeReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeRpc(eq(NETCONF_GET_QNAME), any(ContainerNode.class));
    }

    @Test
    public void testWritableRunningCandidateWriteTransaction() throws Exception {
        testWriteTransaction(
                WriteCandidateRunningTx.class, NetconfSessionPreferences.NETCONF_RUNNING_WRITABLE_URI.toString(),
                NetconfSessionPreferences.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testCandidateWriteTransaction() throws Exception {
        testWriteTransaction(WriteCandidateTx.class, NetconfSessionPreferences.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testRunningWriteTransaction() throws Exception {
        testWriteTransaction(WriteRunningTx.class, NetconfSessionPreferences.NETCONF_RUNNING_WRITABLE_URI.toString());
    }

    private void testWriteTransaction(final Class<? extends AbstractWriteTx> transaction,
            final String... capabilities) {
        final NetconfDeviceDataBroker db = getDataBroker(capabilities);
        Assert.assertEquals(transaction, db.newWriteOnlyTransaction().getClass());
    }

    private NetconfDeviceDataBroker getDataBroker(final String... caps) {
        final NetconfSessionPreferences prefs = NetconfSessionPreferences.fromStrings(Arrays.asList(caps));
        final RemoteDeviceId id =
                new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return new NetconfDeviceDataBroker(id, new EmptyMountPointContext(SCHEMA_CONTEXT), rpcService, prefs);
    }

}
