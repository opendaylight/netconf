/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;

/**
 * Initializer of a NETCONF session on a transport accepted from a call-home device. It is provided by the call-home
 * server, which owns the accepted SSH or TLS transport, and is invoked by {@link CallHomeNodeHandler} to attach its
 * session listener to that transport.
 *
 * <p>This replaces outbound connection setup through {@code NetconfClientFactory}: the device has already connected,
 * hence there is nothing to dial and no client configuration to build.
 */
@FunctionalInterface
public interface CallHomeSessionInitializer {
    /**
     * Initialize the NETCONF session on the accepted transport. The supplied listener receives all events of the
     * NETCONF session, starting with NETCONF hello negotiation.
     *
     * <p>This method is invoked at most once for each {@link CallHomeNodeHandler}.
     *
     * @param sessionListener listener of the NETCONF session, typically the node's {@code NetconfDeviceCommunicator}
     * @return future completing with the NETCONF session once hello negotiation succeeds, or failing if the session
     *         cannot be established. Cancelling the future signals that the node is no longer interested in the
     *         session.
     */
    @NonNull ListenableFuture<NetconfClientSession> initialize(@NonNull NetconfClientSessionListener sessionListener);
}
