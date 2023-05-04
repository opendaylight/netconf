/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

@RunWith(Parameterized.class)
public class SchemalessRpcStructureTransformerTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            { YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .build(), "container.xml", null
            },
            { YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user", "key", "k1"))
                .build(), "keyed-list.xml", null
            },
            { YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user",
                    ImmutableMap.of(QName.create(NAMESPACE, "key1"), "k1", QName.create(NAMESPACE, "key2"), "k2")))
                .build(), "keyed-list-compound-key.xml", null
            },
            { YangInstanceIdentifier.builder()
                .node(createNodeId("top"))
                .node(createNodeId("users"))
                .node(createListNodeId("user", "key", "k2"))
                .build(), "keyed-list-bad-key.xml", IllegalStateException.class
            }});
    }

    @BeforeClass
    public static void suiteSetup() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    private final Class<? extends Exception> expectedException;

    private final String expectedConfig;
    private final String expectedFilter;
    private final String getConfigData;
    private final YangInstanceIdentifier path;
    private final DOMSource source;

    private final SchemalessRpcStructureTransformer adapter = new SchemalessRpcStructureTransformer();
    private final String testDataset;

    public SchemalessRpcStructureTransformerTest(
            final YangInstanceIdentifier path, final String testDataset,
            final Class<? extends Exception> expectedException) throws IOException, SAXException, URISyntaxException {
        this.path = path;
        this.testDataset = testDataset;
        this.expectedException = expectedException;
        source = new DOMSource(XmlUtil.readXmlToDocument(getClass()
                .getResourceAsStream("/schemaless/data/" + testDataset)).getDocumentElement());
        expectedConfig = Files.readString(
                Paths.get(getClass().getResource("/schemaless/edit-config/" + testDataset).toURI()));
        expectedFilter = Files.readString(
                Paths.get(getClass().getResource("/schemaless/filter/" + testDataset).toURI()));
        getConfigData = Files.readString(
                Paths.get(getClass().getResource("/schemaless/get-config/" + testDataset).toURI()));
    }

    @Test
    public void testCreateEditConfigStructure() throws Exception {
        DOMSourceAnyxmlNode data = Builders.anyXmlBuilder()
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(source)
                .build();

        if (expectedException != null) {
            assertThrows(expectedException,
                () -> adapter.createEditConfigStructure(Optional.of(data), path,
                    Optional.of(EffectiveOperation.REPLACE)));
            return;
        }

        final DOMSourceAnyxmlNode anyXmlNode =
                adapter.createEditConfigStructure(Optional.of(data), path, Optional.of(EffectiveOperation.REPLACE));
        final String s = XmlUtil.toString((Element) anyXmlNode.body().getNode());
        Diff diff = new Diff(expectedConfig, s);
        assertTrue(String.format("Input %s: %s", testDataset, diff.toString()), diff.similar());
    }

    @Test
    public void testToFilterStructure() throws Exception {
        final DOMSourceAnyxmlNode anyXmlNode = (DOMSourceAnyxmlNode) adapter.toFilterStructure(path);
        final String s = XmlUtil.toString((Element) anyXmlNode.body().getNode());
        Diff diff = new Diff(expectedFilter, s);
        assertTrue(String.format("Input %s: %s", testDataset, diff.toString()), diff.similar());
    }

    @Test
    public void testSelectFromDataStructure() throws Exception {
        DOMSourceAnyxmlNode data = Builders.anyXmlBuilder()
                .withNodeIdentifier(createNodeId(path.getLastPathArgument().getNodeType().getLocalName()))
                .withValue(new DOMSource(XmlUtil.readXmlToDocument(getConfigData).getDocumentElement()))
                .build();
        final DOMSourceAnyxmlNode dataStructure = (DOMSourceAnyxmlNode) adapter.selectFromDataStructure(data, path)
                .orElseThrow();
        final XmlElement s = XmlElement.fromDomDocument((Document) dataStructure.body().getNode());
        final String dataFromReply = XmlUtil.toString(s.getOnlyChildElement().getDomElement());
        final String expectedData = XmlUtil.toString((Element) source.getNode());
        Diff diff = new Diff(expectedData, dataFromReply);
        assertTrue(String.format("Input %s: %s", testDataset, diff.toString()), diff.similar());
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
