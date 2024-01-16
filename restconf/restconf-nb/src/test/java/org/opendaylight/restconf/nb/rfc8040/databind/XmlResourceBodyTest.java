/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
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

class XmlResourceBodyTest extends AbstractResourceBodyTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");

    XmlResourceBodyTest() {
        super(XmlResourceBody::new);
    }

    private void mockMount() {
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(new FixedDOMSchemaService(IID_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(NetconfDataTreeService.class);
    }

    /**
     * Test PUT operation when message root element is not the same as the last element in request URI.
     * PUT operation message should always start with schema node from URI otherwise exception should be
     * thrown.
     */
    @Test
    void wrongRootElementTest() {
        mockMount();

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
            () -> parse(uriPath, """
                <cont1 xmlns="instance:identifier:module"/>"""));
        final var restconfError = ex.getErrors().get(0);
        assertEquals(ErrorType.PROTOCOL, restconfError.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, restconfError.getErrorTag());
        assertEquals(expectedErrorMessage, restconfError.getErrorMessage());
    }

    @Test
    void testRangeViolation() throws Exception {
        assertRangeViolation(() -> parse("netconf786:foo", """
            <foo xmlns="netconf786"><bar>100</bar></foo>"""));
    }

    @Test
    void putXmlTest() throws Exception {
        final var keyName = QName.create(TOP_LEVEL_LIST, "key-leaf");
        assertEquals(Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(TOP_LEVEL_LIST, keyName, "key-value"))
            .withChild(ImmutableNodes.leafNode(keyName, "key-value"))
            .withChild(ImmutableNodes.leafNode(QName.create(keyName, "ordinary-leaf"), "leaf-value"))
            .build(), parse("foo:top-level-list=key-value", """
                <top-level-list xmlns="foo">
                    <key-leaf>key-value</key-leaf>
                    <ordinary-leaf>leaf-value</ordinary-leaf>
                </top-level-list>"""));
    }

    @Test
    void moduleDataTest() throws Exception {
        testModuleData("instance-identifier-module:cont");
    }

    @Test
    void moduleDataMountPointTest() throws Exception {
        mockMount();

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
            .build(), parse(uriPath, """
                <cont xmlns="instance:identifier:module">
                  <cont1>
                    <lst11 xmlns="augment:module" xmlns:c="augment:augment:module">
                      <keyvalue111>value1</keyvalue111>
                      <keyvalue112>value2</keyvalue112>
                      <lf111 xmlns="augment:augment:module" xmlns:a="instance:identifier:module" \
                xmlns:b="augment:module">/a:cont/a:cont1/b:lst11[b:keyvalue111="value1"][b:keyvalue112="value2"]\
                /c:lf112</lf111>
                      <lf112 xmlns="augment:augment:module">lf112 value</lf112>
                    </lst11>
                  </cont1>
                </cont>"""));
    }

    @Test
    void moduleSubContainerDataPutTest() throws Exception {
        testModuleSubContainerDataPut("instance-identifier-module:cont/cont1");
    }

    @Test
    void moduleSubContainerDataPutMountPointTest() throws Exception {
        mockMount();

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
            .build(), parse(uriPath, """
                <cont1 xmlns="instance:identifier:module">
                  <lflst11 xmlns="augment:module:leaf:list">lflst11_1</lflst11>
                  <lflst11 xmlns="augment:module:leaf:list">lflst11_2</lflst11>
                  <lflst11 xmlns="augment:module:leaf:list">lflst11_3</lflst11>
                  <lf11 xmlns:a="instance:identifier:module" xmlns:b="augment:module:leaf:list" \
                xmlns="augment:module:leaf:list">/a:cont/a:cont1/b:lflst11[.="lflst11_1"]</lf11>
                </cont1>"""));
    }

    @Test
    void testMismatchedInput() throws Exception {
        final var error = assertError(() -> parse("base:cont", """
            <restconf-state xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"/>"""));
        assertEquals("""
            Incorrect message root element (urn:ietf:params:xml:ns:yang:ietf-restconf)restconf-state, should be \
            (ns)cont""", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, error.getErrorTag());
    }

    @Test
    void testMissingKeys() throws Exception {
        final var error = assertError(() -> parse("nested-module:depth1-cont/depth2-list2=one,two", """
                <depth2-list2 xmlns="urn:nested:module">
                  <depth3-lf1-key>one</depth3-lf1-key>
                </depth2-list2>"""));
        assertEquals("""
            Error parsing input: List entry (urn:nested:module?revision=2014-06-03)depth2-list2 is missing leaf values \
            for [depth3-lf2-key]""", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.MALFORMED_MESSAGE, error.getErrorTag());
    }
}
