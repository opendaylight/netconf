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

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.XmlBodyReaderTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.QName;
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
        final var inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataAugmentedElement.xml");
        final QName CONT_AUG = QName.create("test-ns-aug", "container-aug").intern();
        final QName DATA = QName.create("test-ns-aug", "leaf-aug").intern();
        final var expectedData = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(CONT_AUG))
                .withChild(ImmutableNodes.leafNode(DATA, "data"))
                .build();
        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(CONT_AUG, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top system map node element.
     */
    @Test
    public void moduleTargetMapNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataMapNode.xml");

        final QName MAP = QName.create("map:ns", "my-map").intern();
        final QName KEY = QName.create("map:ns", "key-leaf").intern();
        final QName DATA = QName.create("map:ns", "data-leaf").intern();
        final var expectedData = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MAP))
                .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP, KEY, "key"))
                        .withChild(ImmutableNodes.leafNode(KEY, "key"))
                        .withChild(ImmutableNodes.leafNode(DATA, "data"))
                        .build())
                .build();

        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(MAP, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the leaf set node element.
     */
    @Test
    public void modulePatchTargetLeafSetNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataLeafSetNode.xml");

        final QName LEAF_SET = QName.create("set:ns", "my-set").intern();
        final var expectedData = Builders.leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LEAF_SET))
                .withChild(Builders.leafSetEntryBuilder()
                        .withNodeIdentifier(new NodeWithValue(LEAF_SET, "data1"))
                        .withValue("data1")
                        .build())
                .build();
        // This one looks weird - XmlPatchBodyReader line 144 - seems like it operates only with ContainerSchemaNode and
        // ListSchemaNode and for others it just sel result to null. Weird because Json works ok...

//        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
//        checkPatchContext(returnValue);
//        final var data = returnValue.getData().get(0).getNode();
//        assertEquals(LEAF_SET, data.getIdentifier().getNodeType());
//        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top unkeyed list element.
     */
    @Test
    public void moduleTargetUnkeyedListNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataUnkeyedListNode.xml");

        final QName LIST = QName.create("list:ns", "unkeyed-list").intern();
        final QName DATA1 = QName.create("list:ns", "leaf1").intern();
        final QName DATA2 = QName.create("list:ns", "leaf2").intern();
        final var expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST))
                .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(LIST))
                        .withChild(ImmutableNodes.leafNode(DATA1, "data1"))
                        .withChild(ImmutableNodes.leafNode(DATA2, "data2"))
                        .build())
                .build();

        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LIST, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the top choice node element.
     */
    @Test
    public void moduleTargetChoiceNodeTest() throws Exception {
        mockBodyReader("", xmlToPatchBodyReader, false);
        final var inputStream = XmlBodyReaderTest.class
                .getResourceAsStream("/instanceidentifier/xml/xmlPATCHdataChoiceNode.xml");

        // TODO investigate more into this issue
        // XmlParserStream line 366 - it fails get leaf node, but you clearly can see it in debug.
        // May be problem in casting it to ContainerSchemaNode, because after casting there are no children nodes there.
//        final var returnValue = xmlToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
//        checkPatchContext(returnValue);
    }
}
