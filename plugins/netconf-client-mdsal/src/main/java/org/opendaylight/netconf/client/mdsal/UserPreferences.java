/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;

/**
 * DTO with user capabilities to override or merge with device specific capabilities.
 */
public record UserPreferences(
        @NonNull NetconfSessionPreferences sessionPreferences,
        boolean overrideModuleCapabilities,
        boolean overrideNonModuleCapabilities) {

    public UserPreferences {
        requireNonNull(sessionPreferences);

        if (overrideModuleCapabilities && sessionPreferences.moduleBasedCaps().isEmpty()) {
            throw new IllegalStateException(
                    "Override module based capabilities flag set true but module based capabilities list is empty.");
        }
        if (overrideNonModuleCapabilities && sessionPreferences.nonModuleCaps().isEmpty()) {
            throw new IllegalStateException(
                    "Override non-module based capabilities set true but non-module based capabilities list is empty.");
        }
    }

}
