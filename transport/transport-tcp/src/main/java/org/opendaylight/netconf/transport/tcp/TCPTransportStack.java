/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;

/**
 * Base class for TCP-based {@link TransportStack}s.
 */
public abstract sealed class TCPTransportStack extends AbstractTransportStack<TCPTransportChannel>
        permits TCPClient, TCPServer {
    TCPTransportStack(final TransportChannelListener<? super TCPTransportChannel> listener) {
        super(listener);
    }
}
