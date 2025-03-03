/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.operations;

import java.util.Set;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.Registration;

public class OperationsProvider implements NetconfOperationServiceFactory {
    private final Set<Capability> caps;
    private final OperationsCreator operationsCreator;

    public OperationsProvider(final Set<Capability> caps) {
        this(caps, new DefaultOperationsCreator());
    }

    public OperationsProvider(final Set<Capability> caps, final OperationsCreator operationsCreator) {
        this.caps = caps;
        this.operationsCreator = operationsCreator;
    }

    @Override
    public Set<Capability> getCapabilities() {
        return caps;
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(caps, Set.of());
        return () -> { };
    }

    @Override
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return operationsCreator.getNetconfOperationService(caps, sessionId);
    }
}
