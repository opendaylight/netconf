/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.net.SocketAddress;
import java.security.PublicKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;

/**
 * Protocol level Session Context for incoming Call Home connections.
 */
public interface CallHomeProtocolSessionContext {

    /**
     * Returns session identifier provided by  CallHomeAuthorizationProvider.
     *
     * @return Returns application-provided session identifier
     */
    String getSessionId();

    /**
     * Returns public key provided by remote SSH Server for this session.
     *
     * @return public key provided by remote SSH Server
     */
    PublicKey getRemoteServerKey();

    /**
     * Returns remote socket address associated with this session.
     *
     * @return remote socket address associated with this session.
     */
    SocketAddress getRemoteAddress();

    /**
     * Terminate this session.
     */
    void terminate();

    /**
     * Returns transport type for this session.
     *
     * @return {@link Name} for this session.
     */
    Name getTransportType();
}
