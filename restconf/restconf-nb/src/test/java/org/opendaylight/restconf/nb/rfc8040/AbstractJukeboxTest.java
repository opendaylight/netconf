/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.yangtools.yang.common.QName;
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

    protected static EffectiveModelContext JUKEBOX_SCHEMA;

    @BeforeClass
    public static void beforeClass() {
        JUKEBOX_SCHEMA = YangParserTestUtils.parseYangResourceDirectory("/jukebox");
    }

    @AfterClass
    public static void afterClass() {
        JUKEBOX_SCHEMA = null;
    }
}
