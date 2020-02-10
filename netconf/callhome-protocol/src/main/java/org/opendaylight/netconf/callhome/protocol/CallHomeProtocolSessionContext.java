/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import java.net.InetSocketAddress;
import java.security.PublicKey;

/**
 * Protocol level Session Context for incoming Call Home connections.
 */
public interface CallHomeProtocolSessionContext {

    /**
     * Returns session identifier provided by  CallHomeAuthorizationProvider.
     *
     * @return Returns application-provided session identifier
     */
    String getSessionName();

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
    InetSocketAddress getRemoteAddress();

    /**
     * Returns version string provided by remote server.
     *
     * @return Version string provided by remote server.
     */
    String getRemoteServerVersion();

    /** For deleting session if there another callhome session with
     *  the same ssh host key.
     */

    void deleteSessionOnDuplicateSshKey();
}
