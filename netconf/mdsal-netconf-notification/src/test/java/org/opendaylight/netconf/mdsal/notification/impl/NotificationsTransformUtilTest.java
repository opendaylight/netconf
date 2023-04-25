/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Date;
import java.util.Set;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.binding.dom.codec.impl.di.DefaultBindingDOMCodecFactory;
import org.opendaylight.mdsal.binding.generator.impl.DefaultBindingRuntimeGenerator;
import org.opendaylight.netconf.api.messages.NotificationMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChangeBuilder;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.parser.api.YangParserException;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.xml.sax.SAXException;

public class NotificationsTransformUtilTest {
    private static final Date DATE = new Date();
    private static final String INNER_NOTIFICATION =
            "<netconf-capability-change xmlns=\"urn:ietf:params:xml:ns:yang:ietf-netconf-notifications\">"
                    + "<deleted-capability>uri4</deleted-capability>"
                    + "<deleted-capability>uri3</deleted-capability>"
                    + "<added-capability>uri1</added-capability>"
                    + "</netconf-capability-change>";

    private static final String EXPECTED_NOTIFICATION =
            "<notification xmlns=\"urn:ietf:params:netconf:capability:notification:1.0\">"
                    + INNER_NOTIFICATION
                    + "<eventTime>"
                    + NotificationMessage.RFC3339_DATE_FORMATTER.apply(DATE)
                    + "</eventTime>"
                    + "</notification>";

    private static NotificationsTransformUtil UTIL;

    @BeforeClass
    public static void beforeClass() throws YangParserException {
        UTIL = new NotificationsTransformUtil(new DefaultYangParserFactory(), new DefaultBindingRuntimeGenerator(),
            new DefaultBindingDOMCodecFactory());
    }

    @Test
    public void testTransform() throws Exception {
        final NetconfCapabilityChangeBuilder netconfCapabilityChangeBuilder = new NetconfCapabilityChangeBuilder();

        netconfCapabilityChangeBuilder.setAddedCapability(Set.of(new Uri("uri1")));
        netconfCapabilityChangeBuilder.setDeletedCapability(Set.of(new Uri("uri4"), new Uri("uri3")));

        final NetconfCapabilityChange capabilityChange = netconfCapabilityChangeBuilder.build();
        final NotificationMessage transform = UTIL.transform(capabilityChange, DATE,
            Absolute.of(NetconfCapabilityChange.QNAME));

        final String serialized = XmlUtil.toString(transform.getDocument());

        compareXml(EXPECTED_NOTIFICATION, serialized);
    }

    static void compareXml(final String expected, final String actual) throws SAXException, IOException {
        XMLUnit.setIgnoreWhitespace(true);
        final Diff diff = new Diff(expected, actual);
        final DetailedDiff detailedDiff = new DetailedDiff(diff);
        detailedDiff.overrideElementQualifier(new RecursiveElementNameAndTextQualifier());
        assertTrue(detailedDiff.toString(), detailedDiff.similar());
    }

    @Test
    public void testTransformFromDOM() throws Exception {
        final NotificationMessage netconfNotification =
                new NotificationMessage(XmlUtil.readXmlToDocument(INNER_NOTIFICATION), DATE);

        XMLUnit.setIgnoreWhitespace(true);
        compareXml(EXPECTED_NOTIFICATION, netconfNotification.toString());
    }
}
