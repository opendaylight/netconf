/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.streams.listeners;

import com.google.common.collect.Lists;
import java.util.ArrayList;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
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

    @Test
    public void testWithAugment() {
        final SchemaPath schemaPathNotifi =
                SchemaPath.create(true, QName.create("http://example.com/schema/interfaces", "2016-10-07", "notifi"));
        final List<SchemaPath> paths = Lists.newArrayList(schemaPathNotifi);
        final NotificationListenerAdapter notificationListenerAdapter =
                Notificator.createNotificationListener(paths, "streamName", "JSON").get(0);
        Assert.assertNotNull(notificationListenerAdapter);
        final DOMNotification notification = Mockito.mock(DOMNotification.class);
        prepareNotifiWithAugment(notification, schemaPathNotifi);

        notificationListenerAdapter.onNotification(notification);
    }

    @Test
    public void testWithChoice() {
        final SchemaPath schemaPathNotifi =
                SchemaPath.create(true, QName.create("http://example.com/schema/interfaces", "2016-10-07", "notifi"));
        final List<SchemaPath> paths = Lists.newArrayList(schemaPathNotifi);
        final NotificationListenerAdapter notificationListenerAdapter =
                Notificator.createNotificationListener(paths, "streamName", "JSON").get(0);
        Assert.assertNotNull(notificationListenerAdapter);
        final DOMNotification notification = Mockito.mock(DOMNotification.class);
        prepareNotifiWithChoice(notification, schemaPathNotifi);

        notificationListenerAdapter.onNotification(notification);
    }

    private void prepareNotifiWithChoice(final DOMNotification notification, final SchemaPath schemaPathNotifi) {
        Mockito.when(notification.getType()).thenReturn(schemaPathNotifi);

        final ContainerNode node = Mockito.mock(ContainerNode.class);
        Mockito.when(notification.getBody()).thenReturn(node);
        Mockito.when(node.getIdentifier()).thenReturn(
                NodeIdentifier.create(QName.create("http://example.com/schema/interfaces", "2016-10-07", "notifi")));

        final ContainerNode cCont = Mockito.mock(ContainerNode.class);
        final QName cContQName = QName.create("http://example.com/schema/interfaces", "2016-10-07", "c");
        Mockito.when(cCont.getNodeType()).thenReturn(cContQName);
        Mockito.when(cCont.getIdentifier()).thenReturn(NodeIdentifier.create(cContQName));

        final ChoiceNode choiceNode = Mockito.mock(ChoiceNode.class);
        final QName chChoiceQName = QName.create("http://example.com/schema/interfaces", "2016-10-07", "ch");
        Mockito.when(choiceNode.getNodeType()).thenReturn(chChoiceQName);
        Mockito.when(choiceNode.getIdentifier()).thenReturn(NodeIdentifier.create(chChoiceQName));

        final LeafNode chLeaf = prepareLeaf("http://example.com/schema/interfaces", "2016-10-07", "l", "test");

        Mockito.when(choiceNode.getValue()).thenReturn(preparelistOfChild(chLeaf));
        Mockito.when(cCont.getValue()).thenReturn(preparelistOfChild(choiceNode));
        Mockito.when(node.getValue()).thenReturn(preparelistOfChild(cCont));
    }

    private void prepareNotifiWithAugment(final DOMNotification notification, final SchemaPath schemaPathNotifi) {
        Mockito.when(notification.getType()).thenReturn(schemaPathNotifi);

        final ContainerNode node = Mockito.mock(ContainerNode.class);
        Mockito.when(notification.getBody()).thenReturn(node);
        Mockito.when(node.getIdentifier())
                .thenReturn(NodeIdentifier
                        .create(QName.create("http://example.com/schema/interfaces", "2016-10-07", "notifi")));

        final LeafNode augmentLeaf =
                prepareLeaf("http://example.com/schema/ds0", "2016-10-07", "ds0ChannelNumber", "ds0ChannelNumber=8888");
        final LeafNode ifIndexLeaf =
                prepareLeaf("http://example.com/schema/interfaces", "2016-10-07", "ifIndex", "1");
        final LeafNode ifTypeLeaf = prepareLeaf("http://example.com/schema/interfaces", "2016-10-07", "ifType", "ds0");

        final MapEntryNode mapEntryNodeLeafifIndex = Mockito.mock(MapEntryNode.class);
        final MapEntryNode mapEntryNodeLeafIfType = Mockito.mock(MapEntryNode.class);
        final MapEntryNode mapEntryAugmentNodeDs0 = Mockito.mock(MapEntryNode.class);
        Mockito.when(mapEntryNodeLeafifIndex.getValue()).thenReturn(preparelistOfChild(ifIndexLeaf));
        Mockito.when(mapEntryNodeLeafIfType.getValue()).thenReturn(preparelistOfChild(ifTypeLeaf));
        Mockito.when(mapEntryAugmentNodeDs0.getValue()).thenReturn(preparelistOfChild(augmentLeaf));

        final MapNode listIfEntry = Mockito.mock(MapNode.class);
        final QName mapNodeListIfEntryQName =
                QName.create("http://example.com/schema/interfaces", "2016-10-07", "ifEntry");
        Mockito.when(listIfEntry.getNodeType()).thenReturn(mapNodeListIfEntryQName);
        Mockito.when(listIfEntry.getValue())
                .thenReturn(
                        Lists.newArrayList(mapEntryNodeLeafifIndex, mapEntryNodeLeafIfType, mapEntryAugmentNodeDs0));
        Mockito.when(listIfEntry.getIdentifier()).thenReturn(NodeIdentifier.create(mapNodeListIfEntryQName));

        final ContainerNode interfacesCont = Mockito.mock(ContainerNode.class);
        final QName interfacesContQName = QName.create("http://example.com/schema/interfaces", "2016-10-07", "interfaces");
        Mockito.when(interfacesCont.getNodeType()).thenReturn(interfacesContQName);
        Mockito.when(interfacesCont.getIdentifier()).thenReturn(NodeIdentifier.create(interfacesContQName));
        Mockito.when(interfacesCont.getValue()).thenReturn(preparelistOfChild(listIfEntry));

        Mockito.when(node.getValue()).thenReturn(preparelistOfChild(interfacesCont));
    }

    private void prepareNotifi(final DOMNotification notification, final SchemaPath schemaPathNotification) {
        Mockito.when(notification.getType()).thenReturn(schemaPathNotification);

        final ContainerNode node = Mockito.mock(ContainerNode.class);
        Mockito.when(notification.getBody()).thenReturn(node);
        Mockito.when(node.getIdentifier())
                .thenReturn(NodeIdentifier.create(QName.create("notifi:test", "2016-09-27", "notifi")));

        final LeafNode nodeLeafId = prepareLeaf("urn:system:connector", "2016-05-10", "id", "5");

        final LeafNode leafNameContConfig = prepareLeaf("urn:system:connector", "2016-05-10", "name", "test");
        final LeafNode leafDescrContConfig =
                prepareLeaf("urn:system:connector", "2016-05-10", "description", "connector created for testing");
        final LeafNode leafAdminStatusContConfig =
                prepareLeaf("urn:system:connector", "2016-05-10", "admin-status", "system-connector:unlocked");
        final LeafNode leafHostContConfig = prepareLeaf("urn:system:connector", "2016-05-10", "host", "127.0.0.0");

        final ContainerNode contConfig = Mockito.mock(ContainerNode.class);
        final QName contConfigQName = QName.create("urn:system:connector", "2016-05-10", "config");
        Mockito.when(contConfig.getNodeType()).thenReturn(contConfigQName);
        Mockito.when(contConfig.getValue()).thenReturn(preparelistOfChild(leafAdminStatusContConfig,
                leafDescrContConfig, leafHostContConfig, leafNameContConfig));
        Mockito.when(contConfig.getIdentifier()).thenReturn(NodeIdentifier.create(contConfigQName));

        final MapEntryNode mapEntryNodeContConfig = Mockito.mock(MapEntryNode.class);
        final MapEntryNode mapEntryNodeLeafId = Mockito.mock(MapEntryNode.class);
        Mockito.when(mapEntryNodeLeafId.getValue()).thenReturn(preparelistOfChild(nodeLeafId));
        Mockito.when(mapEntryNodeContConfig.getValue()).thenReturn(preparelistOfChild(contConfig));

        final MapNode mapNodeConnector = Mockito.mock(MapNode.class);
        final QName mapNodeConnQName = QName.create("urn:system:connector", "2016-05-10", "connector");
        Mockito.when(mapNodeConnector.getNodeType()).thenReturn(mapNodeConnQName);
        Mockito.when(mapNodeConnector.getValue())
                .thenReturn(Lists.newArrayList(mapEntryNodeLeafId, mapEntryNodeContConfig));
        Mockito.when(mapNodeConnector.getIdentifier()).thenReturn(NodeIdentifier.create(mapNodeConnQName));

        final ContainerNode connectorsCont = Mockito.mock(ContainerNode.class);
        final QName connectorsQName = QName.create("urn:system:connector", "2016-05-10", "connectors");
        Mockito.when(connectorsCont.getNodeType()).thenReturn(connectorsQName);
        Mockito.when(connectorsCont.getValue()).thenReturn(preparelistOfChild(mapNodeConnector));
        Mockito.when(connectorsCont.getIdentifier()).thenReturn(NodeIdentifier.create(connectorsQName));
        Mockito.when(node.getValue()).thenReturn(preparelistOfChild(connectorsCont));
    }

    private List<DataContainerChild<? extends PathArgument, ?>> preparelistOfChild(
            final DataContainerChild<? extends PathArgument, ?>... childs) {
        final List<DataContainerChild<? extends PathArgument, ?>> list = new ArrayList<DataContainerChild<? extends PathArgument, ?>>();
        for (final DataContainerChild<? extends PathArgument, ?> dataContainerChild : childs) {
            list.add(dataContainerChild);
        }
        return list;
    }

    private LeafNode prepareLeaf(final String namespace, final String revision, final String localName,
            final Object value) {
        final LeafNode leaf = Mockito.mock(LeafNode.class);
        final QName leafIdQName = QName.create(namespace, revision, localName);
        Mockito.when(leaf.getNodeType()).thenReturn(leafIdQName);
        Mockito.when(leaf.getValue()).thenReturn(value);
        Mockito.when(leaf.getIdentifier()).thenReturn(NodeIdentifier.create(leafIdQName));
        return leaf;
    }

}
