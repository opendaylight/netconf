/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams;

import com.google.common.annotations.Beta;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.EventFormatter;
import org.opendaylight.restconf.server.spi.TextParameters;

/**
 * Base class for {@link DOMNotification} {@link EventFormatter}s.
 */
@Beta
public abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    protected NotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }

    protected NotificationFormatter(final TextParameters textParams, final String xpathFilter)
            throws XPathExpressionException {
        super(textParams, xpathFilter);
    }
}
