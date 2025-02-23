/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
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
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Addresses issue with list key of type instance-identifier being not recognized properly.
 */
class NC1214Test {
    private static final String MODULE1 = """
        module module1 {
            yang-version 1;
            namespace "urn:test:module1";
            prefix "module1";
            revision "2024-05-06";
            typedef ref-type {
                type instance-identifier;
            }
            container root {
                list items {
                    key name;
                    leaf name {
                        type string;
                    }
                }
            }
        }
        """;
    private static final String MODULE2 = """
        module module2 {
            yang-version 1;
            namespace "urn:test:module2";
            prefix "module2";
            revision "2024-05-06";
            import module1 {
                prefix "m1";
                revision-date "2024-05-06";
            }
            container root {
                list reference {
                    key ref;
                    leaf ref {
                        type m1:ref-type;
                    }
                }
            }
        }
        """;
    private static final String XML = """
        <root xmlns="urn:test:module2">
            <reference>
                <ref xmlns:a="urn:test:module1">/a:root/a:items[a:name='test']</ref>
            </reference>
        </root>
        """;
    private static final QNameModule M1 = QNameModule.ofRevision("urn:test:module1", "2024-05-06");
    private static final QNameModule M2 = QNameModule.ofRevision("urn:test:module2", "2024-05-06");
    private static final QName M1_ITEMS = QName.create(M1, "items");
    private static final QName M2_REF = QName.create(M2, "reference");

    @Test
    void instanceIdentifierReference() throws Exception {
        final var databind = DatabindContext.ofModel(YangParserTestUtils.parseYang(MODULE1, MODULE2));

        final var xmlElement = XmlElement.fromDomDocument(XmlUtil.readXmlToDocument(XML));
        final var validator = new FilterContentValidator(() -> databind);
        final var expected = YangInstanceIdentifier.builder()
            .node(QName.create(M2, "root"))
            .node(M2_REF)
            .nodeWithKey(M2_REF, QName.create(M2, "ref"), YangInstanceIdentifier.builder()
                .node(QName.create(M1, "root"))
                .node(M1_ITEMS)
                .nodeWithKey(M1_ITEMS, QName.create(M1, "name"), "test")
                .build()
            ).build();

        assertEquals(expected, validator.validate(xmlElement));
    }
}
