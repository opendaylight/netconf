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
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class JsonResourceBodyTest extends AbstractResourceBodyTest {
    private static EffectiveModelContext MODEL_CONTEXT;

    public JsonResourceBodyTest() {
        super(JsonResourceBody::new, MODEL_CONTEXT);
    }

    @BeforeClass
    public static void initialization() throws FileNotFoundException {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        MODEL_CONTEXT = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void moduleDataTest() throws Exception {
        final var entryId = NodeIdentifierWithPredicates.of(LST11,
            Map.of(KEYVALUE111, "value1", KEYVALUE112, "value2"));

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(CONT_NID)
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(CONT1_NID)
                .withChild(Builders.mapBuilder()
                    .withNodeIdentifier(new NodeIdentifier(LST11))
                    .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(entryId)
                        .withChild(ImmutableNodes.leafNode(KEYVALUE111, "value1"))
                        .withChild(ImmutableNodes.leafNode(KEYVALUE112, "value2"))
                        .withChild(ImmutableNodes.leafNode(LF111, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                            new NodeIdentifier(LST11), entryId, LF112_NID)))
                        .withChild(ImmutableNodes.leafNode(LF112_NID, "lf112 value"))
                        .build())
                    .build())
                .build())
            .build(), parseResource("instance-identifier-module:cont", "/instanceidentifier/json/jsondata.json"));
    }

    @Test
    public void moduleSubContainerDataPutTest() throws Exception {
        final var lf11 = QName.create("augment:module:leaf:list", "2014-01-27", "lf11");
        final var lflst11 = QName.create(lf11, "lflst11");

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(CONT1_NID)
            .withChild(ImmutableNodes.leafNode(lf11, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                new NodeIdentifier(lflst11), new NodeWithValue<>(lflst11, "lflst11_1"))))
            .build(), parseResource("instance-identifier-module:cont/cont1",
                "/instanceidentifier/json/json_sub_container.json"));
    }

    @Test
    public void testRangeViolation() throws Exception {
        assertRangeViolation(() -> parse("netconf786:foo", """
              {
                "netconf786:foo": {
                  "bar": 100
                }
              }"""));
    }
}
