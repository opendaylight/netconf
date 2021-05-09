/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.nn.to.xml.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.sal.rest.impl.test.providers.AbstractBodyReaderTest;
import org.opendaylight.netconf.sal.rest.impl.NormalizedNodeXmlBodyWriter;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

public class NnInstanceIdentifierToXmlTest extends AbstractBodyReaderTest {
    private static EffectiveModelContext schemaContext;

    private final NormalizedNodeXmlBodyWriter xmlBodyWriter = new NormalizedNodeXmlBodyWriter();

    public NnInstanceIdentifierToXmlTest() {
        super(schemaContext, null);
    }

    @BeforeClass
    public static void initialization() throws URISyntaxException {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Test
    public void nnAsYangInstanceIdentifierAugmentLeafList() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNCLeafList();

        final OutputStream output = new ByteArrayOutputStream();

        xmlBodyWriter.writeTo(normalizedNodeContext, null, null, null, mediaType, null, output);

        assertNotNull(output);

        final String outputJson = output.toString();

        assertTrue(outputJson.contains("<cont xmlns="));
        assertTrue(outputJson.contains(
                '"' + "instance:identifier:module" + '"'));
        assertTrue(outputJson.contains(">"));

        assertTrue(outputJson.contains("<cont1>"));

        assertTrue(outputJson.contains("<lf11 xmlns="));
        assertTrue(outputJson.contains(
                '"' + "augment:module:leaf:list" + '"'));
        assertTrue(outputJson.contains(">"));
        assertTrue(outputJson.contains("/instanceidentifier/"));
        assertTrue(outputJson.contains("</lf11>"));

        assertTrue(outputJson.contains("<lflst11 xmlns="));
        assertTrue(outputJson.contains(
                '"' + "augment:module:leaf:list" + '"'));
        assertTrue(outputJson.contains(">"));
        assertTrue(outputJson.contains("lflst11 value"));
        assertTrue(outputJson.contains("</lflst11>"));

        assertTrue(outputJson.contains("</cont1>"));
        assertTrue(outputJson.contains("</cont>"));
    }

