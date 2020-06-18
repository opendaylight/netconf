/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.http2;

import org.eclipse.jetty.io.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NotificationStreamConnectionListener implements Connection.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationStreamConnectionListener.class);

    @Override
    public void onOpened(Connection connection) {
        LOG.debug("onOpened");
    }

    @Override
    public void onClosed(Connection connection) {
        LOG.debug("onClosed");
    }

}
