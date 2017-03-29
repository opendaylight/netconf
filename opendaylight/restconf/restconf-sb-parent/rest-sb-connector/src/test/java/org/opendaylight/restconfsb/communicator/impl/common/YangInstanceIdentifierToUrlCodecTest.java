/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.common;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.restconfsb.communicator.impl.xml.RetestUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class YangInstanceIdentifierToUrlCodecTest {

    public static final String NS = "http://example.com/ns/example-jukebox";
    public static final String REV = "2013-12-21";

    private static final QName JUKEBOX = QName.create(NS, REV, "jukebox");
    private static final QName LIBRARY = QName.create(JUKEBOX, "library");
    private static final QName LISTVAL1 = QName.create(JUKEBOX, "listVal1");
    private static final QName LISTVAL2 = QName.create(JUKEBOX, "listVal2");
    private static final QName LIST_KEY1 = QName.create(JUKEBOX, "keyVal1");
    private static final QName LIST_KEY2 = QName.create(JUKEBOX, "keyVal2");
    private static final QName LIST_KEY3 = QName.create(JUKEBOX, "keyVal3");
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
    private static final String KEY_NAME1 = "Key test1";
    private static final String KEY_NAME2 = "Key test2";
    private static final String KEY_NAME3 = "Key test3";

    private SchemaContext schemaContext;
    private YangInstanceIdentifierToUrlCodec codec;

    @Before
    public void setUp() throws Exception {
        final List<InputStream> streams = new ArrayList<>();
        streams.add(getClass().getResourceAsStream("/yang/example-jukebox@2013-12-21.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-restconf@2014-02-13.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-yang-types@2010-09-24.yang"));
        streams.add(getClass().getResourceAsStream("/yang/ietf-inet-types@2010-09-24.yang"));
        streams.add(getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));
        schemaContext = RetestUtils.parseYangStreams(streams);
        codec = new YangInstanceIdentifierToUrlCodec(schemaContext);
    }

    @Test
    public void testSerialize() throws Exception {
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(JUKEBOX)
                .node(LIBRARY)
                .node(ARTIST)
                .nodeWithKey(ARTIST, ARTIST_KEY, ARTIST_NAME)
                .node(ALBUM)
                .nodeWithKey(ALBUM, ALBUM_KEY, ALBUM_NAME)
                .node(GENRE)
                .build();
        final String serialize2 = codec.serialize(id);
        System.out.println(serialize2);
        Assert.assertEquals(id, codec.deserialize(serialize2));
    }

    @Test
    public void testDeserialize() throws Exception {
        final QName root = QName.create("urn:dummy:mod", "2016-03-01", "root");
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(root)
                .node(QName.create(root, "ch"))
                .node(QName.create(root, "leaf-case"))
                .build();
        final String serialize2 = codec.serialize(id);
        System.out.println(serialize2);
        Assert.assertEquals(id, codec.deserialize(serialize2));
    }

    @Test
    public void testSerializeDeserializeList() {
        final String expectedSerializedString = "/example-jukebox:jukebox/library/listVal1=Key test1,Key test2,Key test3/listVal2=Key test1,Key test2,Key test3";
        final Map<QName, Object> keys = new HashMap<>();
        keys.put(LIST_KEY1, KEY_NAME1);
        keys.put(LIST_KEY2, KEY_NAME2);
        keys.put(LIST_KEY3, KEY_NAME3);
        final YangInstanceIdentifier id = YangInstanceIdentifier.builder()
                .node(JUKEBOX)
                .node(LIBRARY)
                .node(LISTVAL1)
                .nodeWithKey(LISTVAL1, keys)
                .node(LISTVAL2)
                .nodeWithKey(LISTVAL2, keys)
                .build();
        final String serialize = codec.serialize(id);
        Assert.assertEquals(expectedSerializedString, serialize);
        Assert.assertEquals(id, codec.deserialize(serialize));
    }

}