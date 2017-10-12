/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

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
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final Map<QName, DataSchemaNode> childNodes = new HashMap<>();
        childNodes.put(NODE_1_QNAME, schemaNode1);
        childNodes.put(NODE_2_QNAME, schemaNode2);
        final Set<AugmentationSchemaNode> augmentations = new HashSet<>();
        augmentations.add(augSchema1);
        augmentations.add(augSchema2);
        proxy = new NodeContainerProxy(QNAME, childNodes, augmentations);
    }

    @Test
    public void testGetQName() throws Exception {
        Assert.assertEquals(QNAME, proxy.getQName());
    }

    @Test
    public void testGetChildNodes() throws Exception {
        Assert.assertEquals(2, proxy.getChildNodes().size());
    }

    @Test
    public void testGetAvailableAugmentations() throws Exception {
        final Set<AugmentationSchemaNode> augmentations = proxy.getAvailableAugmentations();
        Assert.assertEquals(2, augmentations.size());
        Assert.assertTrue(augmentations.contains(augSchema1));
        Assert.assertTrue(augmentations.contains(augSchema2));
    }

    @Test
    public void testGetDataChildByName() throws Exception {
        final DataSchemaNode schemaNode = proxy.getDataChildByName(NODE_1_QNAME);
        Assert.assertEquals(schemaNode1, schemaNode);
    }

    @Test
    public void testGetTypeDefinitions() throws Exception {
        Assert.assertTrue(proxy.getTypeDefinitions().isEmpty());
    }

    @Test
    public void testGetGroupings() throws Exception {
        Assert.assertTrue(proxy.getGroupings().isEmpty());
    }

    @Test
    public void testGetUses() throws Exception {
        Assert.assertTrue(proxy.getUses().isEmpty());
    }

    @Test
    public void testGetUnknownSchemaNodes() throws Exception {
        Assert.assertTrue(proxy.getUnknownSchemaNodes().isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIsPresenceContainer() throws Exception {
        proxy.isPresenceContainer();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIsAugmenting() throws Exception {
        proxy.isAugmenting();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIsAddedByUses() throws Exception {
        proxy.isAddedByUses();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIsConfiguration() throws Exception {
        proxy.isConfiguration();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConstraints() throws Exception {
        proxy.getConstraints();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetPath() throws Exception {
        proxy.getPath();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDescription() throws Exception {
        proxy.getDescription();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetReference() throws Exception {
        proxy.getReference();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetStatus() throws Exception {
        proxy.getStatus();
    }

}