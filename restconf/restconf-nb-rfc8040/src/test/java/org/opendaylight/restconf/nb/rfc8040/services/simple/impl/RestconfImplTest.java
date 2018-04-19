/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RestconfImplTest {

    @Test
    public void restImplTest() throws Exception {
        final SchemaContext schemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/restconf/impl"));
        final SchemaContextHandler schemaContextHandler = TestUtils.newSchemaContextHandler(schemaContext);
        final RestconfImpl restconfImpl = new RestconfImpl(schemaContextHandler);
        final NormalizedNodeContext libraryVersion = restconfImpl.getLibraryVersion();
        final LeafNode<?> value = (LeafNode<?>) libraryVersion.getData();
        Assert.assertEquals(IetfYangLibrary.REVISION, value.getValue());
    }
}
