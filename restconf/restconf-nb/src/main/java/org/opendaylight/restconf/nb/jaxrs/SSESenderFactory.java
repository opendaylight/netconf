/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;

/**
 * A factory for creating {@link SSESender}s.
 */
@NonNullByDefault
public final class SSESenderFactory {
    private final PingExecutor pingExecutor;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    public SSESenderFactory(final PingExecutor pingExecutor, final StreamsConfiguration configuration) {
        this.pingExecutor = requireNonNull(pingExecutor);
        heartbeatInterval = configuration.heartbeatInterval();
        maximumFragmentLength = configuration.maximumFragmentLength();
    }

    void newSSESender(final SseEventSink sink, final Sse sse, final RestconfStream<?> stream,
            final EncodingName encodingName, final EventStreamGetParams getParams) {
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESender(pingExecutor, sink, sse, stream, encodingName, getParams,
            maximumFragmentLength, heartbeatInterval);

        try {
            handler.init();
        } catch (UnsupportedEncodingException e) {
            throw new NotFoundException("Unsupported encoding " + encodingName.name(), e);
        } catch (IllegalArgumentException | XPathExpressionException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }
}
