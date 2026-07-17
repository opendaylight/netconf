/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;

/**
 * An {@link HTTPClient} operating over QUIC.
 */
final class QuicHTTPClient extends HTTPClient {
    QuicHTTPClient(final TransportChannelListener<? super HTTPTransportChannel> listener) {
        super(listener, HTTPScheme.HTTPS, null, false);
    }

    @Override
    protected void onUnderlayChannelEstablished(final TransportChannel underlayChannel) {
        // connectQuic() adds the transport channel directly once the QUIC handshake completes
        throw new IllegalStateException("QUIC transport does not use underlay channels");
    }

    @Override
    void initializePipeline(final TransportChannel underlayChannel, final ChannelPipeline pipeline,
            final Http2ConnectionHandler connectionHandler) {
        // unreachable: only invoked from onUnderlayChannelEstablished(), which we override above to throw before
        // ever reaching this call, since QUIC has no separate underlay-connect-then-attach-codec step to hook into
        throw new IllegalStateException("QUIC transport does not use HTTP/2 pipeline");
    }
}
