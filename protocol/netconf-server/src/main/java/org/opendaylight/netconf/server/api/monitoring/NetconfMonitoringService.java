/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

import java.util.Collection;
import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;
import org.opendaylight.yangtools.concepts.Registration;

public interface NetconfMonitoringService {

    Sessions getSessions();

    /**
     * Returns session monitoring service session listener, which is used to notify monitoring service about state of
     * session.
     *
     * @return session listener
     */
    SessionListener getSessionListener();

    Schemas getSchemas();

    String getSchemaForCapability(String moduleName, Optional<String> revision);

    Capabilities getCapabilities();

    /**
     * Allows push based capabilities information transfer. After the listener is registered, current state is pushed
     * to the listener.
     *
     * @param listener Monitoring listener
     * @return listener registration
     */
    Registration registerCapabilitiesListener(CapabilitiesListener listener);

    /**
     * Allows push based sessions information transfer.
     *
     * @param listener Monitoring listener
     * @return listener registration
     */
    Registration registerSessionsListener(SessionsListener listener);

    interface CapabilitiesListener {
        /**
         * Callback used to notify about a change in used capabilities.
         *
         * @param capabilities resulting capabilities
         */
        void onCapabilitiesChanged(Capabilities capabilities);

        /**
         * Callback used to notify about a change in used schemas.
         *
         * @param schemas resulting schemas
         */
        void onSchemasChanged(Schemas schemas);
    }

    interface SessionsListener {
        /**
         * Callback used to notify about netconf session start.
         *
         * @param session started session
         */
        void onSessionStarted(Session session);

        /**
         * Callback used to notify about netconf session end.
         *
         * @param session ended session
         */
        void onSessionEnded(Session session);

        /**
         * Callback used to notify about activity in netconf session, like
         * rpc or notification. It is triggered at regular time interval. Session parameter
         * contains only sessions which state was changed.
         *
         * @param sessions updated sessions
         */
        void onSessionsUpdated(Collection<Session> sessions);
    }
}
