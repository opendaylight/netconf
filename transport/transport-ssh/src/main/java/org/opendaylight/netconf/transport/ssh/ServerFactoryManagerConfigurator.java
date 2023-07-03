/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Extension interface allowing one to customize {@link ServerFactoryManager} before it is used to create the
 * {@link SSHServer} instance.
 */
@FunctionalInterface
public interface ServerFactoryManagerConfigurator {
    /**
     * Apply custom configuration.
     *
     * @param factoryManager server factory manager instance
     * @throws UnsupportedConfigurationException if the configuration is not acceptable
     */
    void configureServerFactoryManager(@NonNull ServerFactoryManager factoryManager)
        throws UnsupportedConfigurationException;
}
