/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.AbstractWriteTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.extension.rev131210.$YangModuleInfoImpl;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NetconfDeviceDataBrokerTest {

    @Mock
    private DOMRpcService rpcService;
    private SchemaContext schemaContext;
    private NetconfDeviceDataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(
                Lists.newArrayList(
                        $YangModuleInfoImpl.getInstance(),
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.$YangModuleInfoImpl.getInstance()));
        schemaContext = moduleInfoBackedContext.tryToCreateSchemaContext().get();
        final DOMRpcResult result = new DefaultDOMRpcResult();
        when(rpcService.invokeRpc(any(SchemaPath.class), any(NormalizedNode.class))).thenReturn(Futures.<DOMRpcResult, DOMRpcException>immediateCheckedFuture(result));

        dataBroker = getDataBroker(NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testNewReadOnlyTransaction() throws Exception {
        final DOMDataReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void testNewReadWriteTransaction() throws Exception {
        final DOMDataReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        tx.read(LogicalDatastoreType.OPERATIONAL, null);
        verify(rpcService).invokeRpc(eq(toPath(NETCONF_GET_QNAME)), any(ContainerNode.class));
    }

    @Test
    public void testWritableRunningCandidateWriteTransaction() throws Exception {
        testWriteTransaction(WriteCandidateRunningTx.class, NetconfMessageTransformUtil.NETCONF_RUNNING_WRITABLE_URI.toString(),
                NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testCandidateWriteTransaction() throws Exception {
        testWriteTransaction(WriteCandidateTx.class, NetconfMessageTransformUtil.NETCONF_CANDIDATE_URI.toString());
    }

    @Test
    public void testRunningWriteTransaction() throws Exception {
        testWriteTransaction(WriteRunningTx.class, NetconfMessageTransformUtil.NETCONF_RUNNING_WRITABLE_URI.toString());
    }

    private void testWriteTransaction(final Class<? extends AbstractWriteTx> transaction, final String... capabilities) {
        final NetconfDeviceDataBroker db = getDataBroker(capabilities);
        Assert.assertEquals(transaction, db.newWriteOnlyTransaction().getClass());
    }

    private NetconfDeviceDataBroker getDataBroker(final String... caps) {
        final NetconfSessionPreferences prefs = NetconfSessionPreferences.fromStrings(Arrays.asList(caps));
        final RemoteDeviceId id = new RemoteDeviceId("device-1", InetSocketAddress.createUnresolved("localhost", 17830));
        return new NetconfDeviceDataBroker(id, schemaContext, rpcService, prefs);
    }

}