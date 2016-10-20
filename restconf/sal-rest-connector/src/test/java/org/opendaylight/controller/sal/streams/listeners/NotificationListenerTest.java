/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.streams.listeners;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.CreateNotificationStreamInput1.NotificationOutputType;
import org.opendaylight.yangtools.util.SingletonSet;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NotificationListenerTest {

    private SchemaContext schmeaCtx;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        ControllerContext.getInstance().setGlobalSchema(TestUtils.loadSchemaContext("/notifications"));
        this.schmeaCtx = ControllerContext.getInstance().getGlobalSchema();
    }

    @Test
    public void notifi_leafTest() throws Exception {
        final QNameModule moduleQName =
                QNameModule.create(new URI("notifi:mod"), new SimpleDateFormat("yyyy-MM-dd").parse("2016-11-23"));
        final SchemaPath schemaPathNotifi = SchemaPath.create(false, QName.create(moduleQName, "notifi-leaf"));

        final DOMNotification notificationData = Mockito.mock(DOMNotification.class);

        final LeafNode leaf = mockLeaf(QName.create(moduleQName, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.getLastComponent(), leaf);

        Mockito.when(notificationData.getType()).thenReturn(schemaPathNotifi);
        Mockito.when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        Assert.assertTrue(result.contains("ietf-restconf:notification"));
        Assert.assertTrue(result.contains("event-time"));
        Assert.assertTrue(result.contains("notifi-module:notifi-leaf"));
        Assert.assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_cont_leafTest() throws Exception {
        final QNameModule moduleQName =
                QNameModule.create(new URI("notifi:mod"), new SimpleDateFormat("yyyy-MM-dd").parse("2016-11-23"));

        final SchemaPath schemaPathNotifi =
                SchemaPath.create(false, QName.create(moduleQName, "notifi-cont"));

        final DOMNotification notificationData = Mockito.mock(DOMNotification.class);

        final LeafNode leaf = mockLeaf(QName.create(moduleQName, "lf"));
        final ContainerNode cont = mockCont(QName.create(moduleQName, "cont"), leaf);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.getLastComponent(), cont);

        Mockito.when(notificationData.getType()).thenReturn(schemaPathNotifi);
        Mockito.when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        Assert.assertTrue(result.contains("ietf-restconf:notification"));
        Assert.assertTrue(result.contains("event-time"));
        Assert.assertTrue(result.contains("notifi-module:notifi-cont"));
        Assert.assertTrue(result.contains("cont"));
        Assert.assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_list_Test() throws Exception {
        final QNameModule moduleQName =
                QNameModule.create(new URI("notifi:mod"), new SimpleDateFormat("yyyy-MM-dd").parse("2016-11-23"));

        final SchemaPath schemaPathNotifi = SchemaPath.create(false, QName.create(moduleQName, "notifi-list"));

        final DOMNotification notificationData = Mockito.mock(DOMNotification.class);

        final LeafNode leaf = mockLeaf(QName.create(moduleQName, "lf"));
        final MapEntryNode entry = mockMapEntry(QName.create(moduleQName, "lst"), leaf);
        final MapNode list = mockList(QName.create(moduleQName, "lst"), entry);
        final ContainerNode cont = mockCont(QName.create(moduleQName, "cont"), list);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.getLastComponent(), cont);

        Mockito.when(notificationData.getType()).thenReturn(schemaPathNotifi);
        Mockito.when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        Assert.assertTrue(result.contains("ietf-restconf:notification"));
        Assert.assertTrue(result.contains("event-time"));
        Assert.assertTrue(result.contains("notifi-module:notifi-list"));
        Assert.assertTrue(result.contains("lst"));
        Assert.assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_grpTest() throws Exception {
        final QNameModule moduleQName =
                QNameModule.create(new URI("notifi:mod"), new SimpleDateFormat("yyyy-MM-dd").parse("2016-11-23"));

        final SchemaPath schemaPathNotifi = SchemaPath.create(false, QName.create(moduleQName, "notifi-grp"));

        final DOMNotification notificationData = Mockito.mock(DOMNotification.class);

        final LeafNode leaf = mockLeaf(QName.create(moduleQName, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.getLastComponent(), leaf);

        Mockito.when(notificationData.getType()).thenReturn(schemaPathNotifi);
        Mockito.when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        Assert.assertTrue(result.contains("ietf-restconf:notification"));
        Assert.assertTrue(result.contains("event-time"));
        Assert.assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_augmTest() throws Exception {
        final QNameModule moduleQName =
                QNameModule.create(new URI("notifi:mod"), new SimpleDateFormat("yyyy-MM-dd").parse("2016-11-23"));

        final SchemaPath schemaPathNotifi = SchemaPath.create(false, QName.create(moduleQName, "notifi-augm"));

        final DOMNotification notificationData = Mockito.mock(DOMNotification.class);

        final LeafNode leaf = mockLeaf(QName.create(moduleQName, "lf-augm"));
        final AugmentationNode augm = mockAugm(leaf);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.getLastComponent(), augm);

        Mockito.when(notificationData.getType()).thenReturn(schemaPathNotifi);
        Mockito.when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        Assert.assertTrue(result.contains("ietf-restconf:notification"));
        Assert.assertTrue(result.contains("event-time"));
        Assert.assertTrue(result.contains("lf-augm" + '"' + ":" + '"' + "value"));
    }

    private AugmentationNode mockAugm(final LeafNode leaf) {
        final AugmentationNode augm = Mockito.mock(AugmentationNode.class);
        final AugmentationIdentifier augmId = new AugmentationIdentifier(SingletonSet.of(leaf.getNodeType()));
        Mockito.when(augm.getIdentifier()).thenReturn(augmId);

        final Collection<DataContainerChild<? extends PathArgument, ?>> childs = new ArrayList<>();
        childs.add(leaf);

        Mockito.when(augm.getValue()).thenReturn(childs);
        return augm;
    }

    private MapEntryNode mockMapEntry(final QName entryQName, final LeafNode leaf) {
        final MapEntryNode entry = Mockito.mock(MapEntryNode.class);
        final Map<QName, Object> keyValues = new HashMap<>();
        keyValues.put(leaf.getNodeType(), "value");
        final NodeIdentifierWithPredicates nodeId = new NodeIdentifierWithPredicates(leaf.getNodeType(), keyValues);
        Mockito.when(entry.getIdentifier()).thenReturn(nodeId);
        Mockito.when(entry.getChild(Mockito.any())).thenReturn(Optional.of(leaf));

        final Collection<DataContainerChild<? extends PathArgument, ?>> childs = new ArrayList<>();
        childs.add(leaf);

        Mockito.when(entry.getValue()).thenReturn(childs);
        return entry;
    }

    private MapNode mockList(final QName listQName, final MapEntryNode... entries) {
        final MapNode list = Mockito.mock(MapNode.class);
        Mockito.when(list.getIdentifier()).thenReturn(NodeIdentifier.create(listQName));
        Mockito.when(list.getNodeType()).thenReturn(listQName);
        Mockito.when(list.getValue()).thenReturn(Lists.newArrayList(entries));
        return list;
    }

    private ContainerNode mockCont(final QName contQName, final DataContainerChild<? extends PathArgument, ?> child) {
        final ContainerNode cont = Mockito.mock(ContainerNode.class);
        Mockito.when(cont.getIdentifier()).thenReturn(NodeIdentifier.create(contQName));
        Mockito.when(cont.getNodeType()).thenReturn(contQName);

        final Collection<DataContainerChild<? extends PathArgument, ?>> childs = new ArrayList<>();
        childs.add(child);
        Mockito.when(cont.getValue()).thenReturn(childs);
        return cont;
    }

    private LeafNode mockLeaf(final QName leafQName) {
        final LeafNode child = Mockito.mock(LeafNode.class);
        Mockito.when(child.getNodeType()).thenReturn(leafQName);
        Mockito.when(child.getIdentifier()).thenReturn(NodeIdentifier.create(leafQName));
        Mockito.when(child.getValue()).thenReturn("value");
        return child;
    }

    private String prepareJson(final DOMNotification notificationData, final SchemaPath schemaPathNotifi)
            throws Exception {
        final List<SchemaPath> paths = new ArrayList<>();
        paths.add(schemaPathNotifi);
        final List<NotificationListenerAdapter> listNotifi =
                Notificator.createNotificationListener(paths, "stream-name", NotificationOutputType.JSON.toString());
        final NotificationListenerAdapter notifi = listNotifi.get(0);

        final Class<?> vars[] = {};
        final Method prepareJsonM = notifi.getClass().getDeclaredMethod("prepareJson", vars);
        prepareJsonM.setAccessible(true);

        final Field notification = notifi.getClass().getDeclaredField("notification");
        notification.setAccessible(true);
        notification.set(notifi, notificationData);

        final Field schema = notifi.getClass().getDeclaredField("schemaContext");
        schema.setAccessible(true);
        schema.set(notifi, this.schmeaCtx);

        final String result = (String) prepareJsonM.invoke(notifi, null);
        Preconditions.checkNotNull(result);
        return result;
    }
}
