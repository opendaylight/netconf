/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.time.Instant;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class XMLNotificationFormatter extends NotificationFormatter {
    private static final XMLNotificationFormatter INSTANCE = new XMLNotificationFormatter();

    public static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory() {
        @Override
        public XMLNotificationFormatter getFormatter(final String xpathFilter) throws XPathExpressionException {
            return new XMLNotificationFormatter(xpathFilter);
        }

        @Override
        public XMLNotificationFormatter getFormatter() {
            return INSTANCE;
        }
    };

    private XMLNotificationFormatter() {

    }

    private XMLNotificationFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    String createText(final SchemaContext schemaContext, final DOMNotification input, final Instant now)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
