/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NodeContainerProxyTest {
    private static final QName QNAME = QName.create("ns", "2016-10-19", "name");
    private static final QName NODE_1_QNAME = QName.create(QNAME, "node-1");
    private static final QName NODE_2_QNAME = QName.create(QNAME, "node-2");

    @Mock
    private AugmentationSchemaNode augSchema1;
    @Mock
    private AugmentationSchemaNode augSchema2;
    @Mock
    private DataSchemaNode schemaNode1;
    @Mock
    private DataSchemaNode schemaNode2;
    private NodeContainerProxy proxy;

    @Before
    public void setUp() {
        proxy = new NodeContainerProxy(QNAME, SchemaPath.SAME,
            Map.of(NODE_1_QNAME, schemaNode1, NODE_2_QNAME, schemaNode2), Set.of(augSchema1, augSchema2));
    }

    @Test
    public void testGetQName() {
        assertSame(QNAME, proxy.getQName());
    }

    @Test
    @Deprecated
    public void testGetPath() {
        assertSame(SchemaPath.SAME, proxy.getPath());
    }

    @Test
    public void testGetChildNodes() {
        final var children = proxy.getChildNodes();
        assertEquals(2, children.size());
        assertThat(children, containsInAnyOrder(schemaNode1, schemaNode2));
    }

    @Test
    public void testGetAvailableAugmentations() {
        final var augmentations = proxy.getAvailableAugmentations();
        assertEquals(2, augmentations.size());
        assertThat(augmentations, containsInAnyOrder(augSchema1, augSchema2));
    }

    @Test
    public void testFindDataChildByName() {
        assertEquals(Optional.of(schemaNode1), proxy.findDataChildByName(NODE_1_QNAME));
    }

    @Test
    public void testGetTypeDefinitions() {
        assertEmpty(proxy.getTypeDefinitions());
    }

    @Test
    public void testGetGroupings() {
        assertEmpty(proxy.getGroupings());
    }

    @Test
    public void testGetUses() {
        assertEmpty(proxy.getUses());
    }

    @Test
    public void testGetUnknownSchemaNodes() {
        assertEmpty(proxy.getUnknownSchemaNodes());
    }

    @Test
    public void testIsPresenceContainer() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.isPresenceContainer());
    }

    @Test
    @Deprecated
    public void testIsAugmenting() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.isAugmenting());
    }

    @Test
    @Deprecated
    public void testIsAddedByUses() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.isAddedByUses());
    }

    @Test
    public void testIsConfiguration() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.isConfiguration());
    }

    @Test
    public void testGetDescription() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.getDescription());
    }

    @Test
    public void testGetReference() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.getReference());
    }

    @Test
    public void testGetStatus() {
        assertThrows(UnsupportedOperationException.class, () -> proxy.getStatus());
    }

    static void assertEmpty(final Collection<?> coll) {
        assertEquals(List.of(), List.copyOf(coll));
    }
}
