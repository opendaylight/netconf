/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class ResourceBodyTest extends AbstractJukeboxTest {
    @Test
    public void testValidTopLevelNodeName() {
        assertSame(GAP_IID, ResourceBody.validTopLevelNodeName(GAP_IID, GAP_LEAF));
    }

    @Test
    public void testValidTopLevelNodeNamePathEmpty() {
        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> ResourceBody.validTopLevelNodeName(YangInstanceIdentifier.of(), GAP_LEAF));
    }

    @Test
    public void testValidTopLevelNodeNameWrongTopIdentifier() {
        // FIXME: more asserts
        assertThrows(RestconfDocumentedException.class,
            () -> ResourceBody.validTopLevelNodeName(PLAYLIST_IID, GAP_LEAF));
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() {
        final var path = YangInstanceIdentifier.builder()
            .node(JUKEBOX_QNAME)
            .node(PLAYLIST_QNAME)
            .nodeWithKey(PLAYLIST_QNAME, NAME_QNAME, "name of band")
            .build();
        final var iidContext = InstanceIdentifierContext.ofLocalPath(JUKEBOX_SCHEMA, path);
        ResourceBody.validateListKeysEqualityInPayloadAndUri(iidContext.getSchemaNode(), path, BAND_ENTRY);
    }
}
