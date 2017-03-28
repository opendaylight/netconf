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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

public class Bug8084 {

    private static final QName base = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    public void testValidateTypes() throws Exception {
        final List<InputStream> sources = new ArrayList<>();
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-mod-0.yang"));
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-augment.yang"));
        sources.add(getClass().getResourceAsStream("/yang/mdsal-netconf-mapping-test.yang"));
        final SchemaContext context = YangParserTestUtils.parseYangStreams(sources);
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        final FilterContentValidator validator = new FilterContentValidator(currentContext);

        final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/bug8084.xml"));

        final XmlElement xmlElement = XmlElement.fromDomDocument(document);
        final YangInstanceIdentifier actual = validator.validate(xmlElement, document);

        final Map<QName, Object> inputs = new HashMap<>();
        inputs.put(QName.create(base, "id1"), "aaa");
        inputs.put(QName.create(base, "id2"), Byte.valueOf("-9"));
        inputs.put(QName.create(base, "id3"), Short.valueOf("-30000"));
        inputs.put(QName.create(base, "id4"), Integer.valueOf("-2000000000"));
        inputs.put(QName.create(base, "id5"), Long.valueOf("-2000000000000000"));
        inputs.put(QName.create(base, "id6"), Short.valueOf("9"));
        inputs.put(QName.create(base, "id7"), Integer.valueOf("30000"));
        inputs.put(QName.create(base, "id8"), Long.valueOf("2000000000"));
        inputs.put(QName.create(base, "id9"), BigInteger.valueOf(Long.valueOf("2000000000000000")));
        inputs.put(QName.create(base, "id10"), true);
        inputs.put(QName.create(base, "id11"), BigDecimal.valueOf(128.55));
        inputs.put(QName.create(base, "id12"), QName.create(base, "foo"));
        inputs.put(QName.create(base, "id13"), QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "foo"));
        final QName idActual = (QName) ((YangInstanceIdentifier.NodeIdentifierWithPredicates) actual.getLastPathArgument()).
                getKeyValues().get(QName.create(base, "id12"));


        final YangInstanceIdentifier expected = YangInstanceIdentifier.builder()
                .node(base)
                .node(QName.create(base, "multi-key-list2"))
                .nodeWithKey(QName.create(base, "multi-key-list2"), inputs)
                .build();
        final QName idExpected = (QName) ((YangInstanceIdentifier.NodeIdentifierWithPredicates) expected.getLastPathArgument()).
                getKeyValues().get(QName.create(base, "id12"));
        assertEquals(idExpected, idActual);
        assertEquals(expected, actual);

    }
}
