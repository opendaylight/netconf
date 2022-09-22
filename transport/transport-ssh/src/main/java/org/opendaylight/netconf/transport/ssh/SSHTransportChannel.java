/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.netconf.transport.api.AbstractOverlayTransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannel;

final class SSHTransportChannel extends AbstractOverlayTransportChannel {
    SSHTransportChannel(final TransportChannel tcp) {
        super(tcp);
    }
}
