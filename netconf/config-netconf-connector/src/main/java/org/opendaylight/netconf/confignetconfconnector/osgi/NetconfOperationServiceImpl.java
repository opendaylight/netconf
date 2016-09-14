/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.confignetconfconnector.osgi;

import java.util.Set;
import org.opendaylight.controller.config.facade.xml.ConfigSubsystemFacade;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;

public class NetconfOperationServiceImpl implements NetconfOperationService {

    private final NetconfOperationProvider operationProvider;
    private final ConfigSubsystemFacade configSubsystemFacade;

    public NetconfOperationServiceImpl(final ConfigSubsystemFacade configSubsystemFacade,
            final String netconfSessionIdForReporting) {
        this.configSubsystemFacade = configSubsystemFacade;
        this.operationProvider = new NetconfOperationProvider(configSubsystemFacade, netconfSessionIdForReporting);
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return operationProvider.getOperations();
    }

    @Override
    public void close() {
        configSubsystemFacade.close();
    }

}
