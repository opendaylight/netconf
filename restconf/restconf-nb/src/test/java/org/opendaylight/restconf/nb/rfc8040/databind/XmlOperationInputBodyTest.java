/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class XmlOperationInputBodyTest extends AbstractOperationInputBodyTest {
    @Override
    OperationInputBody moduleSubContainerDataPostActionBody() {
        return new XmlOperationInputBody(stringInputStream("""
            <input xmlns="instance:identifier:module">
              <delay>600</delay>
            </input>"""));
    }

    @Override
    OperationInputBody testEmptyBody() {
        return new XmlOperationInputBody(InputStream.nullInputStream());
    }

    @Test
    public void rpcModuleInputTest() throws Exception {
        final var rpcTest = QName.create("invoke:rpc:module", "2013-12-03", "rpc-test");
        final var stack = SchemaInferenceStack.of(YangParserTestUtils.parseYangResourceDirectory("/invoke-rpc"));
        stack.enterSchemaTree(rpcTest);

        final var body = new XmlOperationInputBody(
            XmlOperationInputBodyTest.class.getResourceAsStream("/invoke-rpc/xml/rpc-input.xml"));

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "input")))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create(rpcTest, "cont")))
                .withChild(ImmutableNodes.leafNode(QName.create(rpcTest, "lf"), "lf-test"))
                .build())
            .build(), body.toContainerNode(stack.toInference()));
    }
}
