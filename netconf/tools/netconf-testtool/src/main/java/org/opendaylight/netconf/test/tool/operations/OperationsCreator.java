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
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;

/**
 * Creator which enables testtool users to inject own. implementation of {@link NetconfOperationService}
 */
public interface OperationsCreator {
    /**
     * Creates instance of {@link NetconfOperationService} based on caller context.
     *
     * @param capabilities Model capabilities.
     * @param sessionId Netconf SessionId for reporting
     * @return Instance of {@link NetconfOperationService}.
     */
    NetconfOperationService getNetconfOperationService(Set<Capability> capabilities, SessionIdType sessionId);
}
