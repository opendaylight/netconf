/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonBodyReaderTest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class JsonOperationInputBodyTest extends AbstractBodyReaderTest {
    private static EffectiveModelContext schemaContext;

    @BeforeClass
    public static void initialization() throws FileNotFoundException {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleSubContainerDataPostActionTest() throws Exception {
        final var stack = SchemaInferenceStack.ofDataTreePath(schemaContext, CONT_QNAME,
            QName.create(CONT_QNAME, "cont1"));
        stack.enterSchemaTree(QName.create(CONT_QNAME, "reset"));

        final var body = new JsonOperationInputBody(
            JsonBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/json_cont_action.json"));
        final var data = body.toContainerNode(stack.toInference());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create(CONT_QNAME, "input")))
            .withChild(ImmutableNodes.leafNode(QName.create(CONT_QNAME, "delay"), Uint32.valueOf(600)))
            .build(), data);
    }
}
