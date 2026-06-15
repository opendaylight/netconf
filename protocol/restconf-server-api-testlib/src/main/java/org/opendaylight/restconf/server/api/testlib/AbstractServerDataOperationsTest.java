/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.databind.DatabindPath.Data;
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
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractServerDataOperationsTest extends AbstractJukeboxTest {
    protected static final ContainerNode JUKEBOX_WITH_BANDS = ImmutableNodes.newContainerBuilder()
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
    protected static final ContainerNode JUKEBOX_WITH_PLAYLIST = ImmutableNodes.newContainerBuilder()
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
    protected static final MapNode PLAYLIST = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(PLAYLIST_QNAME))
        .withChild(BAND_ENTRY)
        .build();

    // instance identifier for accessing container node "player"
    protected static final YangInstanceIdentifier PLAYER_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME);
    protected static final YangInstanceIdentifier ARTIST_IID = YangInstanceIdentifier.builder()
        .node(JUKEBOX_QNAME)
        .node(LIBRARY_QNAME)
        .node(ARTIST_QNAME)
        .build();
    protected static final YangInstanceIdentifier ARTIST_CHILD_IID = ARTIST_IID
        .node(NodeIdentifierWithPredicates.of(ARTIST_QNAME, NAME_QNAME, "name of artist"));

    protected static final Data ARTIST_DATA = jukeboxPath(ARTIST_IID);
    protected static final Data ARTIST_CHILD_DATA = jukeboxPath(ARTIST_CHILD_IID);
    protected static final Data PLAYER_DATA = jukeboxPath(PLAYER_IID);

    protected static final DatabindContext MODULES_DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResourceDirectory("/modules"));

    protected static final QName BASE = QName.create("ns", "2016-02-28", "base");
    protected static final QName LIST_KEY_QNAME = QName.create(BASE, "list-key");
    protected static final QName LEAF_LIST_QNAME = QName.create(BASE, "leaf-list");
    protected static final QName LIST_QNAME = QName.create(BASE, "list");
    protected static final QName CONT_QNAME = QName.create(BASE, "cont");

    protected static final NodeIdentifierWithPredicates NODE_WITH_KEY =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue");
    protected static final NodeIdentifierWithPredicates NODE_WITH_KEY_2 =
        NodeIdentifierWithPredicates.of(LIST_QNAME, LIST_KEY_QNAME, "keyValue2");

    protected static final LeafNode<?> CONTENT = ImmutableNodes.leafNode(QName.create(BASE, "leaf-content"), "content");
    protected static final LeafNode<?> CONTENT_2 =
        ImmutableNodes.leafNode(QName.create(BASE, "leaf-content-different"), "content-different");
    protected static final YangInstanceIdentifier PATH = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY)
        .build();
    protected static final YangInstanceIdentifier PATH_2 = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LIST_QNAME)
        .node(NODE_WITH_KEY_2)
        .build();
    protected static final YangInstanceIdentifier PATH_3 = YangInstanceIdentifier.of(CONT_QNAME, LIST_QNAME);
    protected static final MapEntryNode DATA = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT)
        .build();
    protected static final MapEntryNode DATA_2 = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .build();
    protected static final LeafNode<?> CONTENT_LEAF = ImmutableNodes.leafNode(QName.create(BASE, "content"), "test");
    protected static final LeafNode<?> CONTENT_LEAF_2 = ImmutableNodes.leafNode(QName.create(BASE, "content2"),
        "test2");
    protected static final ContainerNode DATA_3 = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(BASE, "container")))
        .withChild(CONTENT_LEAF)
        .build();
    protected static final ContainerNode DATA_4 = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(BASE, "container2")))
        .withChild(CONTENT_LEAF_2)
        .build();
    protected static final MapNode LIST_DATA = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .build();
    protected static final MapNode LIST_DATA_2 = ImmutableNodes.newSystemMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(QName.create(LIST_QNAME, "list")))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    protected static final UserMapNode ORDERED_MAP_NODE_1 = ImmutableNodes.newUserMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .build();
    protected static final UserMapNode ORDERED_MAP_NODE_2 = ImmutableNodes.newUserMapBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(DATA)
        .withChild(DATA_2)
        .build();
    protected static final MapEntryNode CHECK_DATA = ImmutableNodes.newMapEntryBuilder()
        .withNodeIdentifier(NODE_WITH_KEY)
        .withChild(CONTENT_2)
        .withChild(CONTENT)
        .build();
    protected static final LeafSetNode<String> LEAF_SET_NODE_1 = ImmutableNodes.<String>newSystemLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    protected static final LeafSetNode<String> LEAF_SET_NODE_2 = ImmutableNodes.<String>newSystemLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .build();
    protected static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_1 = ImmutableNodes.<String>newUserLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("one")
        .withChildValue("two")
        .build();
    protected static final LeafSetNode<String> ORDERED_LEAF_SET_NODE_2 = ImmutableNodes.<String>newUserLeafSetBuilder()
        .withNodeIdentifier(new NodeIdentifier(LEAF_LIST_QNAME))
        .withChildValue("three")
        .withChildValue("four")
        .build();
    protected static final YangInstanceIdentifier LEAF_SET_NODE_PATH = YangInstanceIdentifier.builder()
        .node(CONT_QNAME)
        .node(LEAF_LIST_QNAME)
        .build();
    protected static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_1 = ImmutableNodes.newUnkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT)
        .build();
    protected static final UnkeyedListEntryNode UNKEYED_LIST_ENTRY_NODE_2 = ImmutableNodes.newUnkeyedListEntryBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(CONTENT_2)
        .build();
    protected static final UnkeyedListNode UNKEYED_LIST_NODE_1 = ImmutableNodes.newUnkeyedListBuilder()
        .withNodeIdentifier(new NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_1)
        .build();
    protected static final UnkeyedListNode UNKEYED_LIST_NODE_2 = ImmutableNodes.newUnkeyedListBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(LIST_QNAME))
        .withChild(UNKEYED_LIST_ENTRY_NODE_2)
        .build();
    protected static final NodeIdentifier NODE_IDENTIFIER =
        new NodeIdentifier(QName.create("ns", "2016-02-28", "container"));

    protected static Data moudlesPath(final YangInstanceIdentifier path, final DatabindContext modules) {
        final var childAndStack = modules.schemaTree().enterPath(path).orElseThrow();
        return new Data(modules, childAndStack.stack().toInference(), path, childAndStack.node());
    }
}
