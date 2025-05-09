/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.restconf.server.spi.EventFormatter;
import org.opendaylight.restconf.server.spi.TextParameters;

/**
 * Base class for {@link DOMNotification} {@link EventFormatter}s.
 */
@NonNullByDefault
public abstract class NotificationFormatter extends EventFormatter<DOMNotification> {
    protected NotificationFormatter(final TextParameters textParams) {
        super(textParams);
    }
}
