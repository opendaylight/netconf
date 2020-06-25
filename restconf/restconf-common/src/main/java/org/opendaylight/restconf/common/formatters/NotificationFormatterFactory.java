/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;

public interface NotificationFormatterFactory extends EventFormatterFactory<DOMNotification> {
    @Override
    NotificationFormatter getFormatter();

    @Override
    NotificationFormatter getFormatter(String xpathFilter) throws XPathExpressionException;
}
