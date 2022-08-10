/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import java.util.concurrent.CompletionStage;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;

public final class TCPTransportBootstrap extends TransportBootstrap {
    public TCPTransportBootstrap(final TransportChannelListener listener) {
        super(listener);
    }

    @Override
    public CompletionStage<TransportStack> initiate(final TcpClientGrouping connectParams)
            throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletionStage<TransportStack> listen(final TcpServerGrouping listenParams)
            throws UnsupportedConfigurationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected int tcpConnectPort() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    protected int tcpListenPort() {
        // TODO Auto-generated method stub
        return 0;
    }
}
