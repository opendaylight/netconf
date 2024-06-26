/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.server.api.AbstractServerRequest;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.ServerException;
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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.DOMException;

abstract class AbstractRestconfStrategyTest extends AbstractJukeboxTest {
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

    @Mock
    private EffectiveModelContext mockSchemaContext;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private AbstractServerRequest<Empty> dataDeleteRequest;
    @Mock
    private AbstractServerRequest<DataPatchResult> dataPatchRequest;
    @Captor
    private ArgumentCaptor<DataPatchResult> dataPatchCaptor;
    @Mock
    private AbstractServerRequest<DataPostResult> dataPostRequest;
    @Captor
    private ArgumentCaptor<DataPostResult> dataPostCaptor;
    @Mock
    private AbstractServerRequest<DataYangPatchResult> dataYangPatchRequest;
    @Captor
    private ArgumentCaptor<DataYangPatchResult> dataYangPatchCaptor;
    @Mock
    AbstractServerRequest<DataPutResult> dataPutRequest;

    private DatabindContext mockDatabind;

    @BeforeEach
    void initMockDatabind() {
        mockDatabind = DatabindContext.ofModel(mockSchemaContext);
    }

    abstract @NonNull DefaultRestconfStrategy newStrategy(DatabindContext databind);

    final @NonNull DefaultRestconfStrategy jukeboxStrategy() {
        return newStrategy(JUKEBOX_DATABIND);
    }

    final @NonNull DefaultRestconfStrategy mockStrategy() {
        return newStrategy(mockDatabind);
    }

    /**
     * Test of successful DELETE operation.
     */
    @Test
    final void testDeleteData() throws Exception {
        testDeleteDataStrategy().dataDELETE(dataDeleteRequest, ApiPath.empty());
        verify(dataDeleteRequest).completeWith(Empty.value());
    }

    abstract @NonNull DefaultRestconfStrategy testDeleteDataStrategy();

