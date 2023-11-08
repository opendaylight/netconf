/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.notif;

import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.EventFormatter;
import org.opendaylight.restconf.server.spi.EventFormatterFactory;

abstract class NotificationFormatterFactory extends EventFormatterFactory<DOMNotification> {
    NotificationFormatterFactory(final EventFormatter<DOMNotification> emptyFormatter) {
        super(emptyFormatter);
    }
}
