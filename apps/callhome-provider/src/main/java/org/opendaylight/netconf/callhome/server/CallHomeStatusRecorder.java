/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import java.net.SocketAddress;
import java.security.PublicKey;

/**
 * Records the status of incoming connections from configured call-home devices.
 */
public interface CallHomeStatusRecorder {

     /**
     * Records netconf connection established successfully.
     *
     * @param id unique client identifier
     */
    void reportSuccess(String id);

     /**
     * Records client disconnected .
     *
     * @param id unique client identifier
     */
    void reportDisconnected(String id);

    /**
     * Records authentication failure for configured client (device).
     *
     * @param id unique client identifier
     */
    void reportFailedAuth(String id);

     /**
     * Records NETCONF layer initialization failure for configured client (device).
     *
     * @param id unique client identifier
     */
    void reportNetconfFailure(String id);

    /**
     * Records unknown {@link PublicKey} resulting client (device) identification failure.
     *
     * @param address the remote address the connection was accepted from
     * @param publicKey {@link PublicKey} used by client for own identification
     */
    void reportUnknown(SocketAddress address, PublicKey publicKey);

    /**
     * Records any exception causing closure of incoming connection. Mainly for debugging purposes.
     *
     * @param throwable exception thrown
     */
    default void onTransportChannelFailure(Throwable throwable) {
        // ignore by default
    }
}
