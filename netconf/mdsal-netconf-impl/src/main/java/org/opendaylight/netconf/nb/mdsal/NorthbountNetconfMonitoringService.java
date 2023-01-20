/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nb.mdsal;

import org.opendaylight.netconf.impl.osgi.NetconfMonitoringServiceImpl;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author nite
 *
 */
@Component
public class NorthbountNetconfMonitoringService extends NetconfMonitoringServiceImpl {
    @Activate
    public NorthbountNetconfMonitoringService(
        @Reference(target = "type = netconf-server-monitoring") final NetconfOperationServiceFactoryListener monitoring) {
        super(monitoring);
    }
}
