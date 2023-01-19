/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import java.util.Set;
import org.opendaylight.controller.config.yang.netconf.mdsal.monitoring.GetSchema;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;

public class NetconfMonitoringOperationService implements NetconfOperationService {
    private final NetconfMonitoringService monitor;

    public NetconfMonitoringOperationService(final NetconfMonitoringService monitor) {
        this.monitor = monitor;
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Set.of(new Get(monitor), new GetSchema("testtool-session", monitor));
    }

    @Override
    public void close() {
        // No-op
    }

}
