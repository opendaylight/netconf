/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

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
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
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
import org.xmlunit.assertj.XmlAssert;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class XmlNotificationListenerTest {
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

        final String result = prepareXmlResult(notificationData, schemaPathNotifi);

        final String control = "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">"
                + "<eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-leaf xmlns=\"notifi:mod\">"
                + "<lf>value</lf></notifi-leaf></notification>";

        assertXmlMatches(result, control);
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

        final String result = prepareXmlResult(notificationData, schemaPathNotifi);

        final String control = "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">"
                + "<eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-cont xmlns=\"notifi:mod\">"
                + "<cont><lf>value</lf></cont></notifi-cont></notification>";

        assertXmlMatches(result, control);
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

        final String result = prepareXmlResult(notificationData, schemaPathNotifi);

        final String control = "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">"
                + "<eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-list xmlns=\"notifi:mod\">"
                + "<lst><lf>value</lf></lst></notifi-list></notification>";

        assertXmlMatches(result, control);
    }

    @Test
    public void notifi_grpTest() throws Exception {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-grp"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareXmlResult(notificationData, schemaPathNotifi);

        final String control = "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">"
                + "<eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-grp xmlns=\"notifi:mod\">"
                + "<lf>value</lf></notifi-grp></notification>";

        assertXmlMatches(result, control);
    }

    @Test
    public void notifi_augmTest() throws Exception {
        final Absolute schemaPathNotifi = Absolute.of(QName.create(MODULE, "notifi-augm"));

        final DOMNotification notificationData = mock(DOMNotification.class);

        final LeafNode<String> leaf = mockLeaf(QName.create(MODULE, "lf-augm"));
        final ContainerNode notifiBody = mockCont(schemaPathNotifi.lastNodeIdentifier(), leaf);

        when(notificationData.getType()).thenReturn(schemaPathNotifi);
        when(notificationData.getBody()).thenReturn(notifiBody);

        final String result = prepareXmlResult(notificationData, schemaPathNotifi);

        final String control = "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">"
                + "<eventTime>2020-06-29T14:23:46.086855+02:00</eventTime><notifi-augm xmlns=\"notifi:mod\">"
                + "<lf-augm>value</lf-augm></notifi-augm></notification>";

        assertXmlMatches(result, control);
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

    private static String prepareXmlResult(final DOMNotification notificationData, final Absolute schemaPathNotifi)
            throws Exception {
        final NotificationListenerAdapter notifiAdapter = ListenersBroker.getInstance().registerNotificationListener(
                schemaPathNotifi, "xml-stream", NotificationOutputTypeGrouping.NotificationOutputType.XML);
        return notifiAdapter.formatter().eventData(SCHEMA_CONTEXT, notificationData, Instant.now(), false,
                false, false).orElseThrow();
    }
}
