/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

/**
 * Interface exposing NETCONF device RPC service. This interface is never implemented directly, but rather through its
 * {@code non-sealed} specializations.
 */
public sealed interface RemoteDeviceAccess {
    /**
     * NETCONF device RPCs operating just as any other {@link DOMRpcService}.
     */
    non-sealed interface Normalized extends RemoteDeviceAccess, DOMRpcService {

        @NonNull MountPointContext mountpointContext();

        @NonNull DOMRpcService rpcService();

        @Nullable DOMActionService actionService();
    }

    /**
     * NETCONF device RPCs operating in terms of {@link DocumentRpcService}.
     */
    non-sealed interface OrgW3CDom extends RemoteDeviceAccess, DocumentRpcService {
        // Just an interface combination
    }
}
