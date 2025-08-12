/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.AbstractFiniteResponse;
import org.opendaylight.netconf.transport.http.ResponseOutput;

@NonNullByDefault
final class URLRequestResponse extends AbstractFiniteResponse {
    private final URL url;
    private final boolean withContent;

    URLRequestResponse(final URL url, final boolean withContent) {
        super(HttpResponseStatus.OK);
        this.url = requireNonNull(url);
        this.withContent = withContent;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void writeTo(final ResponseOutput output) throws IOException {
        final var mediaType = URLConnection.guessContentTypeFromName(url.getFile());
        final var contentType = mediaType != null ? AsciiString.of(mediaType)
            : HttpHeaderValues.APPLICATION_OCTET_STREAM;

        try (var out = output.start(status(), HttpHeaderNames.CONTENT_TYPE, contentType)) {
            if (withContent) {
                try (var in = url.openStream()) {
                    in.transferTo(out);
                } catch (RuntimeException | IOException e) {
                    out.handleError(e);
                    throw e;
                }
            }
        }
    }
}
