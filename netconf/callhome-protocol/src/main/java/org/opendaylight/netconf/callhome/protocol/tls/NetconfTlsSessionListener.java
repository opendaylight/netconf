/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol.tls;

import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTlsSessionListener implements NetconfClientSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTlsSessionListener.class);

    public NetconfTlsSessionListener() {
        // empty constructor
    }

    @Override
    public void onSessionUp(NetconfClientSession session) {
        LOG.error("Session is up");
    }

    @Override
    public void onSessionDown(NetconfClientSession session, Exception cause) {
        LOG.error("Session is down");
    }

    @Override
    public void onSessionTerminated(NetconfClientSession session, NetconfTerminationReason reason) {
        LOG.error("Session terminated");
    }

    @Override
    public void onMessage(NetconfClientSession session, NetconfMessage message) {
        LOG.error("Got a message");
    }
}
