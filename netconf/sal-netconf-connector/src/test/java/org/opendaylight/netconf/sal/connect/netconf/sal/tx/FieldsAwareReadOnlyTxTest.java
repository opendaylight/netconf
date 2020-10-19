/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
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
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import java.net.InetSocketAddress;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@RunWith(MockitoJUnitRunner.class)
public class FieldsAwareReadOnlyTxTest {

    @Mock
    private DOMRpcService rpc;
    @Mock
    private NormalizedNode<?, ?> mockedNode;

    @Before
    public void setUp() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc)
                .invokeRpc(any(QName.class), any(ContainerNode.class));
    }

    @Test
    public void testReadWithFields() {
        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mock(MountPointContext.class));
        final FieldsAwareReadOnlyTx readOnlyTx = new FieldsAwareReadOnlyTx(
                netconfOps, new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)));

        readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.empty(),
                Collections.singletonList(YangInstanceIdentifier.empty()));
        verify(rpc).invokeRpc(Mockito.eq(NETCONF_GET_CONFIG_QNAME), any(ContainerNode.class));

        readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.empty());
        verify(rpc).invokeRpc(Mockito.eq(NETCONF_GET_QNAME), any(ContainerNode.class));
    }
}