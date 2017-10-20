/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.test.tool.operations;

import java.util.Set;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;

/**
 * Creator which enables testtool users to inject own.
 * implementation of {@link NetconfOperationService}
 */
public interface OperationsCreator {

    /**
     * Creates instance of {@link NetconfOperationService} based on caller context.
     * @param capabilities
     *   Model capabilities.
     * @param idProvider
     *   Provider's session context.
     * @param netconfSessionIdForReporting
     *   Netconf SessionId for reporting
     * @return
     *   Instance of {@link NetconfOperationService}.
     */
    NetconfOperationService getNetconfOperationService(Set<Capability> capabilities,
        SessionIdProvider idProvider,
        String netconfSessionIdForReporting);

}
