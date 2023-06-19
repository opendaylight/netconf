/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class JsonNotificationListenerTest {
    private static final Logger LOG = LoggerFactory.getLogger(JsonNotificationListenerTest.class);

    private static final QNameModule MODULE =
        QNameModule.create(XMLNamespace.of("notifi:mod"), Revision.of("2016-11-23"));

    private static EffectiveModelContext SCHEMA_CONTEXT;

    @BeforeClass
    public static void beforeClass() throws Exception {
        SCHEMA_CONTEXT = TestUtils.loadSchemaContext("/notifications");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Test
    public void notifi_leafTest() throws Exception {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-leaf"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        LOG.info("json result: {}", result);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-leaf"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    public void notifi_cont_leafTest() throws Exception {
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
    public void notifi_list_Test() throws Exception {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-list"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final MapEntryNode entry = mockMapEntry(QName.create(MODULE, "lst"), leaf);
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), Builders.mapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(MODULE, "lst")))
            .withChild(entry)
            .build());

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
    public void notifi_grpTest() throws Exception {
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
    public void notifi_augmTest() throws Exception {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-augm"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf-augm"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(notificationData, schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("lf-augm" + '"' + ":" + '"' + "value"));
    }

    private static MapEntryNode mockMapEntry(final QName entryQName, final LeafNode<String> leaf) {
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(entryQName, leaf.name().getNodeType(), leaf.body()))
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

    private static String prepareJson(final DOMNotification notificationData, final Absolute schemaPathNotifi)
            throws Exception {
        final NotificationListenerAdapter notifiAdapter = ListenersBroker.getInstance().registerNotificationListener(
                schemaPathNotifi, "json-stream", NotificationOutputType.JSON);
        return notifiAdapter.formatter()
                .eventData(SCHEMA_CONTEXT, notificationData, Instant.now(), false, false, false).orElseThrow();
    }
}
