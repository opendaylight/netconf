/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.nb.rfc8040.streams.Configuration;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;

public abstract class StreamUrlResolver {

    private StreamUrlResolver() {
    }

    /**
     * Factory method - creation of corresponding {@link StreamUrlResolver} based on provided {@link Configuration}
     * - SSE or Websockets URL resolver.
     *
     * @param configuration Websocket configuration.
     * @return Instance of {@link StreamUrlResolver}.
     */
    public static StreamUrlResolver getInstance(final Configuration configuration) {
        return configuration.isUseSSE() ? serverSentEvents() : webSockets();
    }

    /**
     * Prepare URI from stream name.
     *
     * @param streamName name of the stream
     * @return final {@link URI}
     */
    @NonNull
    abstract URI prepareUriByStreamName(String streamName);

    /**
     * Prepare URI from stream name and request {@link UriInfo}. The output {@link URI} contains scheme, host, and
     * port too matching request {@link UriInfo}.
     *
     * @param streamName name of the stream
     * @param uriInfo    request {@link UriInfo}
     * @return final {@link URI}
     */
    @NonNull
    abstract URI prepareUriByStreamName(String streamName, UriInfo uriInfo);

    /**
     * Implementation of UrlResolver for Server-sent events.
     */
    private static final class ServerSentEvents extends StreamUrlResolver {
        private static final ServerSentEvents INSTANCE = new ServerSentEvents();

        @Override
        @NonNull
        URI prepareUriByStreamName(final String streamName) {
            return URI.create(RestconfConstants.BASE_URI_PATTERN
                    + '/'
                    + RestconfConstants.NOTIF
                    + '/'
                    + streamName);
        }

        @Override
        @NonNull
        URI prepareUriByStreamName(final String streamName, final UriInfo uriInfo) {
            final URI uriTemplate = uriInfo.getAbsolutePath();
            return UriBuilder.fromUri(prepareUriByStreamName(streamName))
                    .scheme(uriTemplate.getScheme())
                    .host(uriTemplate.getHost())
                    .port(uriTemplate.getPort())
                    .build();
        }
    }

    /**
     * Implementation of UrlResolver for Web sockets.
     */
    private static final class WebSockets extends StreamUrlResolver {
        private static final WebSockets INSTANCE = new WebSockets();

        @Override
        @NonNull
        URI prepareUriByStreamName(final String streamName) {
            return URI.create(RestconfConstants.BASE_URI_PATTERN
                    + '/'
                    + streamName);
        }

        @Override
        @NonNull
        URI prepareUriByStreamName(final String streamName, final UriInfo uriInfo) {
            final URI uriTemplate = uriInfo.getAbsolutePath();
            final UriBuilder builder = UriBuilder.fromUri(prepareUriByStreamName(streamName))
                    .host(uriTemplate.getHost())
                    .port(uriTemplate.getPort());
            switch (uriTemplate.getScheme()) {
                case "https":
                    builder.scheme("wss");
                    break;
                case "http":
                default:
                    builder.scheme("ws");
            }
            return builder.build();
        }
    }

    @VisibleForTesting
    public static StreamUrlResolver serverSentEvents() {
        return ServerSentEvents.INSTANCE;
    }

    @VisibleForTesting
    public static StreamUrlResolver webSockets() {
        return WebSockets.INSTANCE;
    }
}