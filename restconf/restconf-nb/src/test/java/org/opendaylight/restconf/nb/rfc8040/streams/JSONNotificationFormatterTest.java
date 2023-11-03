/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;

@ExtendWith(MockitoExtension.class)
class JSONNotificationFormatterTest extends AbstractNotificationListenerTest {
    @Mock
    private DOMNotification notificationData;

    @Test
    void notifi_leafTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-leaf");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-leaf"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    void notifi_cont_leafTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-cont");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var cont = mockCont(QName.create(MODULE, "cont"), leaf);
        final var notifiBody = mockCont(schemaPathNotifi, cont);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-cont"));
        assertTrue(result.contains("cont"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    void notifi_list_Test() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-list");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var entry = mockMapEntry(QName.create(MODULE, "lst"), leaf);
        final var notifiBody = mockCont(schemaPathNotifi, Builders.mapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(QName.create(MODULE, "lst")))
            .withChild(entry)
            .build());

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("notifi-module:notifi-list"));
        assertTrue(result.contains("lst"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    void notifi_grpTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-grp");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(schemaPathNotifi);

        assertTrue(result.contains("ietf-restconf:notification"));
        assertTrue(result.contains("event-time"));
        assertTrue(result.contains("lf" + '"' + ":" + '"' + "value"));
    }

    @Test
    void notifi_augmTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-augm");

        final var leaf = mockLeaf(QName.create(MODULE, "lf-augm"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareJson(schemaPathNotifi);

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

    private String prepareJson(final QName schemaPathNotifi) throws Exception {
        final var ret = JSONNotificationFormatter.EMPTY.eventData(MODEL_CONTEXT, notificationData, Instant.now());
        assertNotNull(ret);
        return ret;
    }
}
