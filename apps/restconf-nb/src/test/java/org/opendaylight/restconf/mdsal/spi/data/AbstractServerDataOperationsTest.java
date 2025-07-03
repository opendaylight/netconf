/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.PatchContext;
import org.opendaylight.restconf.server.api.PatchEntity;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.testlib.AbstractJukeboxTest;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerDataOperations;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.DOMException;

abstract class AbstractServerDataOperationsTest extends AbstractJukeboxTest {
    static final ContainerNode JUKEBOX_WITH_BANDS = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(BAND_ENTRY)
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description 2"))
                .build())
            .build())
        .build();
    static final ContainerNode JUKEBOX_WITH_PLAYLIST = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description A"))
                .build())
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description B"))
                .build())
            .build())
        .build();
    static final MapNode PLAYLIST = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
        .withChild(BAND_ENTRY)
        .build();
    // instance identifier for accessing container node "player"
    static final YangInstanceIdentifier PLAYER_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME);
    static final YangInstanceIdentifier ARTIST_IID = YangInstanceIdentifier.builder()
        .node(JUKEBOX_QNAME)
        .node(LIBRARY_QNAME)
        .node(ARTIST_QNAME)
        .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "name of artist")
        .build();

    private static final Data ARTIST_DATA = jukeboxPath(ARTIST_IID);
    private static final Data PLAYER_DATA = jukeboxPath(PLAYER_IID);

    // Read mock data
    private static final DatabindContext MODULES_DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/modules"));

    static final QName BASE = QName.create("ns", "2016-02-28", "base");
    private static final QName LIST_KEY_QNAME = QName.create(BASE, "list-key");
    private static final QName LEAF_LIST_QNAME = QName.create(BASE, "leaf-list");
    private static final QName LIST_QNAME = QName.create(BASE, "list");
    static final QName CONT_QNAME = QName.create(BASE, "cont");

    private static final NodeIdentifierWithPredicates NODE_WITH_KEY =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue");
    private static final NodeIdentifierWithPredicates NODE_WITH_KEY_2 =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue2");

    private static final LeafNode<?> CONTENT = ImmutableNodes.leafNode(QName.create(BASE, "leaf-content"), "content");
    private static final LeafNode<?> CONTENT_2 =
        ImmutableNodes.leafNode(QName.create(BASE, "leaf-content-different"), "content-different");
    static final YangInstanceIdentifier PATH = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY)
        .build();
    static final YangInstanceIdentifier PATH_2 = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY_2)
        .build();
    static final YangInstanceIdentifier PATH_3 = YangInstanceIdentifier.of(CONT_QNAME, LIST_QNAME);
    private static final MapEntryNode DATA = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT)
        .build();
    static final MapEntryNode DATA_2 = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .build();
    private static final LeafNode<?> CONTENT_LEAF = ImmutableNodes.leafNode(QName.create(BASE, "content"), "test");
    private static final LeafNode<?> CONTENT_LEAF_2 = ImmutableNodes.leafNode(QName.create(BASE, "content2"), "test2");
    static final ContainerNode DATA_3 = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "container")))
        .withChild(CONTENT_LEAF)
        .build();
    static final ContainerNode DATA_4 = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE, "container2")))
        .withChild(CONTENT_LEAF_2)
        .build();
    static final MapNode LIST_DATA = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .build();
    static final MapNode LIST_DATA_2 = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    static final UserMapNode ORDERED_MAP_NODE_1 = ImmutableNodes.newUserMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .build();
    static final UserMapNode ORDERED_MAP_NODE_2 = ImmutableNodes.newUserMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    private static final MapEntryNode CHECK_DATA = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .withChild(CONTENT)
        .build();
    static final LeafSetNode<String> LEAF_SET_NODE_1 = ImmutableNodes.<String>newSystemLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    static final LeafSetNode<String> LEAF_SET_NODE_2 = ImmutableNodes.<String>newSystemLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .build();
    static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_1 = ImmutableNodes.<String>newUserLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_2 = ImmutableNodes.<String>newUserLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .withChildValue("four")
        .build();
    static final YangInstanceIdentifier LEAF_SET_NODE_PATH = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LEAF_LIST_QNAME)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_1 = ImmutableNodes.newUnkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_2 = ImmutableNodes.newUnkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT_2)
        .build();
    static final UnkeyedListNode UNKEYED_LIST_NODE_1 = ImmutableNodes.newUnkeyedListBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_1)
        .build();
    static final UnkeyedListNode UNKEYED_LIST_NODE_2 = ImmutableNodes.newUnkeyedListBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_2)
        .build();
    private static final NodeIdentifier NODE_IDENTIFIER =
        new NodeIdentifier(QName.create("ns", "2016-02-28", "container"));
    static final Data PATH_DATA = moudlesPath(PATH);
    static final Data PATH_2_DATA = moudlesPath(PATH_2);
    static final Data PATH_3_DATA = moudlesPath(PATH_3);
    static final Data LEAF_SET_NODE_DATA = moudlesPath(LEAF_SET_NODE_PATH);

    private final CompletingServerRequest<Empty> dataDeleteRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPatchResult> dataPatchRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataPostResult> dataPostRequest = new CompletingServerRequest<>();
    private final CompletingServerRequest<DataYangPatchResult> dataYangPatchRequest = new CompletingServerRequest<>();

    final CompletingServerRequest<DataPutResult> dataPutRequest = new CompletingServerRequest<>();

    static Data dataFromPathWithoutContext(final YangInstanceIdentifier path) {
        final var databindContext = DatabindContext.ofModel(JUKEBOX_SCHEMA);
        return new Data(databindContext, Inference.of(JUKEBOX_SCHEMA), path, databindContext.schemaTree().getRoot());
    }

    /**
     * Test of successful DELETE operation.
     */
    @Test
    final void testDeleteData() throws Exception {
        testDeleteDataStrategy().deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));
        assertEquals(Empty.value(), dataDeleteRequest.getResult());
    }

    abstract @NonNull AbstractServerDataOperations testDeleteDataStrategy();

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    final void testNegativeDeleteData() {
        testNegativeDeleteDataStrategy().deleteData(dataDeleteRequest, new Data(JUKEBOX_DATABIND));

        final var errors = assertThrows(RequestException.class, dataDeleteRequest::getResult).errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.DATA_MISSING, error.tag());
    }

    abstract @NonNull AbstractServerDataOperations testNegativeDeleteDataStrategy();

    @Test
    final void testPostContainerData() throws Exception {
        testPostContainerDataStrategy().createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));
        dataPostRequest.getResult();
    }

    abstract @NonNull AbstractServerDataOperations testPostContainerDataStrategy();

    @Test
    final void testPostListData() throws Exception {
        testPostListDataStrategy(BAND_ENTRY, PLAYLIST_IID.node(BAND_ENTRY.name()))
            .createData(dataPostRequest, jukeboxPath(PLAYLIST_IID), jukeboxPayload(PLAYLIST));
        dataPostRequest.getResult();
    }

    abstract @NonNull AbstractServerDataOperations testPostListDataStrategy(MapEntryNode entryNode,
        YangInstanceIdentifier node);

    @Test
    final void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");
        testPostDataFailStrategy(domException).createData(dataPostRequest, JUKEBOX_PATH, jukeboxPayload(EMPTY_JUKEBOX));

        final var errors = assertThrows(RequestException.class, dataPostRequest::getResult).errors();
        assertEquals(1, errors.size());
        assertThat(errors.get(0).info().elementBody(), containsString(domException.getMessage()));
    }

    abstract @NonNull AbstractServerDataOperations testPostDataFailStrategy(DOMException domException);

    @Test
    final void testPatchContainerData() throws Exception {
        testPatchContainerDataStrategy().mergeData(dataPatchRequest, JUKEBOX_PATH, EMPTY_JUKEBOX);
        dataPatchRequest.getResult();
    }

    abstract @NonNull AbstractServerDataOperations testPatchContainerDataStrategy();

    @Test
    final void testPatchLeafData() throws Exception {
        testPatchLeafDataStrategy().mergeData(dataPatchRequest, GAP_PATH, GAP_LEAF);
        dataPatchRequest.getResult();
    }

    abstract @NonNull AbstractServerDataOperations testPatchLeafDataStrategy();

    @Test
    final void testPatchListData() throws Exception {
        testPatchListDataStrategy().mergeData(dataPatchRequest, JUKEBOX_PATH, JUKEBOX_WITH_PLAYLIST);
        dataPatchRequest.getResult();
    }

    abstract @NonNull AbstractServerDataOperations testPatchListDataStrategy();

    @Test
    final void testPatchDataReplaceMergeAndRemove() {
        final var buildArtistList = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();

        patch(new PatchContext("patchRMRm",
            List.of(new PatchEntity("edit1", Operation.Replace, ARTIST_DATA.parent(), buildArtistList),
                new PatchEntity("edit2", Operation.Merge, ARTIST_DATA.parent(), buildArtistList),
                new PatchEntity("edit3", Operation.Remove, ARTIST_DATA))),
            testPatchDataReplaceMergeAndRemoveStrategy(), false, ARTIST_DATA.databind());
    }

    abstract @NonNull AbstractServerDataOperations testPatchDataReplaceMergeAndRemoveStrategy();

    @Test
    final void testPatchDataCreateAndDelete() {
        patch(new PatchContext("patchCD", List.of(
            new PatchEntity("edit1", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX),
            new PatchEntity("edit2", Operation.Delete, GAP_PATH))),
            testPatchDataCreateAndDeleteStrategy(), true, PLAYER_DATA.databind());
    }

    abstract @NonNull AbstractServerDataOperations testPatchDataCreateAndDeleteStrategy();

    @MethodSource
    static List<PatchContext> getPatchContext() {
        final var buildArtistList = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();
        Data artistParent = ARTIST_DATA.parent();
        return List.of(
            new PatchContext("VerifyNotExecutingLastPatchEntity", List.of(
                new PatchEntity("edit1", Operation.Replace, artistParent, buildArtistList),
                new PatchEntity("edit2", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX),
                new PatchEntity("edit3", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX))),
            new PatchContext("VerifyExceptionOnLastPatchEntity", List.of(
                new PatchEntity("edit1", Operation.Replace, artistParent, buildArtistList),
                new PatchEntity("edit2", Operation.Create, PLAYER_DATA, EMPTY_JUKEBOX)))
        );
    }

    @ParameterizedTest
    @MethodSource("getPatchContext")
    public final void testPatchWithDataExistException(final PatchContext patchContext) throws Exception {
        // Prepare patch request.
        final var strategy = testPatchWithDataExistExceptionStrategy();
        strategy.patchData(dataYangPatchRequest, new Data(ARTIST_DATA.databind()), patchContext);

        // Get patch result.
        final var patchStatusContext = dataYangPatchRequest.getResult(2, TimeUnit.DAYS).status();

        // Verify failure and confirm that edit3 operation was not executed.
        assertFalse(patchStatusContext.ok());
        assertNull(patchStatusContext.globalErrors());
        assertEquals(2, patchStatusContext.editCollection().size());

        // Verify that first request is without errors.
        final var delete = patchStatusContext.editCollection().get(0);
        assertTrue(delete.isOk());
        assertEquals("edit1", delete.getEditId());
        assertNull(delete.getEditErrors());

        // Verify that second request failed on DATA_EXISTS.
        final var firstCreate = patchStatusContext.editCollection().get(1);
        assertFalse(firstCreate.isOk());
        assertEquals("edit2", firstCreate.getEditId());
        assertNotNull(firstCreate.getEditErrors());
        final var serverError = firstCreate.getEditErrors().getFirst();
        assertEquals(ErrorTag.DATA_EXISTS, serverError.tag());
        assertEquals(new ErrorPath(PLAYER_DATA), serverError.path());
        assertEquals("Data already exists", serverError.message().elementBody());
    }

    abstract @NonNull AbstractServerDataOperations testPatchWithDataExistExceptionStrategy();

    @Test
    final void testPatchMergePutContainer() {
        patch(new PatchContext("patchM", List.of(new PatchEntity("edit1", Operation.Merge, PLAYER_DATA,
            EMPTY_JUKEBOX))), testPatchMergePutContainerStrategy(), false, PLAYER_DATA.databind());
    }

    abstract @NonNull AbstractServerDataOperations testPatchMergePutContainerStrategy();

    @Test
    final void testDeleteNonexistentData() throws Exception {
        deleteNonexistentDataTestStrategy().patchData(dataYangPatchRequest, new Data(JUKEBOX_DATABIND),
            new PatchContext("patchD", List.of(new PatchEntity("edit1", Operation.Delete, GAP_PATH))));

        final var status = dataYangPatchRequest.getResult().status();
        assertEquals("patchD", status.patchId());
        assertFalse(status.ok());
        final var edits = status.editCollection();
        assertEquals(1, edits.size());
        final var edit = edits.get(0);
        assertEquals("edit1", edit.getEditId());
        assertTestDeleteNonexistentData(status, edit);
    }

    abstract @NonNull AbstractServerDataOperations deleteNonexistentDataTestStrategy();

    abstract void assertTestDeleteNonexistentData(@NonNull PatchStatusContext status, @NonNull PatchStatusEntity edit);

    @Test
    final void readDataConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.CONFIG, PATH_DATA, readDataConfigTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readDataConfigTestStrategy();

    @Test
    final void readAllHavingOnlyConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.ALL, PATH_DATA, readAllHavingOnlyConfigTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readAllHavingOnlyConfigTestStrategy();

    @Test
    final void readAllHavingOnlyNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.ALL, PATH_2_DATA, readAllHavingOnlyNonConfigTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readAllHavingOnlyNonConfigTestStrategy();

    @Test
    final void readDataNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.NONCONFIG, PATH_2_DATA, readDataNonConfigTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readDataNonConfigTestStrategy();

    @Test
    final void readContainerDataAllTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA, readContainerDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readContainerDataAllTestStrategy();

    @Test
    final void readContainerDataConfigNoValueOfContentTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH_DATA, readContainerDataConfigNoValueOfContentTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readContainerDataConfigNoValueOfContentTestStrategy();

    @Test
    final void readListDataAllTest() {
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readListDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readListDataAllTestStrategy();

    @Test
    final void readOrderedListDataAllTest() {
        assertEquals(ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readOrderedListDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readOrderedListDataAllTestStrategy();

    @Test
    void readUnkeyedListDataAllTest() {
        assertEquals(ImmutableNodes.newUnkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next())
                .build())
            .build(), readData(ContentParam.ALL, PATH_3_DATA, readUnkeyedListDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readUnkeyedListDataAllTestStrategy();

    @Test
    final void readLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(LEAF_SET_NODE_1.body())
                .addAll(LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA, readLeafListDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readLeafListDataAllTestStrategy();

    @Test
    final void readOrderedLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newUserLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(ORDERED_LEAF_SET_NODE_1.body())
                .addAll(ORDERED_LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_DATA, readOrderedLeafListDataAllTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readOrderedLeafListDataAllTestStrategy();

    @Test
    void readDataWrongPathOrNoContentTest() {
        assertNull(readData(ContentParam.CONFIG, PATH_2_DATA, readDataWrongPathOrNoContentTestStrategy()));
    }

    abstract @NonNull AbstractServerDataOperations readDataWrongPathOrNoContentTestStrategy();

    static Data moudlesPath(final YangInstanceIdentifier path) {
        final var childAndStack = MODULES_DATABIND.schemaTree().enterPath(path).orElseThrow();
        return new Data(MODULES_DATABIND, childAndStack.stack().toInference(), path, childAndStack.node());
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param content        type of data to read (config, state, all)
     * @param strategy       {@link AbstractServerDataOperations} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    abstract @Nullable NormalizedNode readData(ContentParam content,
        Data path, AbstractServerDataOperations strategy);

    private void patch(final PatchContext patchContext, final AbstractServerDataOperations strategy,
            final boolean failed, final DatabindContext context) {
        strategy.patchData(dataYangPatchRequest, new Data(context), patchContext);

        final PatchStatusContext patchStatusContext;
        try {
            patchStatusContext = dataYangPatchRequest.getResult(1, TimeUnit.DAYS).status();
        } catch (RequestException | InterruptedException | TimeoutException e) {
            throw new AssertionError(e);
        }

        for (var entity : patchStatusContext.editCollection()) {
            if (failed) {
                assertTrue(entity.isOk(), "Edit " + entity.getEditId() + " failed");
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.ok());
    }
}
