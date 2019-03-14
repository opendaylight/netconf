/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.configuration;

/**
 * Specification of RESTCONF configuration listener's API.
 */
public interface RestconfConfigurationListener {

    /**
     * Updating of parsed RESTCONF configuration.
     *
     * @param configurationHolder Object that wraps all RESTCONF settings.
     */
    void updateConfiguration(RestconfConfigurationHolder configurationHolder);

}