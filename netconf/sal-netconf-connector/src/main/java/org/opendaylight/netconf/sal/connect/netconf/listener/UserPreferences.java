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
    private final boolean override;

    public UserPreferences(@Nonnull final NetconfSessionPreferences sessionPreferences, boolean override) {
        this.sessionPreferences = sessionPreferences;
        this.override = override;
    }

    public NetconfSessionPreferences getSessionPreferences() {
        return sessionPreferences;
    }

    public boolean isOverride() {
        return override;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("UserPreferences{");
        sb.append("sessionPreferences=").append(sessionPreferences);
        sb.append(", override=").append(override);
        sb.append('}');
        return sb.toString();
    }
}
