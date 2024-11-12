/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.yangtools.concepts.Registration;

/**
 *
 */
final class RestconfEventStreamHandler extends ChannelInboundHandlerAdapter implements RestconfStream.Sender {
    private final @NonNull EventStreamGetParams params;
    private final @NonNull EncodingName encodingName;
    private final @NonNull RestconfStream<?> stream;
    private final @NonNull HttpVersion version;
    private final @Nullable Integer streamId;

    private Registration reg;

    RestconfEventStreamHandler(final HttpVersion version, final @Nullable Integer streamId,
            final RestconfStream<?> stream, final EncodingName encodingName,  final EventStreamGetParams params)
                throws IOException {
        this.version = requireNonNull(version);
        this.streamId = streamId;
        this.stream = requireNonNull(stream);
        this.encodingName = requireNonNull(encodingName);
        this.params = requireNonNull(params);
    }

    public void onHandlerAdded(final ChannelHandlerContext ctx) {
        try {
            reg = stream.addSubscriber(this, encodingName, params);
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void sendDataMessage(final String data) {
        // TODO Auto-generated method stub

    }

    @Override
    public void endOfStream() {
        // TODO Auto-generated method stub

    }
}
