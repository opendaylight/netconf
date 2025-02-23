/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class XmlChildBodyTest extends AbstractBodyTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");

    private static Data EMPTY_PATH;
    private static Data CONT_PATH;

    @BeforeAll
    static void beforeAll() throws Exception {
        final var testFiles = loadFiles("/instance-identifier");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        final var modelContext = YangParserTestUtils.parseYangFiles(testFiles);

        final var contPath = YangInstanceIdentifier.of(CONT_QNAME);
        final var databind = DatabindContext.ofModel(modelContext);
        final var nodeAndStack = databind.schemaTree().enterPath(contPath).orElseThrow();
        CONT_PATH = new Data(databind, nodeAndStack.stack().toInference(), contPath, nodeAndStack.node());
        EMPTY_PATH = new Data(DatabindContext.ofModel(modelContext));
    }

    @Test
    void postXmlTest() throws Exception {
        final var body = new XmlChildBody(stringInputStream("""
            <top-level-list xmlns="foo">
                <key-leaf>key-value</key-leaf>
                <ordinary-leaf>leaf-value</ordinary-leaf>
            </top-level-list>"""));
        final var payload = body.toPayload(EMPTY_PATH);

        final var entryId = NodeIdentifierWithPredicates.of(TOP_LEVEL_LIST,
            QName.create(TOP_LEVEL_LIST, "key-leaf"), "key-value");
        assertEquals(List.of(new NodeIdentifier(TOP_LEVEL_LIST), entryId), payload.prefix());
        assertEquals(ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(entryId)
            .withChild(ImmutableNodes.leafNode(QName.create(TOP_LEVEL_LIST, "key-leaf"), "key-value"))
            .withChild(ImmutableNodes.leafNode(QName.create(TOP_LEVEL_LIST, "ordinary-leaf"), "leaf-value"))
            .build(), payload.body());
    }

    @Test
    void moduleSubContainerDataPostTest() throws Exception {
        final var body = new XmlChildBody(stringInputStream("""
            <cont1 xmlns="instance:identifier:module">
              <lflst11 xmlns="augment:module:leaf:list">lflst11_1</lflst11>
              <lflst11 xmlns="augment:module:leaf:list">lflst11_2</lflst11>
              <lflst11 xmlns="augment:module:leaf:list">lflst11_3</lflst11>
              <lf11 xmlns:a="instance:identifier:module" xmlns:b="augment:module:leaf:list" \
            xmlns="augment:module:leaf:list">/a:cont/a:cont1/b:lflst11[.="lflst11_1"]</lf11>
            </cont1>"""));
        final var payload = body.toPayload(CONT_PATH);

        final var lflst11 = QName.create("augment:module:leaf:list", "2014-01-27", "lflst11");
        assertEquals(List.of(new NodeIdentifier(CONT1_QNAME)), payload.prefix());
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT1_QNAME))
            .withChild(ImmutableNodes.newSystemLeafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(lflst11))
                .withChildValue("lflst11_1")
                .withChildValue("lflst11_2")
                .withChildValue("lflst11_3")
                .build())
            .withChild(ImmutableNodes.leafNode(QName.create(lflst11, "lf11"), YangInstanceIdentifier.of(
                new NodeIdentifier(CONT_QNAME),
                new NodeIdentifier(CONT1_QNAME),
                new NodeIdentifier(lflst11),
                new NodeWithValue<>(lflst11, "lflst11_1"))))
            .build(), payload.body());
    }

    @Test
    void moduleSubContainerAugmentDataPostTest() throws Exception {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_container.xml"));
        final var payload = body.toPayload(CONT_PATH);

        final var contAugment = QName.create("augment:module", "2014-01-17", "cont-augment");
        assertEquals(List.of(new NodeIdentifier(contAugment)), payload.prefix());
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(contAugment))
            .withChild(ImmutableNodes.leafNode(QName.create(contAugment, "leaf1"), "stryng"))
            .build(), payload.body());
    }

    @Test
    void moduleSubContainerChoiceAugmentDataPostTest() throws Exception {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_choice_container.xml"));
        final var payload = body.toPayload(CONT_PATH);

        final var container1 = QName.create("augment:module", "2014-01-17", "case-choice-case-container1");
        assertEquals(List.of(
            new NodeIdentifier(QName.create(container1, "augment-choice1")),
            new NodeIdentifier(QName.create(container1, "augment-choice2")),
            new NodeIdentifier(container1)), payload.prefix());
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(new NodeIdentifier(container1))
            .withChild(ImmutableNodes.leafNode(QName.create(container1, "case-choice-case-leaf1"), "stryng"))
            .build(), payload.body());
    }

    /**
     * Test when container with the same name is placed in two modules (foo-module and bar-module). Namespace must be
     * used to distinguish between them to find correct one. Check if container was found not only according to its
     * name, but also by correct namespace used in payload.
     */
    @Test
    void findFooContainerUsingNamespaceTest() throws Exception {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindFooContainer.xml"));
        final var payload = body.toPayload(EMPTY_PATH);

        final var fooBarContainer = new NodeIdentifier(QName.create("foo:module", "2016-09-29", "foo-bar-container"));
        assertEquals(List.of(fooBarContainer), payload.prefix());
        assertEquals(ImmutableNodes.newContainerBuilder().withNodeIdentifier(fooBarContainer).build(),
            payload.body());
    }

    /**
     * Test when container with the same name is placed in two modules (foo-module and bar-module). Namespace must be
     * used to distinguish between them to find correct one. Check if container was found not only according to its
     * name, but also by correct namespace used in payload.
     */
    @Test
    void findBarContainerUsingNamespaceTest() throws Exception {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindBarContainer.xml"));
        final var payload = body.toPayload(EMPTY_PATH);

        final var fooBarContainer = new NodeIdentifier(QName.create("bar:module", "2016-09-29", "foo-bar-container"));
        assertEquals(List.of(fooBarContainer), payload.prefix());
        assertEquals(ImmutableNodes.newContainerBuilder().withNodeIdentifier(fooBarContainer).build(),
            payload.body());
    }
}
