/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;

/**
 * Reported to {@link RemoteDeviceHandler#onDeviceFailed(Throwable)} downstream of {@link NetconfNodeHandler}. Indicates
 * the corresponding NETCONF topology node will never become connected.
 */
public final class ConnectGivenUpException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public ConnectGivenUpException(final String message) {
        super(requireNonNull(message));
    }
}
