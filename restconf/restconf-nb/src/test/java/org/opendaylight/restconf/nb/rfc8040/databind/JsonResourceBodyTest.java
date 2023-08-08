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
        final var cont = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final var cont1 = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont1"));
        final var lst11 = QName.create("augment:module", "2014-01-17", "lst11");
        final var keyvalue111 = QName.create(lst11, "keyvalue111");
        final var keyvalue112 = QName.create(lst11, "keyvalue112");
        final var lf111 = QName.create("augment:augment:module", "2014-01-17", "lf111");
        final var entryId = NodeIdentifierWithPredicates.of(lst11,
            Map.of(keyvalue111, "value1", keyvalue112, "value2"));
        final var lf112 = new NodeIdentifier(QName.create(lf111, "lf112"));

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(cont)
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(cont1)
                .withChild(Builders.mapBuilder()
                    .withNodeIdentifier(new NodeIdentifier(lst11))
                    .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(entryId)
                        .withChild(ImmutableNodes.leafNode(keyvalue111, "value1"))
                        .withChild(ImmutableNodes.leafNode(keyvalue112, "value2"))
                        .withChild(ImmutableNodes.leafNode(lf111,
                            YangInstanceIdentifier.of(cont, cont1, new NodeIdentifier(lst11), entryId, lf112)))
                        .withChild(ImmutableNodes.leafNode(lf112, "lf112 value"))
                        .build())
                    .build())
                .build())
            .build(), parseResource("instance-identifier-module:cont", "/instanceidentifier/json/jsondata.json"));
    }

    @Test
    public void moduleSubContainerDataPutTest() throws Exception {
        final var cont = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        final var cont1 = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont1"));
        final var lf11 = QName.create("augment:module:leaf:list", "2014-01-27", "lf11");
        final var lflst11 = QName.create(lf11, "lflst11");

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(cont1)
            .withChild(ImmutableNodes.leafNode(lf11, YangInstanceIdentifier.of(cont, cont1, new NodeIdentifier(lflst11),
                new NodeWithValue<>(lflst11, "lflst11_1"))))
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