    private static NormalizedNodeContext prepareNNCLeafList() {
        final QName cont = QName.create("instance:identifier:module", "2014-01-17", "cont");
        final QName lflst11 = QName.create("augment:module:leaf:list", "2014-01-17", "lflst11");

        return new NormalizedNodeContext(
            new InstanceIdentifierContext<>(null, schemaContext.getDataChildByName(cont), null, schemaContext),
            Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(cont))
                .withChild(Builders.containerBuilder()
                    .withNodeIdentifier(new NodeIdentifier(QName.create(cont, "cont1")))
                    .withChild(Builders.leafSetBuilder()
                        .withNodeIdentifier(new NodeIdentifier(lflst11))
                        .withChildValue("lflst11 value")
                        .build())
                    .withChild(ImmutableNodes.leafNode(QName.create(lflst11, "lf11"), "/instanceidentifier/"))
                    .build())
                .build());
    }

    @Test
    public void nnAsYangInstanceIdentifierAugment() throws Exception {

        final NormalizedNodeContext normalizedNodeContext = preparNNC();
        final OutputStream output = new ByteArrayOutputStream();

        xmlBodyWriter.writeTo(normalizedNodeContext, null, null, null, mediaType, null, output);

        assertNotNull(output);

        final String outputJson = output.toString();

        assertTrue(outputJson.contains("<cont xmlns="));
        assertTrue(outputJson.contains(
                '"' + "instance:identifier:module" + '"'));
        assertTrue(outputJson.contains(">"));

        assertTrue(outputJson.contains("<cont1>"));

        assertTrue(outputJson.contains("<lst11 xmlns="));
        assertTrue(outputJson.contains('"' + "augment:module" + '"'));
        assertTrue(outputJson.contains(">"));

        assertTrue(outputJson.contains(
                "<keyvalue111>keyvalue111</keyvalue111>"));
        assertTrue(outputJson.contains(
                "<keyvalue112>keyvalue112</keyvalue112>"));

        assertTrue(outputJson.contains("<lf111 xmlns="));
        assertTrue(outputJson.contains(
                '"' + "augment:augment:module" + '"'));
        assertTrue(outputJson.contains(">/cont/cont1/lf12</lf111>"));

        assertTrue(outputJson.contains("<lf112 xmlns="));
        assertTrue(outputJson.contains(
                '"' + "augment:augment:module" + '"'));
        assertTrue(outputJson.contains(">lf12 value</lf112>"));

        assertTrue(outputJson.contains("</lst11></cont1></cont>"));
    }

    private static NormalizedNodeContext preparNNC() {
        final QName cont = QName.create("instance:identifier:module", "2014-01-17",
                "cont");
        final QName cont1 = QName.create("instance:identifier:module", "2014-01-17",
                "cont1");
        final QName lst11 = QName.create("augment:module", "2014-01-17", "lst11");
        final QName lf11 = QName.create("augment:augment:module", "2014-01-17",
                "lf111");
        final QName lf12 = QName.create("augment:augment:module", "2014-01-17",
                "lf112");
        final QName keyvalue111 = QName.create("augment:module", "2014-01-17",
                "keyvalue111");
        final QName keyvalue112 = QName.create("augment:module", "2014-01-17",
                "keyvalue112");

        final DataSchemaNode schemaCont = schemaContext.getDataChildByName(cont);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> dataCont = Builders
                .containerBuilder((ContainerSchemaNode) schemaCont);

        final DataSchemaNode schemaCont1 = ((ContainerSchemaNode) schemaCont)
                .getDataChildByName(cont1);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> dataCont1 = Builders
                .containerBuilder((ContainerSchemaNode) schemaCont1);

        final List<DataSchemaNode> instanceLst11 = ControllerContext
                .findInstanceDataChildrenByName(
                        (DataNodeContainer) schemaCont1, lst11.getLocalName());
        final DataSchemaNode lst11Schema = Iterables.getFirst(instanceLst11, null);

        final CollectionNodeBuilder<MapEntryNode, MapNode> dataLst11 = Builders
                .mapBuilder((ListSchemaNode) lst11Schema);

        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> dataLst11Vaule = Builders
                .mapEntryBuilder((ListSchemaNode) lst11Schema);

        dataLst11Vaule.withChild(buildLeaf(lst11Schema, keyvalue111, dataLst11,
                "keyvalue111"));

        dataLst11Vaule.withChild(buildLeaf(lst11Schema, keyvalue112, dataLst11,
                "keyvalue112"));

        dataLst11Vaule.withChild(buildLeaf(lst11Schema, lf11, dataLst11,
                "/cont/cont1/lf12"));

        dataLst11Vaule.withChild(buildLeaf(lst11Schema, lf12, dataLst11, "lf12 value"));

        dataLst11.withChild(dataLst11Vaule.build());

        dataCont1.withChild(dataLst11.build());
        dataCont.withChild(dataCont1.build());

        final NormalizedNodeContext testNormalizedNodeContext = new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, schemaCont,
                        null, schemaContext), dataCont.build());

        return testNormalizedNodeContext;
    }

    private static LeafNode<?> buildLeaf(final DataSchemaNode lst11Schema, final QName qname,
            final CollectionNodeBuilder<MapEntryNode, MapNode> dataLst11, final Object value) {

        final List<DataSchemaNode> instanceLf = ControllerContext
                .findInstanceDataChildrenByName(
                        (DataNodeContainer) lst11Schema, qname.getLocalName());
        final DataSchemaNode schemaLf = Iterables.getFirst(instanceLf, null);

        return Builders.leafBuilder((LeafSchemaNode) schemaLf).withValue(value)
                .build();
    }

    @Override
    protected MediaType getMediaType() {
        return null;
    }
}
