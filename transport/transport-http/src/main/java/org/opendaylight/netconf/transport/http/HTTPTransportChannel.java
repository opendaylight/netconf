/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import org.opendaylight.netconf.transport.api.AbstractOverlayTransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannel;

public class HTTPTransportChannel extends AbstractOverlayTransportChannel {
    public HTTPTransportChannel(final TransportChannel transportChannel) {
        super(transportChannel);
    }
}
