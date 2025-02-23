/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class JsonResourceBodyTest extends AbstractResourceBodyTest {
    JsonResourceBodyTest() {
        super(JsonResourceBody::new);
    }

    @Test
    void moduleDataTest() throws Exception {
        final var entryId = NodeIdentifierWithPredicates.of(LST11,
            Map.of(KEYVALUE111, "value1", KEYVALUE112, "value2"));

        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(CONT_NID)
            .withChild(ImmutableNodes.newContainerBuilder()
                .withNodeIdentifier(CONT1_NID)
                .withChild(ImmutableNodes.newSystemMapBuilder()
                    .withNodeIdentifier(new NodeIdentifier(LST11))
                    .withChild(ImmutableNodes.newMapEntryBuilder()
                        .withNodeIdentifier(entryId)
                        .withChild(ImmutableNodes.leafNode(KEYVALUE111, "value1"))
                        .withChild(ImmutableNodes.leafNode(KEYVALUE112, "value2"))
                        .withChild(ImmutableNodes.leafNode(LF111, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                            new NodeIdentifier(LST11), entryId, LF112_NID)))
                        .withChild(ImmutableNodes.leafNode(LF112_NID, "lf112 value"))
                        .build())
                    .build())
                .build())
            .build(), parse("instance-identifier-module:cont", """
                {
                  "instance-identifier-module:cont": {
                    "cont1": {
                      "augment-module:lst11": [
                        {
                          "keyvalue111":"value1",
                          "keyvalue112":"value2",
                          "augment-augment-module:lf111": "/instance-identifier-module:cont/cont1\
                /augment-module:lst11[keyvalue111=\\"value1\\"][keyvalue112=\\"value2\\"]/augment-augment-module:lf112",
                          "augment-augment-module:lf112": "lf112 value"
                        }
                      ]
                    }
                  }
                }"""));

    }

    @Test
    void moduleSubContainerDataPutTest() throws Exception {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(CONT1_NID)
            .withChild(ImmutableNodes.leafNode(LF11, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                new NodeIdentifier(LFLST11), new NodeWithValue<>(LFLST11, "lflst11_1"))))
            .build(), parse("instance-identifier-module:cont/cont1", """
                {
                  "instance-identifier-module:cont1": {
                    "augment-module-leaf-list:lf11" : "/instance-identifier-module:cont\
                /instance-identifier-module:cont1/augment-module-leaf-list:lflst11[.=\\"lflst11_1\\"]"
                  }
                }"""));
    }

    @Test
    void testRangeViolation() {
        assertRangeViolation(() -> parse("netconf786:foo", """
            {
              "netconf786:foo": {
                "bar": 100
              }
            }"""));
    }

    @Test
    void testMismatchedInput() {
        final var error = assertError(() -> parse("base:cont", """
            {
              "ietf-restconf:restconf-state" : {
              }
            }"""));
        assertEquals(new ErrorMessage("""
            Payload name ((urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring?revision=2017-01-26)restconf-state) is \
            different from identifier name ((ns?revision=2016-02-28)cont)"""), error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, error.tag());
    }

    @Test
    void testMissingKeys() {
        final var error = assertError(() -> parse("nested-module:depth1-cont/depth2-list2=one,two", """
                {
                  "depth2-list2" : {
                    "depth3-lf1-key" : "one"
                  }
                }"""));
        assertEquals(new ErrorMessage("""
            Error parsing input: List entry (urn:nested:module?revision=2014-06-03)depth2-list2 is missing leaf values \
            for [depth3-lf2-key]"""), error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, error.tag());
    }

    @Test
    void testJukeboxBand() throws Exception {
        final var one = QName.create("urn:nested:module", "2014-06-03", "depth3-lf1-key");
        final var two = QName.create("urn:nested:module", "2014-06-03", "depth3-lf2-key");

        assertEquals(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(
                QName.create("urn:nested:module", "2014-06-03", "depth2-list2"), Map.of(one, "one", two, "two")))
            .withChild(ImmutableNodes.leafNode(one, "one"))
            .withChild(ImmutableNodes.leafNode(two, "two"))
            .build(), parse("nested-module:depth1-cont/depth2-list2=one,two", """
            {
              "depth2-list2" : {
                "depth3-lf1-key" : "one",
                "depth3-lf2-key" : "two"
              }
            }"""));
    }

    @Test
    void testBinaryTypeError() {
        final var error = assertError(() -> parse("netconf1268:foo", """
            {
              "netconf1268:foo": {
                "bar": "a"
              }
            }"""));
        assertEquals(new ErrorMessage("Error parsing input: Last unit does not have enough valid bits"),
            error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, error.tag());
    }
}
