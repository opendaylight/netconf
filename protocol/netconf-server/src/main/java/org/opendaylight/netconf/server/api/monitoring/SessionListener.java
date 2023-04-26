/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

public interface SessionListener {
    /**
     * Callback used to notify about netconf session start.
     *
     * @param session started session
     */
    void onSessionUp(NetconfManagementSession session);

    /**
     * Callback used to notify about netconf session end.
     *
     * @param session ended session
     */
    void onSessionDown(NetconfManagementSession session);

    /**
     * Callback used to notify about activity in netconf session, like
     * rpc or notification.
     *
     * @param event session event, contains session and type of event
     */
    void onSessionEvent(SessionEvent event);
}