    /**
     * Negative test for DELETE operation when data to delete does not exist. Error DATA_MISSING is expected.
     */
    @Test
    final void testNegativeDeleteData() {
        testNegativeDeleteDataStrategy().dataDELETE(dataDeleteRequest, ApiPath.empty());

        final var captor = ArgumentCaptor.forClass(RestconfDocumentedException.class);
        verify(dataDeleteRequest).completeWith(captor.capture());

        final var errors = captor.getValue().getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    abstract @NonNull DefaultRestconfStrategy testNegativeDeleteDataStrategy();

    @Test
    final void testPostContainerData() {
        testPostContainerDataStrategy().postData(dataPostRequest, JUKEBOX_IID, EMPTY_JUKEBOX, null);
    }

    abstract @NonNull DefaultRestconfStrategy testPostContainerDataStrategy();

    @Test
    final void testPostListData() {
        testPostListDataStrategy(BAND_ENTRY, PLAYLIST_IID.node(BAND_ENTRY.name()))
            .postData(dataPostRequest, PLAYLIST_IID, PLAYLIST, null);
    }

    abstract @NonNull DefaultRestconfStrategy testPostListDataStrategy(MapEntryNode entryNode,
            YangInstanceIdentifier node);

    @Test
    final void testPostDataFail() {
        final var domException = new DOMException((short) 414, "Post request failed");
        testPostDataFailStrategy(domException).postData(dataPostRequest, JUKEBOX_IID, EMPTY_JUKEBOX, null);

        final var captor = ArgumentCaptor.forClass(RestconfDocumentedException.class);
        verify(dataPostRequest).completeWith(captor.capture());

        final var errors = captor.getValue().getErrors();
        assertEquals(1, errors.size());
        assertThat(errors.get(0).getErrorInfo(), containsString(domException.getMessage()));
    }

    abstract @NonNull DefaultRestconfStrategy testPostDataFailStrategy(DOMException domException);

    @Test
    final void testPatchContainerData() {
        testPatchContainerDataStrategy().merge(dataPatchRequest, JUKEBOX_IID, EMPTY_JUKEBOX);
        verify(dataPatchRequest).completeWith(dataPatchCaptor.capture());
    }

    abstract @NonNull DefaultRestconfStrategy testPatchContainerDataStrategy();

    @Test
    final void testPatchLeafData() {
        testPatchLeafDataStrategy().merge(dataPatchRequest, GAP_IID, GAP_LEAF);
        verify(dataPatchRequest).completeWith(dataPatchCaptor.capture());
    }

    abstract @NonNull DefaultRestconfStrategy testPatchLeafDataStrategy();

    @Test
    final void testPatchListData() {
        testPatchListDataStrategy().merge(dataPatchRequest, JUKEBOX_IID, JUKEBOX_WITH_PLAYLIST);
        verify(dataPatchRequest).completeWith(dataPatchCaptor.capture());
    }

    abstract @NonNull DefaultRestconfStrategy testPatchListDataStrategy();

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
            List.of(new PatchEntity("edit1", Operation.Replace, ARTIST_IID, buildArtistList),
                new PatchEntity("edit2", Operation.Merge, ARTIST_IID, buildArtistList),
                new PatchEntity("edit3", Operation.Remove, ARTIST_IID))),
            testPatchDataReplaceMergeAndRemoveStrategy(), false);
    }

    abstract @NonNull DefaultRestconfStrategy testPatchDataReplaceMergeAndRemoveStrategy();

    @Test
    final void testPatchDataCreateAndDelete() {
        patch(new PatchContext("patchCD", List.of(
            new PatchEntity("edit1", Operation.Create, PLAYER_IID, EMPTY_JUKEBOX),
            new PatchEntity("edit2", Operation.Delete, CREATE_AND_DELETE_TARGET))),
            testPatchDataCreateAndDeleteStrategy(), true);
    }

    abstract @NonNull DefaultRestconfStrategy testPatchDataCreateAndDeleteStrategy();

    @Test
    final void testPatchMergePutContainer() {
        patch(new PatchContext("patchM", List.of(new PatchEntity("edit1", Operation.Merge, PLAYER_IID, EMPTY_JUKEBOX))),
            testPatchMergePutContainerStrategy(), false);
    }

    abstract @NonNull DefaultRestconfStrategy testPatchMergePutContainerStrategy();

    @Test
    final void testDeleteNonexistentData() {
        deleteNonexistentDataTestStrategy().patchData(dataYangPatchRequest,
            new PatchContext("patchD", List.of(new PatchEntity("edit", Operation.Delete, CREATE_AND_DELETE_TARGET))));

        verify(dataYangPatchRequest).completeWith(dataYangPatchCaptor.capture());
        final var status = dataYangPatchCaptor.getValue().status();
        assertEquals("patchD", status.patchId());
        assertFalse(status.ok());
        final var edits = status.editCollection();
        assertEquals(1, edits.size());
        final var edit = edits.get(0);
        assertEquals("edit", edit.getEditId());
        assertTestDeleteNonexistentData(status, edit);
    }

    abstract @NonNull DefaultRestconfStrategy deleteNonexistentDataTestStrategy();

    abstract void assertTestDeleteNonexistentData(@NonNull PatchStatusContext status, @NonNull PatchStatusEntity edit);

    @Test
    final void readDataConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.CONFIG, PATH, readDataConfigTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readDataConfigTestStrategy();

    @Test
    final void readAllHavingOnlyConfigTest() {
        assertEquals(DATA_3, readData(ContentParam.ALL, PATH, readAllHavingOnlyConfigTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readAllHavingOnlyConfigTestStrategy();

    @Test
    final void readAllHavingOnlyNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.ALL, PATH_2, readAllHavingOnlyNonConfigTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readAllHavingOnlyNonConfigTestStrategy();

    @Test
    final void readDataNonConfigTest() {
        assertEquals(DATA_2, readData(ContentParam.NONCONFIG, PATH_2, readDataNonConfigTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readDataNonConfigTestStrategy();

    @Test
    final void readContainerDataAllTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH, readContainerDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readContainerDataAllTestStrategy();

    @Test
    final void readContainerDataConfigNoValueOfContentTest() {
        assertEquals(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NODE_IDENTIFIER)
            .withChild(CONTENT_LEAF)
            .withChild(CONTENT_LEAF_2)
            .build(), readData(ContentParam.ALL, PATH, readContainerDataConfigNoValueOfContentTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readContainerDataConfigNoValueOfContentTestStrategy();

    @Test
    final void readListDataAllTest() {
        assertEquals(ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(QName.create("ns", "2016-02-28", "list")))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3, readListDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readListDataAllTestStrategy();

    @Test
    final void readOrderedListDataAllTest() {
        assertEquals(ImmutableNodes.newUserMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(CHECK_DATA)
            .build(), readData(ContentParam.ALL, PATH_3, readOrderedListDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readOrderedListDataAllTestStrategy();

    @Test
    void readUnkeyedListDataAllTest() {
        assertEquals(ImmutableNodes.newUnkeyedListBuilder()
            .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
            .withChild(ImmutableNodes.newUnkeyedListEntryBuilder()
                .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
                .withChild(UNKEYED_LIST_ENTRY_NODE_1.body().iterator().next())
                .withChild(UNKEYED_LIST_ENTRY_NODE_2.body().iterator().next())
                .build())
            .build(), readData(ContentParam.ALL, PATH_3, readUnkeyedListDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readUnkeyedListDataAllTestStrategy();

    @Test
    final void readLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newSystemLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(LEAF_SET_NODE_1.body())
                .addAll(LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_PATH, readLeafListDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readLeafListDataAllTestStrategy();

    @Test
    final void readOrderedLeafListDataAllTest() {
        assertEquals(ImmutableNodes.<String>newUserLeafSetBuilder()
            .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
            .withValue(ImmutableList.<LeafSetEntryNode<String>>builder()
                .addAll(ORDERED_LEAF_SET_NODE_1.body())
                .addAll(ORDERED_LEAF_SET_NODE_2.body())
                .build())
            .build(), readData(ContentParam.ALL, LEAF_SET_NODE_PATH, readOrderedLeafListDataAllTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readOrderedLeafListDataAllTestStrategy();

    @Test
    void readDataWrongPathOrNoContentTest() {
        assertNull(readData(ContentParam.CONFIG, PATH_2, readDataWrongPathOrNoContentTestStrategy()));
    }

    abstract @NonNull DefaultRestconfStrategy readDataWrongPathOrNoContentTestStrategy();

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param content        type of data to read (config, state, all)
     * @param strategy       {@link DefaultRestconfStrategy} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final YangInstanceIdentifier path, final @NonNull DefaultRestconfStrategy strategy) {
        try {
            return strategy.readData(content, path, null);
        } catch (ServerException e) {
            throw new AssertionError(e);
        }
    }

    private void patch(final PatchContext patchContext, final DefaultRestconfStrategy strategy, final boolean failed) {
        strategy.patchData(dataYangPatchRequest, patchContext);
        verify(dataYangPatchRequest).completeWith(dataYangPatchCaptor.capture());
        final var patchStatusContext = dataYangPatchCaptor.getValue().status();

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
