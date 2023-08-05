/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.Assert.assertTrue;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.toId;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.custommonkey.xmlunit.Diff;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class FieldsSchemalessRpcStructureTransformerTest {

    /**
     * Sample YANG structure.<br>
     * container c-1 {
     *     leaf leaf-1 {
     *         type string;
     *     }
     *     list list-1 {
     *         leaf key-1 {
     *             type string;
     *         }
     *         leaf key-2 {
     *             type string;
     *         }
     *         container c-2 {
     *             leaf leaf-3 {
     *                 type string;
     *             }
     *             leaf leaf-4 {
     *                 type string;
     *             }
     *         }
     *     }
     * }
     * container c-x {
     *     leaf l-x {
     *         type boolean;
     *     }
     *     leaf l-y {
     *         type boolean;
     *     }
     * }
     */
    private static final QNameModule TEST_MODULE = QNameModule.create(
            XMLNamespace.of("test-namespace"), Revision.of("2020-10-25"));

    private static final NodeIdentifier C1_NID = toId(QName.create(TEST_MODULE, "c-1"));
    private static final NodeIdentifier C2_NID = toId(QName.create(TEST_MODULE, "c-2"));
    private static final NodeIdentifier CX_NID = toId(QName.create(TEST_MODULE, "c-x"));

    private static final NodeIdentifier LEAF1_NID = toId(QName.create(TEST_MODULE, "leaf-1"));
    private static final NodeIdentifier LEAF2_NID = toId(QName.create(TEST_MODULE, "leaf-2"));
    private static final NodeIdentifier LEAF3_NID = toId(QName.create(TEST_MODULE, "leaf-3"));
    private static final NodeIdentifier LX_NID = toId(QName.create(TEST_MODULE, "l-x"));

    private static final QName KEY1_QNAME = QName.create(TEST_MODULE, "key-1");
    private static final QName KEY2_QNAME = QName.create(TEST_MODULE, "key-2");
    private static final NodeIdentifier KEY1_NID = toId(KEY1_QNAME);

    private static final QName LIST1_QNAME = QName.create(TEST_MODULE, "list-1");
    private static final NodeIdentifier LIST1_NID = toId(LIST1_QNAME);

    private final SchemalessRpcStructureTransformer transformer = new SchemalessRpcStructureTransformer();

    @Test
    public void toFilterStructureWithSingleRootTest() throws SAXException, IOException, URISyntaxException {
        final YangInstanceIdentifier rootPath = YangInstanceIdentifier.of(C1_NID);
        final YangInstanceIdentifier leaf1Field = YangInstanceIdentifier.of(LEAF1_NID);
        final YangInstanceIdentifier leaf3Field = YangInstanceIdentifier.of(NodeIdentifierWithPredicates.of(
                LIST1_QNAME, Map.of(KEY1_QNAME, "key1", KEY2_QNAME, "key2")), C2_NID, LEAF3_NID);
        final YangInstanceIdentifier key1Field = YangInstanceIdentifier.of(NodeIdentifierWithPredicates.of(
                LIST1_QNAME, Map.of(KEY1_QNAME, "key1", KEY2_QNAME, "key2")), KEY1_NID);
        final FieldsFilter filter = FieldsFilter.of(rootPath, List.of(leaf1Field, leaf3Field, key1Field));

        final DOMSourceAnyxmlNode filterStructure = (DOMSourceAnyxmlNode) transformer.toFilterStructure(
                Collections.singletonList(filter));
        final Diff diff = getDiff("one-root-filter.xml", filterStructure);
        assertTrue(diff.similar());
    }

    @Test
    public void toFilterStructureWithTwoRootContainersTest() throws SAXException, IOException, URISyntaxException {
        final YangInstanceIdentifier c1RootPath = YangInstanceIdentifier.of(C1_NID);
        final YangInstanceIdentifier cxRootPath = YangInstanceIdentifier.of(CX_NID);
        final YangInstanceIdentifier c2Field = YangInstanceIdentifier.of(LIST1_NID, C2_NID);
        final YangInstanceIdentifier leaf2Field = YangInstanceIdentifier.of(LIST1_NID, C2_NID, LEAF2_NID);
        final YangInstanceIdentifier lxField = YangInstanceIdentifier.of(LX_NID);

        final FieldsFilter filter1 = FieldsFilter.of(c1RootPath, List.of(c2Field, leaf2Field));
        final FieldsFilter filter2 = FieldsFilter.of(cxRootPath, Collections.singletonList(lxField));
        final DOMSourceAnyxmlNode filterStructure = (DOMSourceAnyxmlNode) transformer.toFilterStructure(
                List.of(filter1, filter2));
        final Diff diff = getDiff("two-roots-filter.xml", filterStructure);
        assertTrue(diff.similar());
    }

    private static Diff getDiff(final String filterFileName, final DOMSourceAnyxmlNode filterStructure)
            throws IOException, SAXException, URISyntaxException {
        final String body = XmlUtil.toString((Element) filterStructure.body().getNode());
        final String expectedBody = new String(Files.readAllBytes(Paths.get(FieldsSchemalessRpcStructureTransformerTest
                .class.getResource("/schemaless/filter/" + filterFileName).toURI())));
        return new Diff(expectedBody, body);
    }
}