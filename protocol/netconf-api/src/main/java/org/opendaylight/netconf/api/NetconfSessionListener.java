/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import java.io.EOFException;

// FIXME: NETCONF-554: rework this interface
public interface NetconfSessionListener<S extends NetconfSession> {
    /**
     * Fired when the session was established successfully.
     *
     * @param session New session
     */
    void onSessionUp(S session);

    /**
     * Fired when the session went down because of an IO error. Implementation should take care of closing underlying
     * session.
     *
     * @param session that went down
     * @param cause Exception that was thrown as the cause of session being down. A common cause is
     *              {@link EOFException}, which indicates the remote end has shut down the communication channel.
     */
    void onSessionDown(S session, Exception cause);

    /**
     * Fired when the session is terminated locally. The session has already been closed and transitioned to IDLE state.
     * Any outstanding queued messages were not sent. The user should not attempt to make any use of the session.
     *
     * @param reason the cause why the session went down
     */
    void onSessionTerminated(S session, NetconfTerminationReason reason);

    /**
     * Fired when a normal protocol message is received.
     *
     * @param message Protocol message
     */
    void onMessage(S session, NetconfMessage message);

    /**
     * Fired when a protocol message cannot be decoded.
     *
     * @param failure Decoding failure
     */
    void onError(S session, Exception failure);
}
