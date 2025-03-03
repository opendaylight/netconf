/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.mdsal.monitoring.GetSchema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

public class NetconfMonitoringOperationService implements NetconfOperationService {
    private final NetconfMonitoringService monitor;
    private final SessionIdType sessionId;

    public NetconfMonitoringOperationService(final SessionIdType sessionId, final NetconfMonitoringService monitor) {
        this.sessionId = sessionId;
        this.monitor = monitor;
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Set.of(new Get(sessionId, monitor), new GetSchema(sessionId, monitor));
    }

    @Override
    public void close() {
        // No-op
    }
}
