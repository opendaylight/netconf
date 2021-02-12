/*
 * Copyright (c) 2020 Al-soft and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import org.opendaylight.netconf.client.NetconfClientDispatcher;

/**
 * Allows to initialize the factory with specific dispatcher for creating communicators.
 *
 */
public interface NetconfDeviceCommunicatorInitializerFactory {

    /**
     * Initializing factory for creating communicators.
     *
     * @param netconfClientDispatcher Netconf client dispatcher
     * @return factory
     */
    NetconfDeviceCommunicatorFactory init(NetconfClientDispatcher netconfClientDispatcher);
}
