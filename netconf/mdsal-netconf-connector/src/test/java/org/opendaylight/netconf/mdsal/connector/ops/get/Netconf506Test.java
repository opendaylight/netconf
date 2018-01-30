/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
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

public class Netconf506Test {

    private static final QName BASE = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    public void testValidateTypes() throws Exception {
        final List<InputStream> sources = new ArrayList<>();
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-mod-0.yang"));
        sources.add(getClass().getResourceAsStream("/yang/mdsal-netconf-mapping-test.yang"));
        final SchemaContext context = YangParserTestUtils.parseYangStreams(sources);
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        final FilterContentValidator validator = new FilterContentValidator(currentContext);

        final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/netconf506.xml"));

        final XmlElement xmlElement = XmlElement.fromDomDocument(document);
        final YangInstanceIdentifier actual = validator.validate(xmlElement);

        final Map<QName, Object> inputs = new HashMap<>();
        inputs.put(QName.create(BASE, "name"), "foo");

        final YangInstanceIdentifier expected = YangInstanceIdentifier.builder()
                .node(BASE)
                .node(QName.create(BASE, "leafref-key-list"))
                .nodeWithKey(QName.create(BASE, "leafref-key-list"), inputs)
                .build();
        assertEquals(expected, actual);
    }
}
