/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.base.MoreObjects;

/**
 * An HTTP-addressable resource known to a server.
 */
abstract sealed class ServerResource permits RestconfServerResource, WellKnownResources {
    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
