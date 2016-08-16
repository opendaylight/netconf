/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.streams.listeners;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.sal.restconf.impl.test.TestUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NotificationListenerTest {

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        ControllerContext.getInstance().setGlobalSchema(TestUtils.loadSchemaContext("/instanceidentifier"));
    }

    @Test
    public void test() {
        final SchemaPath schemaPathNotification =
                SchemaPath.create(true, QName.create("notifi:test", "2016-09-27", "notifi"));
        final List<SchemaPath> paths = Lists.newArrayList(schemaPathNotification);
        final NotificationListenerAdapter notificationListenerAdapter =
                Notificator.createNotificationListener(paths, "streamName", "JSON").get(0);
        Assert.assertNotNull(notificationListenerAdapter);
        final DOMNotification notification = Mockito.mock(DOMNotification.class);
        prepareNotifi(notification, schemaPathNotification);

        notificationListenerAdapter.onNotification(notification);
    }

    private void prepareNotifi(final DOMNotification notification, final SchemaPath schemaPathNotification) {
        Mockito.when(notification.getType()).thenReturn(schemaPathNotification);

        final ContainerNode node = Mockito.mock(ContainerNode.class);
        Mockito.when(notification.getBody()).thenReturn(node);

        final LeafNode nodeLeafId = prepareLeaf("id", "urn:ogf:network:connector=1473202360280");

        final LeafNode leafNameContConfig = prepareLeaf("name", "test");
        final LeafNode leafDescrContConfig = prepareLeaf("description", "connector created for integration testing");
        final LeafNode leafAdminStatusContConfig =
                prepareLeaf("admin-status", "system-connector:unlocked");
        final LeafNode leafHostContConfig = prepareLeaf("host", "10.100.71.133");

        final ContainerNode contConfig = Mockito.mock(ContainerNode.class);
        final QName contConfigQName = QName.create("urn:system:connector", "2016-05-10", "config");
        Mockito.when(contConfig.getNodeType()).thenReturn(contConfigQName);
        Mockito.when(contConfig.getValue()).thenReturn(Lists.newArrayList(leafNameContConfig, leafDescrContConfig, leafAdminStatusContConfig, leafHostContConfig));

        final MapEntryNode mapEntryNodeContConfig = Mockito.mock(MapEntryNode.class);
        final MapEntryNode mapEntryNodeLeafId = Mockito.mock(MapEntryNode.class);
        Mockito.when(mapEntryNodeLeafId.getValue()).thenReturn(Lists.newArrayList(nodeLeafId));
        Mockito.when(mapEntryNodeContConfig.getValue()).thenReturn(Lists.newArrayList(contConfig));

        final MapNode mapNodeConnector = Mockito.mock(MapNode.class);
        final QName mapNodeConnQName = QName.create("urn:system:connector", "2016-05-10", "connector");
        Mockito.when(mapNodeConnector.getNodeType()).thenReturn(mapNodeConnQName);
        Mockito.when(mapNodeConnector.getValue())
                .thenReturn(Lists.newArrayList(mapEntryNodeLeafId, mapEntryNodeContConfig));
        final Collection<DataContainerChild<? extends PathArgument, ?>> listConnector =
                Lists.newArrayList(mapNodeConnector);

        final ContainerNode connectorsCont = Mockito.mock(ContainerNode.class);
        final QName connectorsQName = QName.create("urn:system:connector", "2016-05-10", "connectors");
        Mockito.when(connectorsCont.getNodeType()).thenReturn(connectorsQName);
        Mockito.when(connectorsCont.getValue()).thenReturn(listConnector);
        final Collection<DataContainerChild<? extends PathArgument, ?>> notifi = Lists.newArrayList(connectorsCont);
        Mockito.when(node.getValue()).thenReturn(notifi);
    }

    private LeafNode prepareLeaf(final String localName, final Object value) {
        final LeafNode leaf = Mockito.mock(LeafNode.class);
        final QName leafIdQName = QName.create("urn:system:connector", "2016-05-10", localName);
        Mockito.when(leaf.getNodeType()).thenReturn(leafIdQName);
        Mockito.when(leaf.getValue()).thenReturn(value);
        return leaf;
    }

}
