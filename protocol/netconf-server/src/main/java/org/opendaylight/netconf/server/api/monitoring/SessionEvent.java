/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

/**
 * Class represents change in a {@link NetconfManagementSession}.
 */
public final class SessionEvent {
    private final NetconfManagementSession session;
    private final Type type;

    private SessionEvent(final NetconfManagementSession session, final Type type) {
        this.session = session;
        this.type = type;
    }

    /**
     * Returns session, where event occurred.
     *
     * @return session
     */
    public NetconfManagementSession getSession() {
        return session;
    }

    /**
     * Returns event type.
     *
     * @return type
     */
    public Type getType() {
        return type;
    }

    public static SessionEvent inRpcSuccess(final NetconfManagementSession session) {
        return new SessionEvent(session, Type.IN_RPC_SUCCESS);
    }

    public static SessionEvent inRpcFail(final NetconfManagementSession session) {
        return new SessionEvent(session, Type.IN_RPC_FAIL);
    }

    public static SessionEvent outRpcError(final NetconfManagementSession session) {
        return new SessionEvent(session, Type.OUT_RPC_ERROR);
    }

    public static SessionEvent notification(final NetconfManagementSession session) {
        return new SessionEvent(session, Type.NOTIFICATION);
    }

    /**
     * Session event type.
     */
    public enum Type {
        /**
         * Correct rpc message received.
         */
        IN_RPC_SUCCESS,

        /**
         * Incorrect rpc message received.
         */
        IN_RPC_FAIL,

        /**
         * rpc-reply messages sent that contained an rpc-error element.
         */
        OUT_RPC_ERROR,

        /**
         * Notification message sent.
         */
        NOTIFICATION
    }
}
