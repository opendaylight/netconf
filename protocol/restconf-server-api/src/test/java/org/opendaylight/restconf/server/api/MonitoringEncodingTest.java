/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;

class MonitoringEncodingTest {
    @Test
    void of() {
        assertSame(MonitoringEncoding.JSON, MonitoringEncoding.of("json"));
        assertSame(MonitoringEncoding.XML, MonitoringEncoding.of("xml"));
        assertEquals(new MonitoringEncoding("foo"), MonitoringEncoding.of("foo"));
    }

    @Test
    void forEncoding() {
        assertSame(MonitoringEncoding.JSON, MonitoringEncoding.forEncoding(EncodeJson$I.QNAME));
        assertSame(MonitoringEncoding.XML, MonitoringEncoding.forEncoding(EncodeXml$I.QNAME));
        assertNull(MonitoringEncoding.forEncoding(QName.create("foo", "bar")));
        assertThrows(NullPointerException.class, () -> MonitoringEncoding.forEncoding(null));
    }

    @Test
    void encoding() {
        assertSame(EncodeJson$I.QNAME, MonitoringEncoding.JSON.encoding());
        assertSame(EncodeXml$I.QNAME, MonitoringEncoding.XML.encoding());
        assertNull(MonitoringEncoding.of("foo").encoding());
    }
}
