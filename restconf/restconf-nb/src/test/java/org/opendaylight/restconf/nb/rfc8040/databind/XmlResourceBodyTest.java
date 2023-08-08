/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class XmlResourceBodyTest extends AbstractResourceBodyTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");

    private static EffectiveModelContext schemaContext;

    public XmlResourceBodyTest() {
        super(XmlResourceBody::new, schemaContext);
    }

    @BeforeClass
    public static void initialization() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/invoke-rpc"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    /**
     * Test PUT operation when message root element is not the same as the last element in request URI.
     * PUT operation message should always start with schema node from URI otherwise exception should be
     * thrown.
     */
    @Test
    public void wrongRootElementTest() throws Exception {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> parseResource("instance-identifier-module:cont", "/instanceidentifier/xml/bug7933.xml"));

        final var restconfError = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
    }

    /**
     * Test PUT operation when message root element is not the same as the last element in request URI.
     * PUT operation message should always start with schema node from URI otherwise exception should be
     * thrown.
     */
    @Test
    public void wrongRootElementMountPointTest() throws Exception {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> parseResource("instance-identifier-module:cont/yang-ext:mount",
                "/instanceidentifier/xml/bug7933.xml"));
        final var restconfError = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
    }

    @Test
    public void testRangeViolation() throws Exception {
        assertRangeViolation(() -> parse("netconf786:foo", "<foo xmlns=\"netconf786\"><bar>100</bar></foo>"));
    }

    @Test
    public void putXmlTest() throws Exception {
        final var keyName = QName.create(TOP_LEVEL_LIST, "key-leaf");
        assertEquals(Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(TOP_LEVEL_LIST, keyName, "key-value"))
            .withChild(ImmutableNodes.leafNode(keyName, "key-value"))
            .withChild(ImmutableNodes.leafNode(QName.create(keyName, "ordinary-leaf"), "leaf-value"))
            .build(), parseResource("foo:top-level-list=key-value", "/foo-xml-test/foo.xml"));
    }

    @Test
    public void moduleDataTest() throws Exception {
        testModuleData("instance-identifier-module:cont");
    }

    @Test
    public void moduleDataMountPointTest() throws Exception {
        testModuleData("instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont");
    }

    private void testModuleData(final String uriPath) throws Exception {
        final var entryId = NodeIdentifierWithPredicates.of(LST11,
            Map.of(KEYVALUE111, "value1", KEYVALUE112, "value2"));

        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(CONT_NID)
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(CONT1_NID)
                .withChild(Builders.mapBuilder()
                    .withNodeIdentifier(new NodeIdentifier(LST11))
                    .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(entryId)
                        .withChild(ImmutableNodes.leafNode(KEYVALUE111, "value1"))
                        .withChild(ImmutableNodes.leafNode(KEYVALUE112, "value2"))
                        .withChild(ImmutableNodes.leafNode(LF111, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                            new NodeIdentifier(LST11), entryId, LF112_NID)))
                        .withChild(ImmutableNodes.leafNode(LF112_NID, "lf112 value"))
                        .build())
                    .build())
                .build())
            .build(), parseResource(uriPath, "/instanceidentifier/xml/xmldata.xml"));
    }

    @Test
    public void moduleSubContainerDataPutTest() throws Exception {
//        final DataSchemaNode dataSchemaNode = schemaContext
//                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
//        final QName cont1QName = QName.create(dataSchemaNode.getQName(), "cont1");
//        final YangInstanceIdentifier dataII = YangInstanceIdentifier.of(dataSchemaNode.getQName()).node(cont1QName);
//        final DataSchemaNode dataSchemaNodeOnPath = ((DataNodeContainer) dataSchemaNode)
//        .getDataChildByName(cont1QName);
        assertEquals(null, parseResource("instance-identifier-module:cont/cont1",
            "/instanceidentifier/xml/xml_sub_container.xml"));
    }

    @Test
    public void moduleSubContainerDataPutMountPointTest() throws Exception {
//        final DataSchemaNode dataSchemaNode = schemaContext
//                .getDataChildByName(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
        assertEquals(null, parseResource(
            "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1",
            "/instanceidentifier/xml/xml_sub_container.xml"));
    }
}
