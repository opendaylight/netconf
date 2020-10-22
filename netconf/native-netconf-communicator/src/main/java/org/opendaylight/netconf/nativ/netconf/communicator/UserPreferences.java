/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator;

import org.eclipse.jdt.annotation.NonNull;

/**
 * DTO with user capabilities to override or merge with device specific capabilities.
 */
public class UserPreferences {

    private final NetconfSessionPreferences sessionPreferences;
    private final boolean overrideModuleCapabilities;
    private final boolean overrideNonModuleCapabilities;

    public UserPreferences(final @NonNull NetconfSessionPreferences sessionPreferences,
            boolean overrideModuleCapabilities, boolean overrideNonModuleCapabilities) {

        if (overrideModuleCapabilities && (sessionPreferences.getModuleBasedCaps() == null
                || sessionPreferences.getModuleBasedCaps().isEmpty())) {
            throw new IllegalStateException(
                    "Override module based capabilities flag set true but module based capabilities list is empty.");
        }
        if (overrideNonModuleCapabilities && (sessionPreferences.getNonModuleCaps() == null
                || sessionPreferences.getNonModuleCaps().isEmpty())) {
            throw new IllegalStateException(
                    "Override non-module based capabilities set true but non-module based capabilities list is empty.");
        }

        this.sessionPreferences = sessionPreferences;
        this.overrideModuleCapabilities = overrideModuleCapabilities;
        this.overrideNonModuleCapabilities = overrideNonModuleCapabilities;
    }

    public NetconfSessionPreferences getSessionPreferences() {
        return sessionPreferences;
    }

    public boolean moduleBasedCapsOverrided() {
        return overrideModuleCapabilities;
    }

    public boolean nonModuleBasedCapsOverrided() {
        return overrideNonModuleCapabilities;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UserPreferences{");
        sb.append("sessionPreferences=").append(sessionPreferences);
        sb.append(", overrideModuleCapabilities=").append(overrideModuleCapabilities);
        sb.append(", overrideNonModuleCapabilities=").append(overrideNonModuleCapabilities);
        sb.append('}');
        return sb.toString();
    }
}
