/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import java.util.concurrent.CompletionStage;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;

public abstract sealed class TCPTransportStack extends AbstractTransportStack {
    private static final class Initiate extends TCPTransportStack {
        Initiate(final TransportChannelListener listener) {
            super(listener);
        }
    }

    private static final class Listen extends TCPTransportStack {
        Listen(final TransportChannelListener listener) {
            super(listener);
        }

    }

    private TCPTransportStack(final TransportChannelListener listener) {
        super(listener);
    }

    public static @NonNull CompletionStage<TCPTransportStack> initiate(final TransportChannelListener listener,
            final TcpClientGrouping connectParams) throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    public static @NonNull CompletionStage<TCPTransportStack> listen(final TransportChannelListener listener,
            final TcpServerGrouping listenParams) throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }
}
