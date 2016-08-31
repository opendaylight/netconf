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
    private final boolean overrideModularCapability;
    private final boolean overrideNonModularCapability;

    public UserPreferences(@Nonnull final NetconfSessionPreferences sessionPreferences, boolean overrideModularCapability, boolean overrideNonModularCapability) {
        this.sessionPreferences = sessionPreferences;
        this.overrideModularCapability = overrideModularCapability;
        this.overrideNonModularCapability = overrideNonModularCapability;
    }

    public NetconfSessionPreferences getSessionPreferences() {
        return sessionPreferences;
    }

    public boolean isOverrideModularCapability() {
        return overrideModularCapability;
    }

    public boolean isOverrideNonModularCapability() {
        return overrideNonModularCapability;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("UserPreferences{");
        sb.append("sessionPreferences=").append(sessionPreferences);
        sb.append(", overrideModularCapability=").append(overrideModularCapability);
        sb.append(", overrideNonModularCapability=").append(overrideNonModularCapability);
        sb.append('}');
        return sb.toString();
    }
}
