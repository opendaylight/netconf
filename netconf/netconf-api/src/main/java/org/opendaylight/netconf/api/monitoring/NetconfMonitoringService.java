/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.monitoring;

import com.google.common.base.Optional;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.sessions.Session;

public interface NetconfMonitoringService extends CapabilityListener, SessionListener {

    Sessions getSessions();

    Schemas getSchemas();

    String getSchemaForCapability(String moduleName, Optional<String> revision);

    Capabilities getCapabilities();

    /**
     * Allows push based state information transfer. After the listener is registered, current state is pushed to the listener.
     * @param listener Monitoring listener
     * @return listener registration
     */
    AutoCloseable registerListener(MonitoringListener listener);

    interface MonitoringListener {
        /**
         * Callback used to notify about netconf session start
         * @param session started session
         */
        void onSessionStarted(Session session);

        /**
         * Callback used to notify about netconf session end
         * @param session ended session
         */
        void onSessionEnded(Session session);

        /**
         * Callback used to notify about a change in used capabilities
         * @param capabilities actual capabilities
         */
        void onCapabilitiesChanged(Capabilities capabilities);

        /**
         * Callback used to notify about a change in used schemas
         * @param schemas actual schemas
         */
        void onSchemasChanged(Schemas schemas);
    }
}
