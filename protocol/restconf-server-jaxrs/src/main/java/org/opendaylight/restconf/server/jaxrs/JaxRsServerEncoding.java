/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
import org.opendaylight.restconf.server.api.ServerEncoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yangtools.yang.common.QName;

@NonNullByDefault
enum JaxRsServerEncoding implements ServerEncoding {
    JSON(MonitoringEncoding.JSON, EncodeJson$I.QNAME),
    XML(MonitoringEncoding.XML, EncodeXml$I.QNAME);

    private final MonitoringEncoding monitoringEncoding;
    private final QName notificationsEncoding;

    JaxRsServerEncoding(final MonitoringEncoding monitoringEncoding, final QName notificationsEncoding) {
        this.monitoringEncoding = requireNonNull(monitoringEncoding);
        this.notificationsEncoding = requireNonNull(notificationsEncoding);
    }

    @Override
    public MonitoringEncoding monitoringEncoding() {
        return monitoringEncoding;
    }

    @Override
    public QName notificationsEncoding() {
        return notificationsEncoding;
    }
}
