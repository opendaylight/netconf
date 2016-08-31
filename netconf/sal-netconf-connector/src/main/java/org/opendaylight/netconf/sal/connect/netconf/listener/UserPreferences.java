/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import javax.annotation.Nonnull;

/**
 * DTO with user capabilities to override or merge with device specific capabilities
 */
public class UserPreferences {

    private final NetconfSessionPreferences sessionPreferences;
    private final boolean overrideModuleCapabilities;
    private final boolean overrideNonModuleCapabilities;

    public UserPreferences(@Nonnull final NetconfSessionPreferences sessionPreferences,
            boolean overrideModuleCapabilities, boolean overrideNonModuleCapabilities) {

        if (overrideModuleCapabilities && sessionPreferences.getModuleBasedCaps() == null
                || sessionPreferences.getModuleBasedCaps().isEmpty()) {
            throw new IllegalStateException(
                    "Module Based override flag is true but module based capability is null or empty");
        }
        if (overrideNonModuleCapabilities && sessionPreferences.getNonModuleCaps() == null
                || sessionPreferences.getNonModuleCaps().isEmpty()) {
            throw new IllegalStateException(
                    "Non-Module Based override flag is true but non-module based capability is null or empty");
        }

        this.sessionPreferences = sessionPreferences;
        this.overrideModuleCapabilities = overrideModuleCapabilities;
        this.overrideNonModuleCapabilities = overrideNonModuleCapabilities;
    }

    public NetconfSessionPreferences getSessionPreferences() {
        return sessionPreferences;
    }

    public boolean isOverrideModuleCapabilities() {
        return overrideModuleCapabilities;
    }

    public boolean isOverrideNonModuleCapabilities() {
        return overrideNonModuleCapabilities;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("UserPreferences{");
        sb.append("sessionPreferences=").append(sessionPreferences);
        sb.append(", overrideModuleCapabilities=").append(overrideModuleCapabilities);
        sb.append(", overrideNonModuleCapabilities=").append(overrideNonModuleCapabilities);
        sb.append('}');
        return sb.toString();
    }
}
