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
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

public class XmlResourceBodyTest extends AbstractResourceBodyTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");

    public XmlResourceBodyTest() {
        super(XmlResourceBody::new);
    }

    /**
     * Test PUT operation when message root element is not the same as the last element in request URI.
     * PUT operation message should always start with schema node from URI otherwise exception should be
     * thrown.
     */
    @Test
    public void wrongRootElementTest() {
        assertThrowsException("",
            "Incorrect message root element (instance:identifier:module)cont1, should be "
                + "(urn:ietf:params:xml:ns:yang:ietf-restconf)data");
        assertThrowsException("instance-identifier-module:cont/yang-ext:mount",
            "Incorrect message root element (instance:identifier:module)cont1, should be "
                + "(urn:ietf:params:xml:ns:yang:ietf-restconf)data");
        assertThrowsException("instance-identifier-module:cont",
            "Incorrect message root element (instance:identifier:module)cont1, should be "
                + "(instance:identifier:module)cont");
        assertThrowsException("instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont",
            "Incorrect message root element (instance:identifier:module)cont1, should be "
                + "(instance:identifier:module)cont");
    }

    private void assertThrowsException(final String uriPath, final String expectedErrorMessage) {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> parseResource(uriPath, "/instanceidentifier/xml/bug7933.xml"));
        final var restconfError = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(expectedErrorMessage, restconfError.getErrorMessage());
    }

    @Test
    public void testRangeViolation() throws Exception {
        assertRangeViolation(() -> parse("netconf786:foo", """
            <foo xmlns="netconf786"><bar>100</bar></foo>"""));
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
        testModuleSubContainerDataPut("instance-identifier-module:cont/cont1");
    }

    @Test
    public void moduleSubContainerDataPutMountPointTest() throws Exception {
        testModuleSubContainerDataPut(
            "instance-identifier-module:cont/yang-ext:mount/instance-identifier-module:cont/cont1");
    }

    private void testModuleSubContainerDataPut(final String uriPath) throws Exception {
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(CONT1_NID)
            .withChild(Builders.leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LFLST11))
                .withChild(Builders.leafSetEntryBuilder()
                    .withNodeIdentifier(new NodeWithValue<>(LFLST11, "lflst11_3"))
                    .withValue("lflst11_3")
                    .build())
                .withChild(Builders.leafSetEntryBuilder()
                    .withNodeIdentifier(new NodeWithValue<>(LFLST11, "lflst11_1"))
                    .withValue("lflst11_1")
                    .build())
                .withChild(Builders.leafSetEntryBuilder()
                    .withNodeIdentifier(new NodeWithValue<>(LFLST11, "lflst11_2"))
                    .withValue("lflst11_2")
                    .build())
                .build())
            .withChild(ImmutableNodes.leafNode(LF11, YangInstanceIdentifier.of(CONT_NID, CONT1_NID,
                new NodeIdentifier(LFLST11), new NodeWithValue<>(LFLST11, "lflst11_1"))))
            .build(), parseResource(uriPath, "/instanceidentifier/xml/xml_sub_container.xml"));
    }
}
