/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.configuration;

import javax.annotation.Nonnull;

/**
 * Reading of restconf configuration that can be mapped to file /etc/restconf.cfg.
 */
@SuppressWarnings("unused")
public interface RestconfConfiguration {

    /**
     * Reading of actual parsed RESTCONF configuration.
     *
     * @return Parsed RESTCONF settings.
     */
    RestconfConfigurationHolder getActualConfiguration();

    /**
     * Registering of listener that wants to receive updates about actual RESTCONF configuration.
     *
     * @param listener RESTCONF configuration listener.
     */
    void registerListener(@Nonnull RestconfConfigurationListener listener);

    /**
     * Removal of listener that doesn't want to receive updates about actual RESTCONF configuration anymore.
     *
     * @param listener RESTCONF configuration listener.
     */
    void releaseListener(@Nonnull RestconfConfigurationListener listener);
}