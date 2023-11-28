/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.OperationsPostPath;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

abstract class AbstractOperationInputBodyTest extends AbstractInstanceIdentifierTest {
    private static final NodeIdentifier INPUT_NID = new NodeIdentifier(QName.create(CONT_QNAME, "input"));

    private static OperationsPostPath RESET_PATH;

    @BeforeClass
    public static final void setupInference() {
        final var stack = SchemaInferenceStack.ofDataTreePath(IID_SCHEMA, CONT_QNAME, CONT1_QNAME);
        stack.enterSchemaTree(RESET_QNAME);
        RESET_PATH = new OperationsPostPath(IID_DATABIND, stack.toInference());
    }

    @Test
    public final void moduleSubContainerDataPostActionTest() throws Exception {
        final var body = moduleSubContainerDataPostActionBody();

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(INPUT_NID)
            .withChild(ImmutableNodes.leafNode(DELAY_QNAME, Uint32.valueOf(600)))
            .build(), body.toContainerNode(RESET_PATH));
    }

    abstract OperationInputBody moduleSubContainerDataPostActionBody();

    @Test
    public final void testEmpty() throws Exception {
        final var body = testEmptyBody();
        assertEquals(Builders.containerBuilder().withNodeIdentifier(INPUT_NID).build(),
            body.toContainerNode(RESET_PATH));
    }

    abstract OperationInputBody testEmptyBody();

    @Test
    public final void testRpcModuleInput() throws Exception {
        final var rpcTest = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var modelContext = YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc");
        final var stack = SchemaInferenceStack.of(modelContext);
        stack.enterSchemaTree(rpcTest);

        final var body = testRpcModuleInputBody();

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "input")))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "cont")))
                .withChild(ImmutableNodes.leafNode(QName.create(rpcTest, "lf"), "lf-test"))
                .build())
            .build(),
            body.toContainerNode(new OperationsPostPath(DatabindContext.ofModel(modelContext), stack.toInference())));
    }

    abstract OperationInputBody testRpcModuleInputBody();
}
