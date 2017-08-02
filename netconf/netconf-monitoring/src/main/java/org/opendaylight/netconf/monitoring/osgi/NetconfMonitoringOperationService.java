/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.monitoring.osgi;

import com.google.common.collect.Sets;
import java.util.Set;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.monitoring.Get;
import org.opendaylight.netconf.monitoring.GetSchema;

public class NetconfMonitoringOperationService implements NetconfOperationService {

    private final NetconfMonitoringService monitor;

    public NetconfMonitoringOperationService(final NetconfMonitoringService monitor) {
        this.monitor = monitor;
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Sets.newHashSet(new Get(monitor), new GetSchema(monitor));
    }

    @Override
    public void close() {
    }

}
