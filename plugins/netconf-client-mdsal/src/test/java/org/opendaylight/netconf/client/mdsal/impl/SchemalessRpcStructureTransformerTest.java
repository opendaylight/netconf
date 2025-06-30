/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.ConfigNodeKey;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;

@ExtendWith(MockitoExtension.class)
class SchemalessRpcStructureTransformerTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";

    private final SchemalessRpcStructureTransformer adapter = new SchemalessRpcStructureTransformer();

    @MethodSource
    static Stream<Arguments> parameters() {
        return Stream.of(Arguments.of(
                YangInstanceIdentifier.builder()
                    .node(createNodeId("top"))
                    .node(createNodeId("users"))
                    .build(), "container.xml", null),
            Arguments.of(YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user", "key", "k1"))
                .build(), "keyed-list.xml", null),
            Arguments.of(YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user",
                    ImmutableMap.of(QName.create(NAMESPACE, "key1"), "k1", QName.create(NAMESPACE, "key2"), "k2")))
                .build(), "keyed-list-compound-key.xml", null),
            Arguments.of(YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user", "key", "k2"))
                .build(), "keyed-list-bad-key.xml", IllegalStateException.class));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testCreateEditConfigStructure(final YangInstanceIdentifier path, final String testDataset,
            final Class<? extends Throwable> expectedException) throws Exception {
        final var source = new DOMSource(XmlUtil.readXmlToDocument(getClass()
            .getResourceAsStream("/schemaless/data/" + testDataset)).getDocumentElement());
        final var data = ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(source)
                .build();

        if (expectedException != null) {
            assertThrows(expectedException, () -> adapter.createEditConfigStructure(Optional.of(data), path,
                    Optional.of(EffectiveOperation.REPLACE)));
            return;
        }

        final var anyXmlNode =
                adapter.createEditConfigStructure(Optional.of(data), path, Optional.of(EffectiveOperation.REPLACE));
        final var diff = DiffBuilder.compare(Files.readString(Path.of(getClass()
                .getResource("/schemaless/edit-config/" + testDataset).toURI())))
            .withTest(anyXmlNode.body().getNode())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testCreateSingleEditConfigStructure(final YangInstanceIdentifier path, final String testDataset,
            final Class<? extends Throwable> expectedException) throws Exception {
        final var source = new DOMSource(XmlUtil.readXmlToDocument(getClass()
            .getResourceAsStream("/schemaless/data/" + testDataset)).getDocumentElement());
        final var data = ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
            .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
            .withValue(source)
            .build();

        final var elements = new LinkedHashMap<ConfigNodeKey, Optional<NormalizedNode>>();
        elements.put(new ConfigNodeKey(path, EffectiveOperation.REPLACE), Optional.of(data));
        elements.put(new ConfigNodeKey(path, EffectiveOperation.DELETE), Optional.of(data));
        elements.put(new ConfigNodeKey(path, EffectiveOperation.CREATE), Optional.of(data));

        if (expectedException != null) {
            assertThrows(expectedException, () -> adapter.createEditConfigStructure(elements));
            return;
        }

        final var anyXmlNode = adapter.createEditConfigStructure(elements);
        final var diff = DiffBuilder.compare(Files.readString(Path.of(getClass()
                .getResource("/schemaless/single-edit-config/" + testDataset).toURI())))
            .withTest(anyXmlNode.body().getNode())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testToFilterStructure(final YangInstanceIdentifier path, final String testDataset,
            final Class<? extends Throwable> expectedException) throws Exception {
        final var anyXmlNode = (DOMSourceAnyxmlNode) adapter.toFilterStructure(path);
        final var diff = DiffBuilder.compare(Files.readString(
                Path.of(getClass().getResource("/schemaless/filter/" + testDataset).toURI())))
            .withTest(anyXmlNode.body().getNode())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testSelectFromDataStructure(final YangInstanceIdentifier path, final String testDataset,
            final Class<? extends Throwable> expectedException) throws Exception {
        final var source = new DOMSource(XmlUtil.readXmlToDocument(getClass()
            .getResourceAsStream("/schemaless/data/" + testDataset)).getDocumentElement());
        final var data = ImmutableNodes.newAnyxmlBuilder(DOMSource.class)
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(new DOMSource(XmlUtil.readXmlToDocument(Files.readString(Path.of(getClass()
                    .getResource("/schemaless/get-config/" + testDataset).toURI()))).getDocumentElement()))
                .build();
        final var dataStructure = (DOMSourceAnyxmlNode) adapter.selectFromDataStructure(data, path).orElseThrow();
        final XmlElement s = XmlElement.fromDomDocument((Document) dataStructure.body().getNode());
        final var diff = DiffBuilder.compare(source.getNode())
            .withTest(s.getOnlyChildElement().getDomElement())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private static NodeIdentifier createNodeId(final String name) {
        return new NodeIdentifier(QName.create(NAMESPACE, name));
    }

    private static NodeIdentifierWithPredicates createListNodeId(
            final String nodeName, final String keyName, final String id) {
        return NodeIdentifierWithPredicates.of(QName.create(NAMESPACE, nodeName), QName.create(NAMESPACE, keyName), id);
    }

    private static NodeIdentifierWithPredicates createListNodeId(final String nodeName, final Map<QName, Object> keys) {
        return NodeIdentifierWithPredicates.of(QName.create(NAMESPACE, nodeName), keys);
    }
}
