/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfImplTest {
    @Test
    public void restImplTest() {
        final var context = YangParserTestUtils.parseYangResourceDirectory("/restconf/impl");
        final var restconfImpl = new RestconfImpl(() -> DatabindContext.ofModel(context));
        final var libraryVersion = restconfImpl.getLibraryVersion();
        assertEquals("2019-01-04", libraryVersion.getData().body());
    }
}
