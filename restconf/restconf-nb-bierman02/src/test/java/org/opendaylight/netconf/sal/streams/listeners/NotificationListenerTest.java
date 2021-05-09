/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.controller.sal.restconf.impl.test.TestUtils;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NotificationListenerTest {
    private static final QNameModule MODULE = QNameModule.create(XMLNamespace.of("notifi:mod"),
        Revision.of("2016-11-23"));

    private static EffectiveModelContext schemaContext;

    private ControllerContext controllerContext;

    @BeforeClass
    public static void staticInit() throws FileNotFoundException {
        schemaContext = TestUtils.loadSchemaContext("/notifications");
    }

    @Before
    public void init() {
        controllerContext = TestRestconfUtils.newControllerContext(schemaContext);
    }

    @Test
    public void notifi_leafTest() {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-leaf"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-leaf"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_cont_leafTest() {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-cont"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final ContainerNode cont = mockCont(QName.create(MODULE, "cont"), leaf);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), cont);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-cont"));
        assertTrue(result.contains("cont"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_list_Test() {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-list"));

        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), ImmutableNodes.mapNodeBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(MODULE, "lst")))
            .withChild(mockMapEntry(QName.create(MODULE, "lst"), mockLeaf(QName.create(MODULE, "lf"))))
            .build());

        final DOMNotification notificationData = mock(DOMNotification.class);
        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-list"));
        assertTrue(result.contains("lst"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_grpTest() {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-grp"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_augmTest() {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-augm"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf-augm"));
        final AugmentationNode augm = mockAugm(leaf);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), augm);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("lf-augm" + '"' + ":" + '"' + "value"));
    }

    private static AugmentationNode mockAugm(final LeafNode<String> leaf) {
        final AugmentationNode augm = mock(AugmentationNode.class);
        final AugmentationIdentifier augmId = new AugmentationIdentifier(Set.of(leaf.getIdentifier().getNodeType()));
        when(augm.getIdentifier()).thenReturn(augmId);

        final Collection<DataContainerChild> childs = new ArrayList<>();
        childs.add(leaf);

        when(augm.body()).thenReturn(childs);
        return augm;
    }

    private static MapEntryNode mockMapEntry(final QName entryQName, final LeafNode<String> leaf) {
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(entryQName, leaf.getIdentifier().getNodeType(),
                leaf.body()))
            .withChild(leaf)
            .build();
    }

    private static ContainerNode mockCont(final QName contQName, final DataContainerChild child) {
        return Builders.containerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(contQName))
            .withChild(child)
            .build();
    }

    private static LeafNode<String> mockLeaf(final QName leafQName) {
        return ImmutableNodes.leafNode(leafQName, "value");
    }

    private String prepareJson(final DOMNotification notificationData, final Absolute schemaPathNotifi) {
        final List<SchemaPath> paths = new ArrayList<>();
        paths.add(schemaPathNotifi.asSchemaPath());
        final List<NotificationListenerAdapter> listNotifi =
                Notificator.createNotificationListener(paths, "stream-name", NotificationOutputType.JSON.toString(),
                        controllerContext);
        final NotificationListenerAdapter notifi = listNotifi.get(0);
        return requireNonNull(notifi.prepareJson(schemaContext, notificationData));
    }
}
