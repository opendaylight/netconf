/*
 * Copyright (c) 2021 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Futures;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class Netconf799Test {
    private static final QName CONT_QNAME = QName.create("instance:identifier:module", "2014-01-17", "cont");
    private static final QName CONT1_QNAME = QName.create(CONT_QNAME, "cont1");
    private static final QName RESET_QNAME = QName.create(CONT_QNAME, "reset");

    private static final QName DELAY_QNAME = QName.create(CONT_QNAME, "delay");
    private static final QName INPUT_QNAME = QName.create(CONT_QNAME, "input");
    private static final QName OUTPUT_QNAME = QName.create(CONT_QNAME, "output");
    private static final YangInstanceIdentifier ACTION_YII = YangInstanceIdentifier.of(CONT_QNAME).node(CONT1_QNAME);

    @Test
    public void testInvokeAction() {
        final EffectiveModelContext contextRef =
            YangParserTestUtils.parseYangResourceDirectory("/instanceidentifier/yang");

        final DOMDataBroker mockDataBroker = mock(DOMDataBroker.class);

        final DOMActionService actionService = mock(DOMActionService.class);
        doReturn(Futures.immediateFuture(new SimpleDOMActionResult(
            Builders.containerBuilder().withNodeIdentifier(NodeIdentifier.create(OUTPUT_QNAME)).build())))
            .when(actionService).invokeAction(eq(Absolute.of(CONT_QNAME, CONT1_QNAME, RESET_QNAME)), any(), any());

        final RestconfDataServiceImpl dataService = new RestconfDataServiceImpl(
            () -> DatabindContext.ofModel(contextRef), mockDataBroker, mock(DOMMountPointService.class),
            mock(RestconfStreamsSubscriptionService.class), actionService, new StreamsConfiguration(0, 1, 0, false));

        final var nodeAndStack = DataSchemaContextTree.from(contextRef).enterPath(ACTION_YII).orElseThrow();
        final var node = nodeAndStack.node().dataSchemaNode();
        assertThat(node, instanceOf(ActionNodeContainer.class));
        final var actionNode = ((ActionNodeContainer) node).findAction(RESET_QNAME).orElseThrow();
        final var stack = nodeAndStack.stack();
        stack.enterSchemaTree(RESET_QNAME);

        final var response = dataService.invokeAction(NormalizedNodePayload.of(
            InstanceIdentifierContext.ofPath(stack, actionNode, ACTION_YII, null),
            Builders.containerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(INPUT_QNAME))
                .withChild(ImmutableNodes.leafNode(DELAY_QNAME, Uint32.TEN))
                .build()));

        assertEquals(204, response.getStatus());
        assertNull(response.getEntity());
    }
}
