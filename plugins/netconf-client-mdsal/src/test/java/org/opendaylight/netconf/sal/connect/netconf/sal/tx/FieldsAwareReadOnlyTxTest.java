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
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class FieldsAwareReadOnlyTxTest {
    @Mock
    private Rpcs.Normalized rpc;
    @Mock
    private ContainerNode mockedNode;

    @Test
    public void testReadWithFields() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc). invokeNetconf(any(), any());

        final var netconfOps = new NetconfBaseOps(rpc, mock(MountPointContext.class));
        try (var readOnlyTx = new FieldsAwareReadOnlyTx(netconfOps,
                new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {

            readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.empty(),
                List.of(YangInstanceIdentifier.empty()));
            verify(rpc).invokeNetconf(eq(NETCONF_GET_CONFIG_QNAME), any());

            readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.empty());
            verify(rpc).invokeNetconf(eq(NETCONF_GET_QNAME), any());
        }
    }
}