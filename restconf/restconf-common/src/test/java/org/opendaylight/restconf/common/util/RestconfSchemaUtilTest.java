/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.util;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Unit tests for {@link RestconfSchemaUtil}.
 */
public class RestconfSchemaUtilTest {

    @Test
    public void findInCollectionTest() {
        final SchemaNode origSchNode = mockSchemaNode("key");
        final SchemaNode actualSch = findSchemaNodeInCollection("key", origSchNode);
        Assert.assertEquals(origSchNode, actualSch);
    }

    @Test(expected = RuntimeException.class)
    public void findInCollectionFailedTest() {
        final SchemaNode origSchNode = mockSchemaNode("key");
        findSchemaNodeInCollection("bad_key", origSchNode);
    }

    private static SchemaNode findSchemaNodeInCollection(final String key, final SchemaNode... origSchNode) {
        final List<SchemaNode> collection = new ArrayList<>();
        for (final SchemaNode element : origSchNode) {
            collection.add(element);
        }
        return RestconfSchemaUtil.findSchemaNodeInCollection(collection, key);
    }

    private static SchemaNode mockSchemaNode(final String origKey) {
        final SchemaNode mockSchNode = Mockito.mock(SchemaNode.class);
        Mockito.when(mockSchNode.getQName())
                .thenReturn(QName.create("ns", "2016-10-10", origKey));
        return mockSchNode;
    }
}
