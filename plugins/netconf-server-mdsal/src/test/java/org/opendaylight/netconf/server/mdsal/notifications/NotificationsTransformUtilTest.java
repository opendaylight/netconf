/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.notifications;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Answers;
import org.opendaylight.mdsal.binding.dom.codec.impl.di.DefaultBindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.generator.impl.DefaultBindingRuntimeGenerator;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yangtools.yang.binding.EventInstantAware;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

public class NotificationsTransformUtilTest {
    private static final Instant EVENT_TIME = Instant.now();
    private static final String INNER_NOTIFICATION = """
            <netconf-capability-change xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications">
                <deleted-capability>uri4</deleted-capability>
                <deleted-capability>uri3</deleted-capability>
                <added-capability>uri1</added-capability>
            </netconf-capability-change>
        """;

    private static final String EXPECTED_NOTIFICATION =
        "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">\n"
        + INNER_NOTIFICATION
        + "    <eventTime>" + NotificationMessage.RFC3339_DATE_FORMATTER.apply(EVENT_TIME) + "</eventTime>\n"
        + "</notification>\n";

    private static NotificationsTransformUtil UTIL;

    @BeforeClass
    public static void beforeClass() throws YangParserException {
        UTIL = new NotificationsTransformUtil(new DefaultYangParserFactory(), new DefaultBindingRuntimeGenerator(),
            new DefaultBindingDOMCodecFactory());
    }

    @Test
    public void testTransform() throws Exception {
        final var capabilityChange = mock(NetconfCapabilityChange.class,
            withSettings().extraInterfaces(EventInstantAware.class).defaultAnswer(Answers.CALLS_REAL_METHODS));
        doReturn(Set.of(new Uri("uri1"))).when(capabilityChange).getAddedCapability();
        doReturn(Set.of(new Uri("uri4"), new Uri("uri3"))).when(capabilityChange).getDeletedCapability();
        doReturn(EVENT_TIME).when((EventInstantAware) capabilityChange).eventInstant();
        doReturn(null).when(capabilityChange).getChangedBy();
        doReturn(null).when(capabilityChange).getModifiedCapability();
        doReturn(Map.of()).when(capabilityChange).augmentations();

        final var notification = UTIL.transform(capabilityChange, Absolute.of(NetconfCapabilityChange.QNAME));

        compareXml(EXPECTED_NOTIFICATION, XmlUtil.toString(notification.getDocument()));
    }

    @Test
    public void testTransformFromDOM() throws Exception {
        final var notification = new NotificationMessage(XmlUtil.readXmlToDocument(INNER_NOTIFICATION), EVENT_TIME);

        compareXml(EXPECTED_NOTIFICATION, notification.toString());
    }

    private static void compareXml(final String expected, final String actual) throws Exception {
        final var diff = DiffBuilder.compare(expected)
            .withTest(actual)
            .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText))
            .checkForSimilar()
            .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
