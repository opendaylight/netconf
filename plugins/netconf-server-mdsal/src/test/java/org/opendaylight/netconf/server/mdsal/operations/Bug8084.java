/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class Bug8084 {
    private static final QName BASE = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    public void testValidateTypes() throws Exception {
        final var context = YangParserTestUtils.parseYangResources(Bug8084.class,
            "/yang/filter-validator-test-mod-0.yang", "/yang/filter-validator-test-augment.yang",
            "/yang/mdsal-netconf-mapping-test.yang");
        final var currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        final var validator = new FilterContentValidator(currentContext);

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
                .put(QName.create(BASE, "id6"), Short.valueOf("9"))
                .put(QName.create(BASE, "id7"), Integer.valueOf("30000"))
                .put(QName.create(BASE, "id8"), Long.valueOf("2000000000"))
                .put(QName.create(BASE, "id9"), BigInteger.valueOf(Long.parseLong("2000000000000000")))
                .put(QName.create(BASE, "id10"), true)
                .put(QName.create(BASE, "id11"), BigDecimal.valueOf(128.55))
                .put(id12, idExpected)
                .put(QName.create(BASE, "id13"),
                    QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "foo"))
                .build())
            .build(), actual);
    }
}
