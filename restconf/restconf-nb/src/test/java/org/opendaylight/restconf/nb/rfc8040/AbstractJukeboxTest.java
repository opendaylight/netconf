/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractJukeboxTest {
    // container jukebox
    protected static final QName JUKEBOX_QNAME =
        QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
    // container player
    protected static final QName PLAYER_QNAME = QName.create(JUKEBOX_QNAME, "player");
    // container library
    protected static final QName LIBRARY_QNAME = QName.create(JUKEBOX_QNAME, "library");
    // list song
    protected static final QName SONG_QNAME = QName.create(JUKEBOX_QNAME, "song");
    // leaf id
    protected static final QName SONG_ID_QNAME = QName.create(JUKEBOX_QNAME, "id");
    // leaf index
    protected static final QName SONG_INDEX_QNAME = QName.create(JUKEBOX_QNAME, "index");
    // list artist
    protected static final QName ARTIST_QNAME = QName.create(JUKEBOX_QNAME, "artist");
    // list album
    protected static final QName ALBUM_QNAME = QName.create(JUKEBOX_QNAME, "album");
    // leaf name
    protected static final QName NAME_QNAME = QName.create(JUKEBOX_QNAME, "name");
    // leaf gap
    protected static final QName GAP_QNAME = QName.create(JUKEBOX_QNAME, "gap");
    // leaf playlist
    protected static final QName PLAYLIST_QNAME = QName.create(JUKEBOX_QNAME, "playlist");
    // leaf description
    protected static final QName DESCRIPTION_QNAME = QName.create(JUKEBOX_QNAME, "description");

    protected static final YangInstanceIdentifier JUKEBOX_IID = YangInstanceIdentifier.of(JUKEBOX_QNAME);
    protected static final YangInstanceIdentifier PLAYLIST_IID =
        YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYLIST_QNAME);

    // instance identifier for accessing leaf node "gap"
    protected static final YangInstanceIdentifier GAP_IID =
        YangInstanceIdentifier.of(JUKEBOX_QNAME, PLAYER_QNAME, GAP_QNAME);

    protected static final LeafNode<?> GAP_LEAF = ImmutableNodes.leafNode(GAP_QNAME, Decimal64.valueOf("0.2"));
    protected static final ContainerNode CONT_PLAYER = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(PLAYER_QNAME))
        .withChild(GAP_LEAF)
        .build();
    protected static final ContainerNode EMPTY_JUKEBOX = Builders.containerBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(CONT_PLAYER)
        .build();
    protected static final MapEntryNode BAND_ENTRY = Builders.mapEntryBuilder()
        .withNodeIdentifier(NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME, "name of band"))
        .withChild(ImmutableNodes.leafNode(NAME_QNAME, "name of band"))
        .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, "band description"))
        .build();
    protected static final MapEntryNode SONG1 = Builders.mapEntryBuilder()
        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME,
            "1"))
        .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "A"))
        .build();
    protected static final MapEntryNode SONG2 = Builders.mapEntryBuilder()
        .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(SONG_QNAME, SONG_INDEX_QNAME,
            "2"))
        .withChild(ImmutableNodes.leafNode(SONG_ID_QNAME, "B"))
        .build();
    protected static final SystemMapNode PLAYLIST_WITH_SONGS = Builders.mapBuilder()
        .withNodeIdentifier(new NodeIdentifier(JUKEBOX_QNAME))
        .withChild(Builders.mapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(PLAYLIST_QNAME, NAME_QNAME,
                "0"))
                .withChild(Builders.orderedMapBuilder()
                    .withNodeIdentifier(new NodeIdentifier(SONG_QNAME))
                    .withChild(SONG1)
                    .withChild(SONG2)
                    .build())
                .build())
            .build();

    protected static final @NonNull EffectiveModelContext JUKEBOX_SCHEMA =
        YangParserTestUtils.parseYangResourceDirectory("/jukebox");

    protected static final InputStream stringInputStream(final String str) {
        return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }
}
