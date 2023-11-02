/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link Stream} for delivering YANG 1.0 notifications from a particular device.
 */
public final class DeviceStream extends Stream {
    private final @NonNull String deviceName;

    DeviceStream(final String name, final String deviceName) {
        super(name);
        this.deviceName = requireNonNull(deviceName);
    }

    @NonNull String deviceName() {
        return deviceName;
    }

    @Override
    ToStringHelper addToStringArguments(final ToStringHelper helper) {
        return super.addToStringArguments(helper).add("deviceName", deviceName);
    }
}
