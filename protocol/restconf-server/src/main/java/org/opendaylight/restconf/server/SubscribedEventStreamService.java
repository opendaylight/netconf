/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import org.opendaylight.netconf.transport.http.EventStreamListener;
import org.opendaylight.netconf.transport.http.EventStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscribedEventStreamService implements EventStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(SubscribedEventStreamService.class);

    @Override
    public void startEventStream(final String requestUri, final EventStreamListener listener,
            final StartCallback callback) {
        LOG.info("Starting subscribed stream at: {}", requestUri);
        // TODO implement this similar to RestconfStreamService
    }
}
