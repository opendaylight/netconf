/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.XmlBodyReaderTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class XmlPatchBodyReaderTest extends AbstractBodyReaderTest {
    private static EffectiveModelContext schemaContext;

    private final XmlPatchBodyReader xmlToPatchBodyReader;

    public XmlPatchBodyReaderTest() throws Exception {
        super(schemaContext);
        xmlToPatchBodyReader = new XmlPatchBodyReader(databindProvider, mountPointService);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(MediaType.APPLICATION_XML, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Test
    public void moduleDataTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlPATCHdata.xml")));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Error code 400 should be returned.
     */
    @Test
    public void moduleDataValueMissingNegativeTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueMissing.xml");
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Error code 400 should be
     * returned.
     */
    @Test
    public void moduleDataNotValueNotSupportedNegativeTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataValueNotSupported.xml");
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test of Yang Patch with absolute target path.
     */
    @Test
    public void moduleDataAbsoluteTargetPathTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataAbsoluteTargetPath.xml")));
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont", xmlToPatchBodyReader, false);
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            XmlBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/xml/xmlPATCHdataCompleteTargetInURI.xml")));
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void moduleDataMergeOperationOnListTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/xml/xmlPATCHdataMergeOperationOnList.xml");
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void moduleDataMergeOperationOnContainerTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont", xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataMergeOperationOnContainer.xml");
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }

    /**
     * Test of Yang Patch on the top-level container with empty URI for data root.
     */
    @Test
    public void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final InputStream inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHTargetTopLevelContainerWithEmptyURI.xml");
        checkPatchContext(xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
    }

    /**
     * Test of Yang Patch on the top augmented element.
     */
    @Test
    public void moduleTargetTopLevelAugmentedContainerTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>test-patch</patch-id>
                <comment>This test patch for augmented element</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/test-m:container-root/test-m:container-lvl1/test-m-aug:container-aug</target>
                    <value>
                        <container-aug xmlns="test-ns-aug">
                            <leaf-aug>data</leaf-aug>
                        </container-aug>
                    </value>
                </edit>
            </yang-patch>
            """.getBytes(StandardCharsets.UTF_8));
        final var expectedData = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(CONT_AUG_QNAME))
                .withChild(ImmutableNodes.leafNode(LEAF_AUG_QNAME, "data"))
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(CONT_AUG_QNAME, data.name().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top system map node element.
     */
    @Test
    public void moduleTargetMapNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>map-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/map-model:cont-root/map-model:cont1/map-model:my-map=key</target>
                    <value>
                        <my-map xmlns="map:ns">
                            <key-leaf>key</key-leaf>
                            <data-leaf>data</data-leaf>
                        </my-map>
                    </value>
                </edit>
            </yang-patch>
            """.getBytes(StandardCharsets.UTF_8));
        final var expectedData = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MAP_CONT_QNAME))
                .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP_CONT_QNAME, KEY_LEAF_QNAME, "key"))
                        .withChild(ImmutableNodes.leafNode(KEY_LEAF_QNAME, "key"))
                        .withChild(ImmutableNodes.leafNode(DATA_LEAF_QNAME, "data"))
                        .build())
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(MAP_CONT_QNAME, data.name().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the leaf set node element.
     */
    @Test
    public void modulePatchTargetLeafSetNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>set-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/set-model:cont-root/set-model:cont1/set-model:my-set="data1"</target>
                    <value>
                        <my-set xmlns="set:ns">data1</my-set>
                    </value>
                </edit>
            </yang-patch>
            """.getBytes(StandardCharsets.UTF_8));
        final var expectedData = Builders.leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
                .withChild(Builders.leafSetEntryBuilder()
                        .withNodeIdentifier(new NodeWithValue<>(LEAF_SET_QNAME, "data1"))
                        .withValue("data1")
                        .build())
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LEAF_SET_QNAME, data.name().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top unkeyed list element.
     */
    @Test
    public void moduleTargetUnkeyedListNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>list-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/list-model:cont-root/list-model:cont1/list-model:unkeyed-list</target>
                    <value>
                        <unkeyed-list xmlns="list:ns">
                            <leaf1>data1</leaf1>
                            <leaf2>data2</leaf2>
                        </unkeyed-list>
                    </value>
                </edit>
            </yang-patch>
            """.getBytes(StandardCharsets.UTF_8));
        final var expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                        .withChild(ImmutableNodes.leafNode(LIST_LEAF1_QNAME, "data1"))
                        .withChild(ImmutableNodes.leafNode(LIST_LEAF2_QNAME, "data2"))
                        .build())
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LIST_QNAME, data.name().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top case node element.
     */
    @Test
    public void moduleTargetCaseNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
            <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                <patch-id>choice-patch</patch-id>
                <comment>YANG patch comment</comment>
                <edit>
                    <edit-id>edit1</edit-id>
                    <operation>replace</operation>
                    <target>/choice-model:cont-root/choice-model:cont1/choice-model:case-cont1</target>
                    <value>
                        <case-cont1 xmlns="choice:ns">
                            <case-leaf1>data</case-leaf1>
                        </case-cont1>
                    </value>
                </edit>
            </yang-patch>
            """.getBytes(StandardCharsets.UTF_8));
        final var expectedData = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
                .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(CHOICE_CONT_QNAME, data.name().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public void modulePatchSimpleLeafValueTest() throws Exception {
        mockBodyReader("instance-identifier-patch-module:patch-cont/my-list1=leaf1", xmlToPatchBodyReader, false);
        final var inputStream = new ByteArrayInputStream("""
                <yang-patch xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-patch">
                    <patch-id>test-patch</patch-id>
                    <comment>this is test patch</comment>
                    <edit>
                        <edit-id>edit1</edit-id>
                        <operation>replace</operation>
                        <target>/my-list2=my-leaf20/name</target>
                        <value>
                            <name xmlns="instance:identifier:patch:module">my-leaf20</name>
                        </value>
                    </edit>
                </yang-patch>
                """.getBytes(StandardCharsets.UTF_8));
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LEAF_NAME_QNAME, data.name().getNodeType());
        assertEquals(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf20"), data);
    }
}
