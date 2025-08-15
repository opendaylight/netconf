/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;

/**
 * Handle RPCs, adding required logic to RPCs such a request time-out.
 */
public sealed interface RpcsDecorator permits RpcsTimeoutAndRecoveryHandler {

    /**
     * Wrap the given Rpcs service with the required logic.
     *
     *  @param service {@link Rpcs}
     */
    Rpcs decorateRpcs(Rpcs service);
}
