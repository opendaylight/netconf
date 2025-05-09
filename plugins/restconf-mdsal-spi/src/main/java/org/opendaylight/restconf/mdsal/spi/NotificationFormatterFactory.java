/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.EventFormatterFactory;

@NonNullByDefault
public abstract class NotificationFormatterFactory extends EventFormatterFactory<DOMNotification> {
    protected NotificationFormatterFactory(final NotificationFormatter emptyFormatter) {
        super(emptyFormatter);
    }

    @Override
    public final NotificationXPathEventFilter newXPathFilter(final String expression) throws XPathExpressionException {
        return new NotificationXPathEventFilter(expression);
    }
}
