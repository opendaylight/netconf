/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

public class Bug8084 {

    private static final QName BASE = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    public void testValidateTypes() throws Exception {
        final List<InputStream> sources = new ArrayList<>();
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-mod-0.yang"));
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-augment.yang"));
        sources.add(getClass().getResourceAsStream("/yang/mdsal-netconf-mapping-test.yang"));
        final SchemaContext context = YangParserTestUtils.parseYangResourceDirectory("/yang/");
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        final FilterContentValidator validator = new FilterContentValidator(currentContext);

        final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/bug8084.xml"));

        final XmlElement xmlElement = XmlElement.fromDomDocument(document);
        final YangInstanceIdentifier actual = validator.validate(xmlElement);

        final Map<QName, Object> inputs = new HashMap<>();
        inputs.put(QName.create(BASE, "id1"), "aaa");
        inputs.put(QName.create(BASE, "id2"), Byte.valueOf("-9"));
        inputs.put(QName.create(BASE, "id3"), Short.valueOf("-30000"));
        inputs.put(QName.create(BASE, "id4"), Integer.valueOf("-2000000000"));
        inputs.put(QName.create(BASE, "id5"), Long.valueOf("-2000000000000000"));
        inputs.put(QName.create(BASE, "id6"), Short.valueOf("9"));
        inputs.put(QName.create(BASE, "id7"), Integer.valueOf("30000"));
        inputs.put(QName.create(BASE, "id8"), Long.valueOf("2000000000"));
        inputs.put(QName.create(BASE, "id9"), BigInteger.valueOf(Long.valueOf("2000000000000000")));
        inputs.put(QName.create(BASE, "id10"), true);
        inputs.put(QName.create(BASE, "id11"), BigDecimal.valueOf(128.55));
        inputs.put(QName.create(BASE, "id12"), QName.create(BASE, "foo"));
        inputs.put(
                QName.create(BASE, "id13"),
                QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "foo"));
        final QName idActual = (QName) ((NodeIdentifierWithPredicates) actual.getLastPathArgument())
                .getKeyValues().get(QName.create(BASE, "id12"));

        final YangInstanceIdentifier expected = YangInstanceIdentifier.builder()
                .node(BASE)
                .node(QName.create(BASE, "multi-key-list2"))
                .nodeWithKey(QName.create(BASE, "multi-key-list2"), inputs)
                .build();
        final QName idExpected = (QName) ((NodeIdentifierWithPredicates) expected.getLastPathArgument())
                .getKeyValues().get(QName.create(BASE, "id12"));
        assertEquals(idExpected, idActual);
        assertEquals(expected, actual);
    }
}
