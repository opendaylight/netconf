/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.confignetconfconnector.osgi;

import java.util.Set;
import org.opendaylight.controller.config.facade.xml.ConfigSubsystemFacadeFactory;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;

public class NetconfOperationServiceFactoryImpl implements NetconfOperationServiceFactory {

    private final ConfigSubsystemFacadeFactory configFacadeFactory;

    public NetconfOperationServiceFactoryImpl(ConfigSubsystemFacadeFactory configFacadeFactory) {
        this.configFacadeFactory = configFacadeFactory;
    }

    @Override
    public NetconfOperationServiceImpl createService(String netconfSessionIdForReporting) {
        return new NetconfOperationServiceImpl(configFacadeFactory.createFacade(netconfSessionIdForReporting), netconfSessionIdForReporting);
    }

    @Override
    public Set<Capability> getCapabilities() {
        return configFacadeFactory.getCurrentCapabilities();
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return configFacadeFactory.getYangStoreService().registerModuleListener(listener::onCapabilitiesChanged);
    }
}
