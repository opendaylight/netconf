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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.FileNotFoundException;
import java.util.Optional;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionImplementation;
import org.opendaylight.mdsal.dom.api.DOMActionInstance;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.Cont;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.Cont1;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.Reset;
import org.opendaylight.yang.gen.v1.instance.identifier.module.rev140117.cont.cont1.reset.Input;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class InvokeActionThroughRestconfDataServiceTest {
    private static final QName DELAY_QNAME = QName.create(Input.QNAME, "delay").intern();
    private static final QName INPUT_QNAME = QName.create(Reset.QNAME, "input").intern();
    private static final QName OUTPUT_QNAME = QName.create(Reset.QNAME, "output").intern();
    private static final YangInstanceIdentifier ACTION_YII = YangInstanceIdentifier.of(Cont.QNAME).node(Cont1.QNAME);

    private static EffectiveModelContext contextRef;

    private RestconfDataServiceImpl dataService;

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException {
        contextRef = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/instanceidentifier/yang"));
    }

    @Before
    public void setUp() throws Exception {
        final DOMDataBroker mockDataBroker = mock(DOMDataBroker.class);
        final SchemaContextHandler schemaContextHandler = new SchemaContextHandler(mockDataBroker,
                mock(DOMSchemaService.class));
        schemaContextHandler.onModelContextUpdated(contextRef);
        final DOMRpcRouter domRpcRouter = new DOMRpcRouter();
        domRpcRouter.onModelContextUpdated(schemaContextHandler.get());
        final DOMActionProviderService domActionProviderService = domRpcRouter.getActionProviderService();
        this.dataService = new RestconfDataServiceImpl(schemaContextHandler, mockDataBroker,
                mock(DOMMountPointService.class), mock(RestconfStreamsSubscriptionService.class),
                domRpcRouter.getActionService(), mock(Configuration.class));

        // Register Reset Action
        final DOMDataTreeIdentifier domDataTreeIdentifier = new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL,
                ACTION_YII.getParent());
        domActionProviderService.registerActionImplementation(new ResetActionImpl(),
                DOMActionInstance.of(Absolute.of(Cont.QNAME, Cont1.QNAME, Reset.QNAME), domDataTreeIdentifier));
    }

    @Test
    public void testInvokeAction() {
        final var schemaNode = loadAction(contextRef, Reset.QNAME, ACTION_YII);
        assertTrue(schemaNode.isPresent());
        final var context = new InstanceIdentifierContext<SchemaNode>(ACTION_YII, schemaNode.get(), null, contextRef);
        final var containerNode = getResetActionContainerNode();
        final var response = this.dataService.invokeAction(new NormalizedNodeContext(context, containerNode));
        assertEquals(204, response.getStatus());
        assertNull(response.getEntity());
    }

    private static ContainerNode getResetActionContainerNode() {
        final LeafNode<Object> delay = Builders.leafBuilder()
                .withNodeIdentifier(NodeIdentifier.create(DELAY_QNAME))
                .withValue(Uint32.TEN)
                .build();
        return Builders.containerBuilder()
                .withNodeIdentifier(NodeIdentifier.create(INPUT_QNAME))
                .withChild(delay)
                .build();
    }

    private static final class ResetActionImpl implements DOMActionImplementation {
        @Override
        public ListenableFuture<? extends DOMActionResult> invokeAction(final Absolute type,
                final DOMDataTreeIdentifier path, final ContainerNode input) {
            return Futures.immediateFuture(new SimpleDOMActionResult(Builders.containerBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(OUTPUT_QNAME)).build()));
        }
    }

    private static Optional<ActionDefinition> loadAction(final EffectiveModelContext modelContext,
            final QName actionQName, final YangInstanceIdentifier actionYII) {
        final Optional<DataSchemaContextNode<?>> contextNode = DataSchemaContextTree
                .from(modelContext).findChild(actionYII);

        if (contextNode.isPresent()) {
            final DataSchemaNode dataSchemaNode = contextNode.get().getDataSchemaNode();
            if (dataSchemaNode instanceof ActionNodeContainer) {
                return ((ActionNodeContainer) dataSchemaNode).findAction(actionQName);
            }
        }

        return Optional.empty();
    }
}
