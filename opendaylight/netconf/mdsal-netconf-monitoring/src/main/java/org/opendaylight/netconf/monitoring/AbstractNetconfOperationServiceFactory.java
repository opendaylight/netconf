/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.monitoring;

import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

import java.util.Collections;
import java.util.Set;

public abstract class AbstractNetconfOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {

    private final NetconfOperationService operationService;

    private static final AutoCloseable AUTO_CLOSEABLE = new AutoCloseable() {
        @Override
        public void close() throws Exception {
            // NOOP
        }
    };

    public AbstractNetconfOperationServiceFactory(final NetconfOperationService operationService) {
        this.operationService = operationService;
    }

    protected NetconfOperationService getOperationService() {
        return operationService;
    }


    @Override
    public NetconfOperationService createService(final String netconfSessionIdForReporting) {
        return operationService;
    }

    @Override
    public Set<Capability> getCapabilities() {
        // TODO
        // No capabilities exposed to prevent clashes with schemas from mdsal-netconf-connector (it exposes all the schemas)
        // If the schemas exposed by mdsal-netconf-connector are filtered, this class would expose monitoring related models
        return Collections.emptySet();
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return AUTO_CLOSEABLE;
    }

    @Override
    public void close() {}

}
