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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

public class JsonPatchBodyReaderTest extends AbstractPatchBodyReaderTest {
    private final JsonPatchBodyReader jsonToPatchBodyReader =
        new JsonPatchBodyReader(databindProvider, mountPointService);

    @Override
    protected final MediaType getMediaType() {
        return new MediaType(APPLICATION_JSON, null);
    }

    @Test
    public final void modulePatchDataTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream("/instanceidentifier/json/jsonPATCHdata.json")));
    }

    /**
     * Test of successful Patch consisting of create and delete Patch operations.
     */
    @Test
    public final void modulePatchCreateAndDeleteTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataCreateAndDelete.json")));
    }

    /**
     * Test trying to use Patch create operation which requires value without value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public final void modulePatchValueMissingNegativeTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        final var inputStream = JsonPatchBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueMissing.json");

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test trying to use value with Patch delete operation which does not support value. Test should fail with
     * {@link RestconfDocumentedException} with error code 400.
     */
    @Test
    public final void modulePatchValueNotSupportedNegativeTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        final var inputStream = JsonPatchBodyReaderTest.class.getResourceAsStream(
            "/instanceidentifier/json/jsonPATCHdataValueNotSupported.json");

        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null, inputStream));
        assertEquals(ErrorTag.MALFORMED_MESSAGE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Test using Patch when target is completely specified in request URI and thus target leaf contains only '/' sign.
     */
    @Test
    public final void modulePatchCompleteTargetInURITest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont", jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHdataCompleteTargetInURI.json")));
    }

    /**
     * Test of YANG Patch merge operation on list. Test consists of two edit operations - replace and merge.
     */
    @Test
    public final void modulePatchMergeOperationOnListTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHMergeOperationOnList.json")));
    }

    /**
     * Test of YANG Patch merge operation on container. Test consists of two edit operations - create and merge.
     */
    @Test
    public final void modulePatchMergeOperationOnContainerTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont", jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHMergeOperationOnContainer.json")));
    }

    /**
     * Test reading simple leaf value.
     */
    @Test
    public final void modulePatchSimpleLeafValueTest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=leaf1",
            jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHSimpleLeafValue.json"));
        checkPatchContext(returnValue);
        assertEquals(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf20"), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the top-level container with empty URI for data root.
     */
    @Test
    public final void modulePatchTargetTopLevelContainerWithEmptyURITest() throws Exception {
        mockBodyReader(mountPrefix(), jsonToPatchBodyReader, false);

        checkPatchContext(jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            JsonPatchBodyReaderTest.class.getResourceAsStream(
                "/instanceidentifier/json/jsonPATCHTargetTopLevelContainerWithEmptyURI.json")));
    }

    /**
     * Test of YANG Patch on the top-level container with the full path in the URI and "/" in 'target'.
     */
    @Test
    public final void modulePatchTargetTopLevelContainerWithFullPathURITest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont", jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "test-patch",
                        "comment": "Test patch applied to the top-level container with '/' in target",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/",
                                "value": {
                                    "patch-cont": {
                                        "my-list1": [
                                            {
                                                "name": "my-leaf-set",
                                                "my-leaf11": "leaf-a",
                                                "my-leaf12": "leaf-b"
                                            }
                                        ]
                                    }
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(PATCH_CONT_QNAME))
            .withChild(Builders.mapBuilder()
                .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
                .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                    .build())
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the second-level list with the full path in the URI and "/" in 'target'.
     */
    @Test
    public final void modulePatchTargetSecondLevelListWithFullPathURITest() throws Exception {
        mockBodyReader(mountPrefix() + "instance-identifier-patch-module:patch-cont/my-list1=my-leaf-set",
            jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "test-patch",
                        "comment": "Test patch applied to the second-level list with '/' in target",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/",
                                "value": {
                                    "my-list1": [
                                        {
                                            "name": "my-leaf-set",
                                            "my-leaf11": "leaf-a",
                                            "my-leaf12": "leaf-b"
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MY_LIST1_QNAME))
            .withChild(Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(
                            MY_LIST1_QNAME, LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(LEAF_NAME_QNAME, "my-leaf-set"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF11_QNAME, "leaf-a"))
                    .withChild(ImmutableNodes.leafNode(MY_LEAF12_QNAME, "leaf-b"))
                    .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the top augmented element.
     */
    @Test
    public final void modulePatchTargetTopLevelAugmentedContainerTest() throws Exception {
        mockBodyReader(mountPrefix(), jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "test-patch",
                        "comment": "comment",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/test-m:container-root/test-m:container-lvl1/test-m-aug:container-aug",
                                "value": {
                                    "container-aug": {
                                        "leaf-aug": "data"
                                    }
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CONT_AUG_QNAME))
            .withChild(ImmutableNodes.leafNode(LEAF_AUG_QNAME, "data"))
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of YANG Patch on the system map node element.
     */
    @Test
    public final void modulePatchTargetMapNodeTest() throws Exception {
        mockBodyReader(mountPrefix(), jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "map-patch",
                        "comment": "comment",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/map-model:cont-root/map-model:cont1/map-model:my-map=key",
                                "value": {
                                    "my-map": {
                                        "key-leaf": "key",
                                        "data-leaf": "data"
                                    }
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(MAP_CONT_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MAP_CONT_QNAME, KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(KEY_LEAF_QNAME, "key"))
                .withChild(ImmutableNodes.leafNode(DATA_LEAF_QNAME, "data"))
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the leaf set node element.
     */
    @Test
    public final void modulePatchTargetLeafSetNodeTest() throws Exception {
        mockBodyReader(mountPrefix() + "", jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "set-patch",
                        "comment": "comment",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/set-model:cont-root/set-model:cont1/set-model:my-set=data1",
                                "value": {
                                    "my-set": [ "data1" ]
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.leafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_SET_QNAME))
            .withChild(Builders.leafSetEntryBuilder()
                .withNodeIdentifier(new NodeWithValue<>(LEAF_SET_QNAME, "data1"))
                .withValue("data1")
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the unkeyed list node element.
     */
    @Test
    public final void modulePatchTargetUnkeyedListNodeTest() throws Exception {
        mockBodyReader(mountPrefix(), jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "list-patch",
                        "comment": "comment",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/list-model:cont-root/list-model:cont1/list-model:unkeyed-list",
                                "value": {
                                    "unkeyed-list": {
                                        "leaf1": "data1",
                                        "leaf2": "data2"
                                    }
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.unkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(Builders.unkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF1_QNAME, "data1"))
                .withChild(ImmutableNodes.leafNode(LIST_LEAF2_QNAME, "data2"))
                .build())
            .build(), returnValue.getData().get(0).getNode());
    }

    /**
     * Test of Yang Patch on the case node element.
     */
    @Test
    public final void modulePatchTargetCaseNodeTest() throws Exception {
        mockBodyReader(mountPrefix(), jsonToPatchBodyReader, false);

        final var returnValue = jsonToPatchBodyReader.readFrom(null, null, null, mediaType, null,
            new ByteArrayInputStream("""
                {
                    "ietf-yang-patch:yang-patch": {
                        "patch-id": "choice-patch",
                        "comment": "comment",
                        "edit": [
                            {
                                "edit-id": "edit1",
                                "operation": "replace",
                                "target": "/choice-model:cont-root/choice-model:cont1/choice-model:case-cont1",
                                "value": {
                                    "case-cont1": {
                                        "case-leaf1": "data"
                                    }
                                }
                            }
                        ]
                    }
                }""".getBytes(StandardCharsets.UTF_8)));
        checkPatchContext(returnValue);
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(CHOICE_CONT_QNAME))
            .withChild(ImmutableNodes.leafNode(CASE_LEAF1_QNAME, "data"))
            .build(), returnValue.getData().get(0).getNode());
    }
}
