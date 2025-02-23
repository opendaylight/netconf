/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.netconf.common.DatabindPath.Action;
import org.opendaylight.netconf.common.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.testlib.AbstractInstanceIdentifierTest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

abstract class AbstractOperationInputBodyTest extends AbstractInstanceIdentifierTest {
    private static final NodeIdentifier INPUT_NID = new NodeIdentifier(QName.create(CONT_QNAME, "input"));

    private static Action RESET_PATH;

    @BeforeAll
    static final void setupInference() {
        final var stack = SchemaInferenceStack.ofDataTreePath(IID_SCHEMA, CONT_QNAME, CONT1_QNAME);
        final var action = assertInstanceOf(ActionEffectiveStatement.class, stack.enterSchemaTree(RESET_QNAME));

        RESET_PATH = new Action(IID_DATABIND, stack.toInference(), YangInstanceIdentifier.of(CONT_QNAME, CONT1_QNAME),
            action);
    }

    @Test
    final void moduleSubContainerDataPostActionTest() throws Exception {
        final var body = moduleSubContainerDataPostActionBody();

        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(INPUT_NID)
            .withChild(ImmutableNodes.leafNode(DELAY_QNAME, Uint32.valueOf(600)))
            .build(), body.toContainerNode(RESET_PATH));
    }

    abstract OperationInputBody moduleSubContainerDataPostActionBody();

    @Test
    final void testEmpty() throws Exception {
        final var body = testEmptyBody();
        assertEquals(ImmutableNodes.newContainerBuilder().withNodeIdentifier(INPUT_NID).build(),
            body.toContainerNode(RESET_PATH));
    }

    abstract OperationInputBody testEmptyBody();

    @Test
    final void testRpcModuleInput() throws Exception {
        final var rpcTest = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var modelContext = YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc");
        final var stack = SchemaInferenceStack.of(modelContext);
        final var rpc = assertInstanceOf(RpcEffectiveStatement.class, stack.enterSchemaTree(rpcTest));

        final var body = testRpcModuleInputBody();

        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "input")))
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "cont")))
                .withChild(ImmutableNodes.leafNode(QName.create(rpcTest, "lf"), "lf-test"))
                .build())
            .build(),
            body.toContainerNode(new Rpc(DatabindContext.ofModel(modelContext), stack.toInference(), rpc)));
    }

    abstract OperationInputBody testRpcModuleInputBody();
}
