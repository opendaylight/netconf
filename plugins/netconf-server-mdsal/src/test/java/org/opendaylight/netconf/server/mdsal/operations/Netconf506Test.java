/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class Netconf506Test {
    private static final QName BASE = QName.create("urn:dummy:mod-0", "2016-03-01", "mainroot");

    @Test
    void testValidateTypes() throws Exception {
        final var databind = DatabindContext.ofModel(YangParserTestUtils.parseYangResources(Bug8084.class,
                "/yang/filter-validator-test-mod-0.yang", "/yang/mdsal-netconf-mapping-test.yang"));
        final var validator = new FilterContentValidator(() -> databind);

        final var document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class
                .getResourceAsStream("/filter/netconf506.xml"));

        final var xmlElement = XmlElement.fromDomDocument(document);
        final var actual = validator.validate(xmlElement);

        assertEquals(YangInstanceIdentifier.builder()
            .node(BASE)
            .node(QName.create(BASE, "leafref-key-list"))
            .nodeWithKey(QName.create(BASE, "leafref-key-list"), QName.create(BASE, "name"), "foo")
            .build(), actual);
    }
}
