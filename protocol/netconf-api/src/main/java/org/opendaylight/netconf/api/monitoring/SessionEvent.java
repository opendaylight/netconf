/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.monitoring;

/**
 * Class represents change in netconf session.
 */
public final class SessionEvent {
    private final NetconfManagementSession session;
    private final Type type;

    private SessionEvent(NetconfManagementSession session, Type type) {
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

    public static SessionEvent inRpcSuccess(NetconfManagementSession session) {
        return new SessionEvent(session, Type.IN_RPC_SUCCESS);
    }

    public static SessionEvent inRpcFail(NetconfManagementSession session) {
        return new SessionEvent(session, Type.IN_RPC_FAIL);
    }

    public static SessionEvent outRpcError(NetconfManagementSession session) {
        return new SessionEvent(session, Type.OUT_RPC_ERROR);
    }

    public static SessionEvent notification(NetconfManagementSession session) {
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
