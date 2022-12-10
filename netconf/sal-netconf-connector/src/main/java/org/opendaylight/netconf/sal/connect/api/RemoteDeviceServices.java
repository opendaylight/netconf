/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;

/**
 * Set of interfaces exposed by a {@link RemoteDevice}.
 */
public record RemoteDeviceServices(@NonNull DOMRpcService rpcs, @Nullable DOMActionService actions) {
    public RemoteDeviceServices {
        requireNonNull(rpcs);
    }
}
