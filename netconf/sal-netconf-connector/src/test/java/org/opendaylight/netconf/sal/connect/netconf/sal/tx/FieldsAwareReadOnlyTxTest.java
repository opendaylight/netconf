/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_PATH;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_PATH;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class FieldsAwareReadOnlyTxTest {
    @Mock
    private DOMRpcService rpc;
    @Mock
    private NormalizedNode<?, ?> mockedNode;

    @Test
    public void testReadWithFields() {
        doReturn(FluentFutures.immediateFluentFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc)
            .invokeRpc(any(SchemaPath.class), any(ContainerNode.class));

        final NetconfBaseOps netconfOps = new NetconfBaseOps(rpc, mock(MountPointContext.class));
        try (FieldsAwareReadOnlyTx readOnlyTx = new FieldsAwareReadOnlyTx(netconfOps,
                new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {

            readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.empty(),
                List.of(YangInstanceIdentifier.empty()));
            verify(rpc).invokeRpc(eq(NETCONF_GET_CONFIG_PATH), any(ContainerNode.class));

            readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.empty());
            verify(rpc).invokeRpc(eq(NETCONF_GET_PATH), any(ContainerNode.class));
        }
    }
}