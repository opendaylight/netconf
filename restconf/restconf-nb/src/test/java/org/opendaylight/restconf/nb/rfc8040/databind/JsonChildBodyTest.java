/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.AbstractBodyReaderTest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class JsonChildBodyTest extends AbstractBodyReaderTest {
    private static EffectiveModelContext schemaContext;

    public JsonChildBodyTest() {
        super(schemaContext);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleSubContainerDataPostTest() throws Exception {
        final var body = new JsonChildBody(
            JsonChildBodyTest.class.getResourceAsStream("/instanceidentifier/json/json_sub_container.json"));
        final var payload = body.toPayload(YangInstanceIdentifier.of(CONT_QNAME),
            Inference.ofDataTreePath(schemaContext, CONT_QNAME));

        final var lflst11 = QName.create("augment:module:leaf:list", "2014-01-27", "lflst11");
        assertEquals(List.of(new NodeIdentifier(CONT1_QNAME)), payload.prefix());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT1_QNAME))
            .withChild(ImmutableNodes.leafNode(QName.create("augment:module:leaf:list", "2014-01-27", "lf11"),
                YangInstanceIdentifier.of(
                    new NodeIdentifier(CONT_QNAME),
                    new NodeIdentifier(CONT1_QNAME),
                    new NodeIdentifier(lflst11),
                    new NodeWithValue<>(lflst11, "lflst11_1"))))
            .build(), payload.body());
    }

    @Test
    public void moduleSubContainerAugmentDataPostTest() throws Exception {
        final var body = new JsonChildBody(
            JsonChildBodyTest.class.getResourceAsStream("/instanceidentifier/json/json_augment_container.json"));
        final var payload = body.toPayload(YangInstanceIdentifier.of(CONT_QNAME),
            Inference.ofDataTreePath(schemaContext, CONT_QNAME));

        final var contAugment = QName.create("augment:module", "2014-01-17", "cont-augment");
        assertEquals(List.of(new NodeIdentifier(contAugment)), payload.prefix());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(contAugment))
            .withChild(ImmutableNodes.leafNode(QName.create(contAugment, "leaf1"), "stryng"))
            .build(), payload.body());
    }

    @Test
    public void moduleSubContainerChoiceAugmentDataPostTest() throws Exception {
        final var body = new JsonChildBody(
            JsonChildBodyTest.class.getResourceAsStream("/instanceidentifier/json/json_augment_choice_container.json"));
        final var payload = body.toPayload(YangInstanceIdentifier.of(new NodeIdentifier(CONT_QNAME)),
            Inference.ofDataTreePath(schemaContext, CONT_QNAME));

        final var container1 = QName.create("augment:module", "2014-01-17", "case-choice-case-container1");
        assertEquals(List.of(
            new NodeIdentifier(QName.create(container1, "augment-choice1")),
            new NodeIdentifier(QName.create(container1, "augment-choice2")),
            new NodeIdentifier(container1)), payload.prefix());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(container1))
            .withChild(ImmutableNodes.leafNode(QName.create(container1, "case-choice-case-leaf1"), "stryng"))
            .build(), payload.body());
    }
}
