/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.CREATE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.DELETE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.MERGE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REMOVE;
import static org.opendaylight.restconf.common.patch.PatchEditOperation.REPLACE;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
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
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.DOMException;

abstract class AbstractRestconfStrategyTest extends AbstractJukeboxTest {
    static final ContainerNode JUKEBOX_WITH_BANDS = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(BAND_ENTRY)
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band 2"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description 2"))
                .build())
            .build())
        .build();
    static final ContainerNode JUKEBOX_WITH_PLAYLIST = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-A"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description A"))
                .build())
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "MyFavoriteBand-B"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description B"))
                .build())
            .build())
        .build();
    static final MapNode PLAYLIST = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
        .withChild(Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band"))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band"))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description"))
            .build())
        .build();
    static final YangInstanceIdentifier PLAYLIST_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYLIST_QNAME);
    // instance identifier for accessing container node "player"
    static final YangInstanceIdentifier PLAYER_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME);
    static final YangInstanceIdentifier ARTIST_IID = YangInstanceIdentifier.builder()
        .node(JUKEBOX_QNAME)
        .node(LIBRARY_QNAME)
        .node(ARTIST_QNAME)
        .nodeWithKey(ARTIST_QNAME, NAME_QNAME, "name of artist")
        .build();
    // FIXME: this looks weird
    static final YangInstanceIdentifier CREATE_AND_DELETE_TARGET = GAP_IID.node(PLAYER_QNAME).node(GAP_QNAME);

    // Read mock data
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
    private static final MapEntryNode DATA = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT)
        .build();
    static final MapEntryNode DATA_2 = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .build();
    private static final LeafNode<?> CONTENT_LEAF = ImmutableNodes.leafNode(QName.create(BASE, "content"), "test");
    private static final LeafNode<?> CONTENT_LEAF_2 = ImmutableNodes.leafNode(QName.create(BASE, "content2"), "test2");
    static final ContainerNode DATA_3 = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "container")))
        .withChild(CONTENT_LEAF)
        .build();
    static final ContainerNode DATA_4 = Builders.containerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE, "container2")))
        .withChild(CONTENT_LEAF_2)
        .build();
    static final MapNode LIST_DATA = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .build();
    static final MapNode LIST_DATA_2 = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    static final UserMapNode ORDERED_MAP_NODE_1 = Builders.orderedMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .build();
    static final UserMapNode ORDERED_MAP_NODE_2 = Builders.orderedMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    private static final MapEntryNode CHECK_DATA = Builders.mapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .withChild(CONTENT)
        .build();
    static final LeafSetNode<String> LEAF_SET_NODE_1 = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    static final LeafSetNode<String> LEAF_SET_NODE_2 = Builders.<String>leafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .build();
    static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_1 = Builders.<String>orderedLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_2 = Builders.<String>orderedLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .withChildValue("four")
        .build();
    static final YangInstanceIdentifier LEAF_SET_NODE_PATH = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LEAF_LIST_QNAME)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_1 = Builders.unkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT)
        .build();
    private static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_2 = Builders.unkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT_2)
        .build();
    static final UnkeyedListNode UNKEYED_LIST_NODE_1 = Builders.unkeyedListBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_1)
        .build();
    static final UnkeyedListNode UNKEYED_LIST_NODE_2 = Builders.unkeyedListBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_2)
        .build();
    private static final NodeIdentifier NODE_IDENTIFIER =
        new NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    @Mock
    private EffectiveModelContext mockSchemaContext;
    @Mock
    private UriInfo uriInfo;

    /**
     * Test of successful DELETE operation.
     */
    @Test
    public final void testDeleteData() throws Exception {
        final var future = testDeleteDataStrategy().delete(YangInstanceIdentifier.of());
        assertNotNull(Futures.getDone(future));
    }

    abstract @NonNull RestconfStrategy testDeleteDataStrategy();

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    public final void testNegativeDeleteData() {
        final var future = testNegativeDeleteDataStrategy().delete(YangInstanceIdentifier.of());
        final var ex = assertThrows(ExecutionException.class, () -> Futures.getDone(future)).getCause();
        assertThat(ex, instanceOf(RestconfDocumentedException.class));
        final var errors = ((RestconfDocumentedException) ex).getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    abstract @NonNull RestconfStrategy testNegativeDeleteDataStrategy();

    @Test
    public final void testPostContainerData() {
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var response = PostDataTransactionUtil.postData(uriInfo, JUKEBOX_IID, EMPTY_JUKEBOX,
            testPostContainerDataStrategy(), JUKEBOX_SCHEMA, WriteDataParams.empty());
        assertEquals(201, response.getStatus());
    }

    abstract @NonNull RestconfStrategy testPostContainerDataStrategy();

    @Test
    public final void testPostListData() {
        doReturn(UriBuilder.fromUri("http://localhost:8181/rests/")).when(uriInfo).getBaseUriBuilder();

        final var entryNode = PLAYLIST.body().iterator().next();
        final var identifier = entryNode.name();
        final var response = PostDataTransactionUtil.postData(uriInfo, PLAYLIST_IID, PLAYLIST,
            testPostListDataStrategy(entryNode, PLAYLIST_IID.node(identifier)), JUKEBOX_SCHEMA,
            WriteDataParams.empty());
        assertEquals(201, response.getStatus());
        assertThat(URLDecoder.decode(response.getLocation().toString(), StandardCharsets.UTF_8),
            containsString(identifier.getValue(identifier.keySet().iterator().next()).toString()));
    }

    abstract @NonNull RestconfStrategy testPostListDataStrategy(MapEntryNode entryNode, YangInstanceIdentifier node);

    @Test
    public final void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> PostDataTransactionUtil.postData(uriInfo, JUKEBOX_IID, EMPTY_JUKEBOX,
                testPostDataFailStrategy(domException), JUKEBOX_SCHEMA, WriteDataParams.empty()));
        assertEquals(1, ex.getErrors().size());
        assertThat(ex.getErrors().get(0).getErrorInfo(), containsString(domException.getMessage()));
    }

    abstract @NonNull RestconfStrategy testPostDataFailStrategy(DOMException domException);

    @Test
    public final void testPatchContainerData() {
        testPatchContainerDataStrategy().merge(JUKEBOX_IID, EMPTY_JUKEBOX, JUKEBOX_SCHEMA).getOrThrow();
    }

    abstract @NonNull RestconfStrategy testPatchContainerDataStrategy();

    @Test
    public final void testPatchLeafData() {
        testPatchLeafDataStrategy().merge(GAP_IID, GAP_LEAF, JUKEBOX_SCHEMA).getOrThrow();
    }

    abstract @NonNull RestconfStrategy testPatchLeafDataStrategy();

    @Test
    public final void testPatchListData() {
        testPatchListDataStrategy().merge(JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST, JUKEBOX_SCHEMA).getOrThrow();
    }

    abstract @NonNull RestconfStrategy testPatchListDataStrategy();

    @Test
    public final void testPatchDataReplaceMergeAndRemove() {
        final var buildArtistList = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(ARTIST_QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of artist"))
                .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "description of artist"))
                .build())
            .build();

        patch(new PatchContext(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, ARTIST_IID.node(NAME_QNAME)),
            List.of(new PatchEntity("edit1", REPLACE, ARTIST_IID, buildArtistList),
                new PatchEntity("edit2", MERGE, ARTIST_IID, buildArtistList),
                new PatchEntity("edit3", REMOVE, ARTIST_IID)),
            "patchRMRm"), testPatchDataReplaceMergeAndRemoveStrategy(), false);
    }

    abstract @NonNull RestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy();

    @Test
    public final void testPatchDataCreateAndDelete() {
        patch(new PatchContext(InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit1", CREATE, PLAYER_IID, EMPTY_JUKEBOX),
                new PatchEntity("edit2", DELETE, CREATE_AND_DELETE_TARGET)),
            "patchCD"),
            testPatchDataCreateAndDeleteStrategy(), true);
    }

    abstract @NonNull RestconfStrategy testPatchDataCreateAndDeleteStrategy();

    @Test
    public final void testPatchMergePutContainer() {
        patch(new PatchContext(InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit1", MERGE, PLAYER_IID, EMPTY_JUKEBOX)), "patchM"),
            testPatchMergePutContainerStrategy(), false);
    }

    abstract @NonNull RestconfStrategy testPatchMergePutContainerStrategy();

    @Test
    public final void testDeleteNonexistentData() {
        final var patchStatusContext = PatchDataTransactionUtil.patchData(new PatchContext(
            InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, GAP_IID),
            List.of(new PatchEntity("edit", DELETE, CREATE_AND_DELETE_TARGET)), "patchD"),
            deleteNonexistentDataTestStrategy(), JUKEBOX_SCHEMA);
        assertFalse(patchStatusContext.isOk());
    }

    abstract @NonNull RestconfStrategy deleteNonexistentDataTestStrategy();

    abstract void assertTestDeleteNonexistentData(@NonNull PatchStatusContext status);

    @Test
    public final void readDataConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.CONFIG, PATH, readDataConfigTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readDataConfigTestStrategy();

    @Test
    public final void readAllHavingOnlyConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.ALL, PATH, readAllHavingOnlyConfigTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readAllHavingOnlyConfigTestStrategy();

    @Test
    public final void readAllHavingOnlyNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.ALL, PATH_2, readAllHavingOnlyNonConfigTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readAllHavingOnlyNonConfigTestStrategy();

    @Test
    public final void readDataNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.NONCONFIG, PATH_2, readDataNonConfigTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readDataNonConfigTestStrategy();

    @Test
    public final void readContainerDataAllTest() {
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH, readContainerDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readContainerDataAllTestStrategy();

    @Test
    public final void readContainerDataConfigNoValueOfContentTest() {
        assertEquals(Builders.containerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH, readContainerDataConfigNoValueOfContentTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy();

    @Test
    public final void readListDataAllTest() {
        assertEquals(Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3, readListDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readListDataAllTestStrategy();

    @Test
    public final void readOrderedListDataAllTest() {
        assertEquals(Builders.orderedMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3, readOrderedListDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readOrderedListDataAllTestStrategy();

    @Test
    public void readUnkeyedListDataAllTest() {
        assertEquals(Builders.unkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(Builders.unkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next())
                .build())
            .build(), readData(ContentParam.ALL, PATH_3, readUnkeyedListDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readUnkeyedListDataAllTestStrategy();

    @Test
    public final void readLeafListDataAllTest() {
        assertEquals(Builders.<String>leafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(LEAF_SET_NODE_1.body())
                .addAll(LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_PATH, readLeafListDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readLeafListDataAllTestStrategy();

    @Test
    public final void readOrderedLeafListDataAllTest() {
        assertEquals(Builders.<String>orderedLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(ORDERED_LEAF_SET_NODE_1.body())
                .addAll(ORDERED_LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_PATH, readOrderedLeafListDataAllTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readOrderedLeafListDataAllTestStrategy();

    @Test
    public void readDataWrongPathOrNoContentTest() {
        assertNull(readData(ContentParam.CONFIG, PATH_2, readDataWrongPathOrNoContentTestStrategy()));
    }

    abstract @NonNull RestconfStrategy readDataWrongPathOrNoContentTestStrategy();

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param content        type of data to read (config, state, all)
     * @param strategy       {@link RestconfStrategy} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy) {
        return ReadDataTransactionUtil.readData(content, path, strategy, null, mockSchemaContext);
    }

    private static void patch(final PatchContext patchContext, final RestconfStrategy strategy, final boolean failed) {
        final var patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, strategy, JUKEBOX_SCHEMA);
        for (var entity : patchStatusContext.getEditCollection()) {
            if (failed) {
                assertTrue("Edit " + entity.getEditId() + " failed", entity.isOk());
            } else {
                assertTrue(entity.isOk());
            }
        }
        assertTrue(patchStatusContext.isOk());
    }
}
