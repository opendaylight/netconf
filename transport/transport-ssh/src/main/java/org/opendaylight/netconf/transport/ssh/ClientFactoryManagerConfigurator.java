/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

/**
 * Extension interface allowing one to customize {@link ClientFactoryManager} before it is used to create the
 * {@link SSHClient} instance.
 */
@FunctionalInterface
public interface ClientFactoryManagerConfigurator {
    /**
     * Apply custom configuration.
     *
     * @param factoryManager client factory manager instance
     * @throws UnsupportedConfigurationException if the configuration is not acceptable
     */
    void configureClientFactoryManager(@NonNull ClientFactoryManager factoryManager)
        throws UnsupportedConfigurationException;
}
