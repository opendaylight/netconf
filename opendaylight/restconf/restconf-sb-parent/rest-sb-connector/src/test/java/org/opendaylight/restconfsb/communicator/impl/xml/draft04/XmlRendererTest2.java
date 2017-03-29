/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.impl.xml.RetestUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class XmlRendererTest2 {

    public static final String NS = "http://example.com/ns/example-jukebox";
    public static final String REV = "2013-12-21";

    private static final QName JUKEBOX = QName.create(NS, REV, "jukebox");
    private static final QName LIBRARY = QName.create(JUKEBOX, "library");
    private static final QName ARTIST = QName.create(JUKEBOX, "artist");
    private static final QName ARTIST_KEY = QName.create(JUKEBOX, "name");
    private static final QName ALBUM = QName.create(JUKEBOX, "album");
    private static final QName ALBUM_KEY = QName.create(JUKEBOX, "name");
    private static final QName GENRE = QName.create(JUKEBOX, "genre");
    private static final QName YEAR = QName.create(JUKEBOX, "year");

    private static final String ARTIST_NAME = "Foo Fighters";
    private static final String ALBUM_NAME = "Wasting Light";
    private static final String GENRE_NAME = "example-jukebox:rock";
    private static final String YEAR_VALUE = "2011";

    private static final String EXPECTED_PATH = "example-jukebox:jukebox/library/artist/Foo Fighters/album/Wasting Light/";

    private static final String EXPECTED_XML =
            "<album xmlns=\"http://example.com/ns/example-jukebox\">\n" +
            "    <name>Wasting Light</name>\n" +
            "    <genre>example-jukebox:rock</genre>\n" +
            "    <year>2011</year>\n" +
            "</album>";
    private static final String EXPECTED_RPC_INPUT = "<input xmlns=\"http://example.com/ns/example-jukebox\">" +
            "<playlist>playlist-name</playlist>" +
            "<song-number>song-number-value</song-number>" +
            "</input>";

    private XmlRenderer renderer;

    @BeforeClass
    public static void suiteSetup() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Before
    public void setUp() throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(getClass().getResourceAsStream("/yang/example-jukebox@2013-12-21.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-restconf@2014-02-13.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-yang-types@2010-09-24.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-inet-types@2010-09-24.yang"));
        final SchemaContext schemaContext = RetestUtils.parseYangStreams(streams);
        renderer = new XmlRenderer(schemaContext);
    }

    @Test
    public void testWriteReqBody() throws Exception {
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(JUKEBOX)
                .node(LIBRARY)
                .node(ARTIST)
                .nodeWithKey(ARTIST, ARTIST_KEY, ARTIST_NAME)
                .node(ALBUM)
                .nodeWithKey(ALBUM, ALBUM_KEY, ALBUM_NAME)
                .node(GENRE)
                .build();
        final LeafNode<Object> genre = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(GENRE))
                .withValue(GENRE_NAME)
                .build();
        final LeafNode<Object> year = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(YEAR))
                .withValue(YEAR_VALUE)
                .build();
        final MapEntryNode album = Builders.mapEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifierWithPredicates(ALBUM, ALBUM_KEY, ALBUM_NAME))
                .withChild(genre)
                .withChild(year)
                .build();
        final Request request = renderer.renderEditConfig(id, album);
        final String actual = request.getBody();
        final Diff diff = XMLUnit.compareXML(EXPECTED_XML, actual);
        Assert.assertTrue(diff.toString(), diff.similar());
    }

    @Test
    public void testRenderRpc() throws Exception {
        final LeafNode<Object> playlist = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(JUKEBOX, "playlist")))
                .withValue("playlist-name")
                .build();
        final LeafNode<Object> songNumber = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(JUKEBOX, "song-number")))
                .withValue("song-number-value")
                .build();
        final ContainerNode containerNode = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(JUKEBOX, "input")))
                .withChild(playlist)
                .withChild(songNumber)
                .build();
        final SchemaPath path = SchemaPath.create(true, QName.create(JUKEBOX, "play"));
        final Request request = renderer.renderOperation(path, containerNode);
        Assert.assertEquals("/operations/example-jukebox:play", request.getPath());
        final Diff diff = XMLUnit.compareXML(EXPECTED_RPC_INPUT, request.getBody());
        Assert.assertTrue(diff.toString(), diff.similar());
    }
}
