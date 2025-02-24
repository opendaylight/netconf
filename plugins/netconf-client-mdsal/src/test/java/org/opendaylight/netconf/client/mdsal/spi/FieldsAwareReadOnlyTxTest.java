/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.GetConfig;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class FieldsAwareReadOnlyTxTest {
    @Mock
    private Rpcs.Normalized rpc;
    @Mock
    private ContainerNode mockedNode;
    @Mock
    private EffectiveModelContext modelContext;

    @Test
    void testReadWithFields() {
        doReturn(Futures.immediateFuture(new DefaultDOMRpcResult(mockedNode))).when(rpc). invokeNetconf(any(), any());

        final var netconfOps = new NetconfBaseOps(DatabindContext.ofModel(modelContext), rpc);
        try (var readOnlyTx = new FieldsAwareReadOnlyTx(netconfOps,
                new RemoteDeviceId("a", new InetSocketAddress("localhost", 196)))) {

            readOnlyTx.read(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.of(),
                List.of(YangInstanceIdentifier.of()));
            verify(rpc).invokeNetconf(eq(GetConfig.QNAME), any());

            readOnlyTx.read(LogicalDatastoreType.OPERATIONAL, YangInstanceIdentifier.of());
            verify(rpc).invokeNetconf(eq(Get.QNAME), any());
        }
    }
}
