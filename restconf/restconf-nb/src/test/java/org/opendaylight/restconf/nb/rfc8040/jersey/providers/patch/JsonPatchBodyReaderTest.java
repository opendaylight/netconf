/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.InputStream;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.AbstractBodyReaderTest;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.test.JsonBodyReaderTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class JsonPatchBodyReaderTest extends AbstractBodyReaderTest {

    private final JsonPatchBodyReader jsonToPatchBodyReader;
    private static EffectiveModelContext schemaContext;

    public JsonPatchBodyReaderTest() throws Exception {
        super(schemaContext);
        jsonToPatchBodyReader = new JsonPatchBodyReader(databindProvider, mountPointService);
    }

    @Override
    protected MediaType getMediaType() {
        return new MediaType(APPLICATION_JSON, null);
    }

    @BeforeClass
    public static void initialization() {
        schemaContext = schemaContextLoader("/instanceidentifier/yang", schemaContext);
    }

    @Test
    public void modulePatchDataTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdata.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test of successful Patch consisting of create and delete Patch operations.
     */
    @Test
    public void modulePatchCreateAndDeleteTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataCreateAndDelete.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueMissingNegativeTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public void modulePatchValueNotSupportedNegativeTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public void modulePatchCompleteTargetInURITest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataCompleteTargetInURI.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test of Yang Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public void modulePatchMergeOperationOnListTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHMergeOperationOnList.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test of Yang Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public void modulePatchMergeOperationOnContainerTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHMergeOperationOnContainer.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public void modulePatchSimpleLeafValueTest() throws Exception {
        final String uri = "instance-identifier-patch-module:patch-cont/my-list1=leaf1";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHSimpleLeafValue.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test of Yang Patch on the top-level container with empty URI for data root.
     */
    @Test
    public void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        final String uri = "";
        mockBodyReader(uri, jsonToPatchBodyReader, false);

        final InputStream inputStream = JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHTargetTopLevelContainerWithEmptyURI.json");

        final PatchContext returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
    }

    /**
     * Test of Yang Patch on the system map node element.
     */
    @Test
    public void modulePatchTargetMapNodeTest() throws Exception {
        mockBodyReader("", jsonToPatchBodyReader, false);
        final var inputStream = JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataMapNode.json");
        final var expectedData = Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MAP_CONT_QNAME))
                .withChild(Builders.mapEntryBuilder()
                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP_CONT_QNAME, KEY_LEAF_QNAME, "key"))
                        .withChild(ImmutableNodes.leafNode(KEY_LEAF_QNAME, "key"))
                        .withChild(ImmutableNodes.leafNode(DATA_LEAF_QNAME, "data"))
                        .build())
                .build();
        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(MAP_CONT_QNAME, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the leaf set node element.
     */
    @Test
    public void modulePatchTargetLeafSetNodeTest() throws Exception {
        mockBodyReader("", jsonToPatchBodyReader, false);
        final var inputStream = JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataLeafSetNode.json");
        final var expectedData = Builders.leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
                .withChild(Builders.leafSetEntryBuilder()
                        .withNodeIdentifier(new NodeWithValue(LEAF_SET_QNAME, "data1"))
                        .withValue("data1")
                        .build())
                .build();

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LEAF_SET_QNAME, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the unkeyed list node element.
     */
    @Test
    public void modulePatchTargetUnkeyedListNodeTest() throws Exception {
        mockBodyReader("", jsonToPatchBodyReader, false);
        final var inputStream = JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataUnkeyedListNode.json");
        final var expectedData = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(Builders.unkeyedListEntryBuilder()
                        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                        .withChild(ImmutableNodes.leafNode(LIST_LEAF1_QNAME, "data1"))
                        .withChild(ImmutableNodes.leafNode(LIST_LEAF2_QNAME, "data2"))
                        .build())
                .build();

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(LIST_QNAME, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }

    /**
     * Test of Yang Patch on the choice node element.
     */
    @Test
    public void modulePatchTargetChoiceNodeTest() throws Exception {
        mockBodyReader("", jsonToPatchBodyReader, false);
        final var inputStream = JsonBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataChoiceNode.json");
        final var expectedData = Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
                .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
                .build();
        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream);
        checkPatchContext(returnValue);
        final var data = returnValue.getData().get(0).getNode();
        assertEquals(CHOICE_CONT_QNAME, data.getIdentifier().getNodeType());
        assertEquals(expectedData, data);
    }
}
