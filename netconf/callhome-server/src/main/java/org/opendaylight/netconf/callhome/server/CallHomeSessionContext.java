/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;

/**
 * Session Context for incoming Call-Home connections.
 */
public interface CallHomeSessionContext {

    /**
     * Returns unique identifier of a connected device.
     *
     * @return identifier
     */
    String id();

    /**
     * Returns {@link NetconfClientSessionListener} associated with Netconf session expected to be established
     * through current connection.
     *
     * @return netconf session listener
     */
    NetconfClientSessionListener netconfSessionListener();

    /**
     * Returns {@link SettableFuture} for {@link NetconfClientSessionListener} expected to be established
     * through current connection.
     *
     * @return settable future for netconf session
     */
    SettableFuture<NetconfClientSession> settableFuture();

    /**
     * Terminates current connection.
     */
    void close();
}
