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
import org.opendaylight.restconf.server.api.DataPostPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class XmlChildBodyTest extends AbstractBodyTest {
    private static final QName TOP_LEVEL_LIST = QName.create("foo", "2017-08-09", "top-level-list");

    private static DataPostPath EMPTY_PATH;
    private static DataPostPath CONT_PATH;

    @BeforeAll
    static void beforeAll() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        final var modelContext = YangParserTestUtils.parseYangFiles(testFiles);

        CONT_PATH = new DataPostPath(DatabindContext.ofModel(modelContext),
            Inference.ofDataTreePath(modelContext, CONT_QNAME), YangInstanceIdentifier.of(CONT_QNAME));
        EMPTY_PATH = new DataPostPath(DatabindContext.ofModel(modelContext),
            Inference.ofDataTreePath(modelContext), YangInstanceIdentifier.of());
    }

    @Test
    void postXmlTest() {
        final var body = new XmlChildBody(XmlChildBodyTest.class.getResourceAsStream("/foo-xml-test/foo.xml"));
        final var payload = body.toPayload(EMPTY_PATH);

        final var entryId = NodeIdentifierWithPredicates.of(TOP_LEVEL_LIST,
            QName.create(TOP_LEVEL_LIST, "key-leaf"), "key-value");
        assertEquals(List.of(new NodeIdentifier(TOP_LEVEL_LIST), entryId), payload.prefix());
        assertEquals(Builders.mapEntryBuilder()
            .withNodeIdentifier(entryId)
            .withChild(ImmutableNodes.leafNode(QName.create(TOP_LEVEL_LIST, "key-leaf"), "key-value"))
            .withChild(ImmutableNodes.leafNode(QName.create(TOP_LEVEL_LIST, "ordinary-leaf"), "leaf-value"))
            .build(), payload.body());
    }

    @Test
    void moduleSubContainerDataPostTest() {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xml_sub_container.xml"));
        final var payload = body.toPayload(CONT_PATH);

        final var lflst11 = QName.create("augment:module:leaf:list", "2014-01-27", "lflst11");
        assertEquals(List.of(new NodeIdentifier(CONT1_QNAME)), payload.prefix());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT1_QNAME))
            .withChild(Builders.leafSetBuilder()
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
    void moduleSubContainerAugmentDataPostTest() {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_container.xml"));
        final var payload = body.toPayload(CONT_PATH);

        final var contAugment = QName.create("augment:module", "2014-01-17", "cont-augment");
        assertEquals(List.of(new NodeIdentifier(contAugment)), payload.prefix());
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(contAugment))
            .withChild(ImmutableNodes.leafNode(QName.create(contAugment, "leaf1"), "stryng"))
            .build(), payload.body());
    }

    @Test
    void moduleSubContainerChoiceAugmentDataPostTest() {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xml_augment_choice_container.xml"));
        final var payload = body.toPayload(CONT_PATH);

        final var container1 = QName.create("augment:module", "2014-01-17", "case-choice-case-container1");
        assertEquals(List.of(
            new NodeIdentifier(QName.create(container1, "augment-choice1")),
            new NodeIdentifier(QName.create(container1, "augment-choice2")),
            new NodeIdentifier(container1)), payload.prefix());
        assertEquals(Builders.containerBuilder()
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
    void findFooContainerUsingNamespaceTest() {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindFooContainer.xml"));
        final var payload = body.toPayload(EMPTY_PATH);

        final var fooBarContainer = new NodeIdentifier(QName.create("foo:module", "2016-09-29", "foo-bar-container"));
        assertEquals(List.of(fooBarContainer), payload.prefix());
        assertEquals(Builders.containerBuilder().withNodeIdentifier(fooBarContainer).build(),
            payload.body());
    }

    /**
     * Test when container with the same name is placed in two modules (foo-module and bar-module). Namespace must be
     * used to distinguish between them to find correct one. Check if container was found not only according to its
     * name, but also by correct namespace used in payload.
     */
    @Test
    void findBarContainerUsingNamespaceTest() {
        final var body = new XmlChildBody(
            XmlChildBodyTest.class.getResourceAsStream("/instanceidentifier/xml/xmlDataFindBarContainer.xml"));
        final var payload = body.toPayload(EMPTY_PATH);

        final var fooBarContainer = new NodeIdentifier(QName.create("bar:module", "2016-09-29", "foo-bar-container"));
        assertEquals(List.of(fooBarContainer), payload.prefix());
        assertEquals(Builders.containerBuilder().withNodeIdentifier(fooBarContainer).build(),
            payload.body());
    }
}
