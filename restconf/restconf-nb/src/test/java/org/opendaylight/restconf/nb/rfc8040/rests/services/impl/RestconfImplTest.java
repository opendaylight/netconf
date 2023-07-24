/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler.REVISION;

import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfImplTest {
    @Test
    public void restImplTest() throws Exception {
        final var context = YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/restconf/impl"));
        final RestconfImpl restconfImpl = new RestconfImpl(() -> DatabindContext.ofModel(context));
        final NormalizedNodePayload libraryVersion = restconfImpl.getLibraryVersion();
        final LeafNode<?> value = (LeafNode<?>) libraryVersion.getData();
        assertEquals(REVISION.toString(), value.body());
    }
}
