/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A definition of a stream.
 */
abstract sealed class Stream permits DataTreeChangeStream, DeviceStream, NotificationStream {
    private final @NonNull String name;

    Stream(final String name) {
        this.name = requireNonNull(name);
    }

    public final @NonNull String streamName() {
        return name;
    }

    @Override
    public final String toString() {
        return addToStringArguments(MoreObjects.toStringHelper(this)).toString();
    }

    ToStringHelper addToStringArguments(final ToStringHelper helper) {
        return helper.add("name", name);
    }
}
