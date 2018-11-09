/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import javax.xml.transform.dom.DOMSource;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

@RunWith(Parameterized.class)
public class SchemalessRpcStructureTransformerTest {

    private static final String NAMESPACE = "http://example.com/schema/1.2/config";

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    private final Class<? extends Exception> expectedException;

    private final String expectedConfig;
    private final String expectedFilter;
    private final String getConfigData;
    private final YangInstanceIdentifier path;
    private final DOMSource source;

    private final SchemalessRpcStructureTransformer adapter = new SchemalessRpcStructureTransformer();
    private final String testDataset;

    public SchemalessRpcStructureTransformerTest(
            final YangInstanceIdentifier path, String testDataset, final Class<? extends Exception> expectedException)
            throws IOException, SAXException, URISyntaxException {
        this.path = path;
        this.testDataset = testDataset;
        this.expectedException = expectedException;
        this.source = new DOMSource(XmlUtil.readXmlToDocument(getClass()
                .getResourceAsStream("/schemaless/data/" + testDataset)).getDocumentElement());
        this.expectedConfig = new String(Files.readAllBytes(
                Paths.get(getClass().getResource("/schemaless/edit-config/" + testDataset).toURI())));
        this.expectedFilter = new String(Files.readAllBytes(
                Paths.get(getClass().getResource("/schemaless/filter/" + testDataset).toURI())));
        this.getConfigData = new String(Files.readAllBytes(
                Paths.get(getClass().getResource("/schemaless/get-config/" + testDataset).toURI())));
    }

    @Parameterized.Parameters
    public static Collection parameters() {
        final Object[][] params = {
                {YangInstanceIdentifier.builder()
                        .node(createNodeId("top"))
                        .node(createNodeId("users"))
                        .build(), "container.xml", null},
                {YangInstanceIdentifier.builder()
                        .node(createNodeId("top"))
                        .node(createNodeId("users"))
                        .node(createListNodeId("user", "key", "k1"))
                        .build(), "keyed-list.xml", null},
                {YangInstanceIdentifier.builder()
                        .node(createNodeId("top"))
                        .node(createNodeId("users"))
                        .node(createListNodeId("user", ImmutableMap.of(QName.create(NAMESPACE, "key1"), "k1",
                                QName.create(NAMESPACE, "key2"), "k2")))
                        .build(), "keyed-list-compound-key.xml", null},
                {YangInstanceIdentifier.builder()
                        .node(createNodeId("top"))
                        .node(createNodeId("users"))
                        .node(createListNodeId("user", "key", "k2"))
                        .build(), "keyed-list-bad-key.xml", IllegalStateException.class}
        };
        return Arrays.asList(params);
    }

    @Test
    public void testCreateEditConfigStructure() {
        if (expectedException != null) {
            thrown.expect(expectedException);
        }
        final AnyXmlNode data = Builders.anyXmlBuilder()
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(source)
                .build();
        final AnyXmlNode anyXmlNode =
                adapter.createEditConfigStructure(Optional.of(data), path, Optional.of(ModifyAction.REPLACE));
        final String s = XmlUtil.toString((Element) anyXmlNode.getValue().getNode());
        final Diff diff = DiffBuilder.compare(expectedConfig)
                .withTest(s)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();
        Assert.assertFalse(String.format("Input %s: %s", testDataset, diff.toString()), diff.hasDifferences());
    }

    @Test
    public void testToFilterStructure() {
        final AnyXmlNode anyXmlNode = (AnyXmlNode) adapter.toFilterStructure(path);
        final String s = XmlUtil.toString((Element) anyXmlNode.getValue().getNode());
        final Diff diff = DiffBuilder.compare(expectedFilter)
                .withTest(s)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();
        Assert.assertFalse(String.format("Input %s: %s", testDataset, diff.toString()), diff.hasDifferences());
    }

    @Test
    public void testSelectFromDataStructure() throws Exception {
        final AnyXmlNode data = Builders.anyXmlBuilder()
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(new DOMSource(XmlUtil.readXmlToDocument(getConfigData).getDocumentElement()))
                .build();
        final AnyXmlNode dataStructure = (AnyXmlNode) adapter.selectFromDataStructure(data, path).get();
        final XmlElement s = XmlElement.fromDomDocument((Document) dataStructure.getValue().getNode());
        final String dataFromReply = XmlUtil.toString(s.getOnlyChildElement().getDomElement());
        final String expectedData = XmlUtil.toString((Element) source.getNode());
        final Diff diff = DiffBuilder.compare(expectedData)
                .withTest(dataFromReply)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();
        Assert.assertFalse(String.format("Input %s: %s", testDataset, diff.toString()), diff.hasDifferences());
    }

    private static YangInstanceIdentifier.NodeIdentifier createNodeId(String name) {
        return new YangInstanceIdentifier.NodeIdentifier(QName.create(NAMESPACE, name));
    }

    private static YangInstanceIdentifier.NodeIdentifierWithPredicates createListNodeId(
            final String nodeName, final String keyName, final String id) {
        return new YangInstanceIdentifier
                .NodeIdentifierWithPredicates(QName.create(NAMESPACE, nodeName), QName.create(NAMESPACE, keyName), id);
    }

    private static YangInstanceIdentifier.NodeIdentifierWithPredicates createListNodeId(
            final String nodeName, final Map<QName, Object> keys) {
        return new YangInstanceIdentifier.NodeIdentifierWithPredicates(QName.create(NAMESPACE, nodeName), keys);
    }
}
