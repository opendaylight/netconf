/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.io.UnsupportedEncodingException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access to notification streams via Server-Sent Events.
 */
@Path("/")
// FIXME: integrate this service into JaxRsRestconf once we remove support for WebSockets
final class SSEStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(SSEStreamService.class);

    private final RestconfStream.Registry streamRegistry;
    private final PingExecutor pingExecutor;
    private final int maximumFragmentLength;
    private final int heartbeatInterval;

    SSEStreamService(final RestconfStream.Registry streamRegistry, final PingExecutor pingExecutor,
            final StreamsConfiguration configuration) {
        this.streamRegistry = requireNonNull(streamRegistry);
        this.pingExecutor = requireNonNull(pingExecutor);
        heartbeatInterval = configuration.heartbeatInterval();
        maximumFragmentLength = configuration.maximumFragmentLength();
    }

    /**
     * Attach to a particular notification stream.
     *
     * @param streamName path to target
     */
    @GET
    @Path("/{encodingName:[a-zA-Z]+}/{streamName:.+}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getSSE(@PathParam("encodingName") final EncodingName encodingName,
            @PathParam("streamName") final String streamName, @Context final UriInfo uriInfo,
            @Context final SseEventSink sink, @Context final Sse sse) {
        final var stream = streamRegistry.lookupStream(streamName);
        if (stream == null) {
            LOG.debug("Listener for stream with name {} was not found.", streamName);
            throw new NotFoundException("No such stream: " + streamName);
        }

        final ImmutableMap<String, String> queryParameters;
        try {
            queryParameters = QueryParams.normalize(uriInfo);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }

        final EventStreamGetParams params;
        try {
            params = EventStreamGetParams.ofQueryParameters(queryParameters);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage(), e);
        }

        LOG.debug("Listener for stream with name {} has been found, SSE session handler will be created.", streamName);
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESender(pingExecutor, sink, sse, stream, encodingName, params,
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
