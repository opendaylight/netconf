/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.monitoring.osgi;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.monitoring.Get;
import org.opendaylight.netconf.monitoring.GetSchema;

public class NetconfMonitoringOperationService implements NetconfOperationService {

    private final ImmutableSet<NetconfOperation> netconfOperations;

    public NetconfMonitoringOperationService(final NetconfMonitoringService monitor) {
        netconfOperations = ImmutableSet.of(new Get(monitor), new GetSchema(monitor));
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return netconfOperations;
    }

    @Override
    public void close() {
    }

}
