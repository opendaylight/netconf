/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.notif;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.nb.rfc8040.streams.AbstractNotificationListenerTest;
import org.opendaylight.restconf.nb.rfc8040.streams.notif.XMLNotificationFormatter;
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
import org.xmlunit.assertj.XmlAssert;

@ExtendWith(MockitoExtension.class)
class XMLNotificationFormatterTest extends AbstractNotificationListenerTest {
    @Mock
    private DOMNotification notificationData;

    @Test
    void notifi_leafTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-leaf");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        assertXmlMatches(prepareXmlResult(schemaPathNotifi), """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">\
            <eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-leaf xmlns="notifi:mod">\
            <lf>value</lf></notifi-leaf></notification>""");
    }

    @Test
    void notifi_cont_leafTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-cont");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var cont = mockCont(QName.create(MODULE, "cont"), leaf);
        final var notifiBody = mockCont(schemaPathNotifi, cont);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        assertXmlMatches(prepareXmlResult(schemaPathNotifi), """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">\
            <eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-cont xmlns="notifi:mod">\
            <cont><lf>value</lf></cont></notifi-cont></notification>""");
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

        assertXmlMatches(prepareXmlResult(schemaPathNotifi), """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">\
            <eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-list xmlns="notifi:mod">\
            <lst><lf>value</lf></lst></notifi-list></notification>""");
    }

    @Test
    void notifi_grpTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-grp");

        final var leaf = mockLeaf(QName.create(MODULE, "lf"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        assertXmlMatches(prepareXmlResult(schemaPathNotifi), """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">\
            <eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-grp xmlns="notifi:mod">\
            <lf>value</lf></notifi-grp></notification>""");
    }

    @Test
    void notifi_augmTest() throws Exception {
        final QName schemaPathNotifi = QName.create(MODULE, "notifi-augm");

        final var leaf = mockLeaf(QName.create(MODULE, "lf-augm"));
        final var notifiBody = mockCont(schemaPathNotifi, leaf);

        when(notificationData.getType()).thenReturn(Absolute.of(schemaPathNotifi));
        when(notificationData.getBody()).thenReturn(notifiBody);

        assertXmlMatches(prepareXmlResult(schemaPathNotifi), """
            <notification xmlns="urn:ietf:params:xml:ns:netconf:notification:1.0">\
            <eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-augm xmlns="notifi:mod">\
            <lf-augm>value</lf-augm></notifi-augm></notification>""");
    }

    private static void assertXmlMatches(final String result, final String control) {
        XmlAssert.assertThat(result).and(control)
                // text values have localName null but we want to compare those, ignore only nodes that have localName
                // with eventTime value
                .withNodeFilter(node -> node.getLocalName() == null || !node.getLocalName().equals("eventTime"))
                .areSimilar();
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

    private String prepareXmlResult(final QName schemaPathNotifi) throws Exception {
        final var ret = XMLNotificationFormatter.EMPTY.eventData(MODEL_CONTEXT, notificationData, Instant.now());
        assertNotNull(ret);
        return ret;
    }
}
