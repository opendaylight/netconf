/*
 * Copyright (c) 2021 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.Futures;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.nb.rfc8040.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class Netconf799Test extends AbstractInstanceIdentifierTest {
    private static final QName OUTPUT_QNAME = QName.create(CONT_QNAME, "output");

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMActionService actionService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private RestconfStreamsSubscriptionService restconfStreamSubService;

    @Test
    public void testInvokeAction() {
        doReturn(Futures.immediateFuture(new SimpleDOMActionResult(
            Builders.containerBuilder().withNodeIdentifier(NodeIdentifier.create(OUTPUT_QNAME)).build())))
            .when(actionService).invokeAction(eq(Absolute.of(CONT_QNAME, CONT1_QNAME, RESET_QNAME)), any(), any());

        final var dataService = new RestconfDataServiceImpl(() -> DatabindContext.ofModel(IID_SCHEMA),
            new MdsalRestconfServer(dataBroker, rpcService, mountPointService), dataBroker, restconfStreamSubService,
            actionService, new StreamsConfiguration(0, 1, 0, false));

        final var response = dataService.postDataJSON("instance-identifier-module:cont/cont1/reset",
            stringInputStream("""
            {
              "instance-identifier-module:input": {
                "delay": 600
              }
            }"""), null);
        assertEquals(204, response.getStatus());
        assertNull(response.getEntity());
    }
}
