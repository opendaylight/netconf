/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class Bug8084 {
    private static final QName BASE = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    void testValidateTypes() throws Exception {
        final var databind = DatabindContext.ofModel(YangParserTestUtils.parseYangResources(Bug8084.class,
            "/yang/filter-validator-test-mod-0.yang", "/yang/filter-validator-test-augment.yang",
            "/yang/mdsal-netconf-mapping-test.yang"));
        final var validator = new FilterContentValidator(() -> databind);

        final var document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/bug8084.xml"));

        final var xmlElement = XmlElement.fromDomDocument(document);
        final var actual = validator.validate(xmlElement);

        final var id12 = QName.create(BASE, "id12");
        final var idExpected = QName.create(BASE, "foo");
        final var idActual = (QName) ((NodeIdentifierWithPredicates) actual.getLastPathArgument()).getValue(id12);
        assertEquals(idExpected, idActual);

        assertEquals(YangInstanceIdentifier.builder()
            .node(BASE)
            .node(QName.create(BASE, "multi-key-list2"))
            .nodeWithKey(QName.create(BASE, "multi-key-list2"), ImmutableMap.<QName, Object>builder()
                .put(QName.create(BASE, "id1"), "aaa")
                .put(QName.create(BASE, "id2"), Byte.valueOf("-9"))
                .put(QName.create(BASE, "id3"), Short.valueOf("-30000"))
                .put(QName.create(BASE, "id4"), Integer.valueOf("-2000000000"))
                .put(QName.create(BASE, "id5"), Long.valueOf("-2000000000000000"))
                .put(QName.create(BASE, "id6"), Uint8.valueOf(9))
                .put(QName.create(BASE, "id7"), Uint16.valueOf(30000))
                .put(QName.create(BASE, "id8"), Uint32.valueOf(2000000000))
                .put(QName.create(BASE, "id9"), Uint64.valueOf(2000000000000000L))
                .put(QName.create(BASE, "id10"), true)
                .put(QName.create(BASE, "id11"), Decimal64.valueOf("128.55"))
                .put(id12, idExpected)
                .put(QName.create(BASE, "id13"),
                    QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "foo"))
                .build())
            .build(), actual);
    }
}
