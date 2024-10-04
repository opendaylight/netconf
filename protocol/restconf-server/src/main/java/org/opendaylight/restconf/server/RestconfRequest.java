/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: NETCONF-1379: eliminate/refactor this class
@NonNullByDefault
abstract class RestconfRequest {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfRequest.class);

    abstract void onSuccess(FullHttpResponse response);

    final void execute(final PendingRequest<?> pending, final HttpVersion version, final ByteBuf content) {
        pending.execute(new PendingRequestListener() {
            @Override
            public void requestFailed(final PendingRequest<?> request, final Exception cause) {
                LOG.warn("Internal error while processing {}", request, cause);
                final var response = new DefaultFullHttpResponse(version,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
                final var content = response.content();
                // Note: we are tempted to do a cause.toString() here, but we are dealing with unhandled badness here,
                //       so we do not want to be too revealing -- hence a message is all the user gets.
                ByteBufUtil.writeUtf8(content, cause.getMessage());
                HttpUtil.setContentLength(response, content.readableBytes());
                onSuccess(response);
            }

            @Override
            public void requestComplete(final PendingRequest<?> request, final Response reply) {
                // FIXME: ServerRequests typically finish with a FormattableBody, which can contain a huge entity, which
                //        we do *not* want to completely buffer to a FullHttpResponse.
                final FullHttpResponse response;

                switch (reply) {
                    case CompletedRequest completed -> {
                        response = completed.toHttpResponse(version);
                    }

                    // FIXME: these payloads use a synchronous dump of data into the socket. We cannot safely do that on
                    //        the event loop, because a slow client would end up throttling our IO threads simply
                    //        because of TCP window and similar queuing/backpressure things.
                    //
                    //        we really want to kick off a virtual thread to take care of that, i.e. doing its own
                    //        synchronous write thing, talking to a short queue (SPSC?) of HttpObjects.
                    //
                    //        the event loop of each channel would be the consumer of that queue, picking them off as
                    //        quickly as possible, but execting backpressure if the amount of pending stuff goes up.
                    //
                    //        as for the HttpObjects: this effectively means that the OutputStreams used in the below
                    //        code should be replaced with entities which perform chunking:
                    //        - buffer initial stuff, so that we produce a FullHttpResponse if the payload is below
                    //          256KiB (or so), i.e. producing Content-Length header and dumping the thing in one go
                    //        - otherwise emit just HttpResponse with Transfer-Enconding: chunked and continue sending
                    //          out chunks (of reasonable size).
                    //        - finish up with a LastHttpContent

                    case CharSourceResponse charSource -> {
                        response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
                        final var content = response.content();
                        try (var os = new ByteBufOutputStream(content)) {
                            charSource.source().asByteSource(StandardCharsets.UTF_8).copyTo(os);
                        } catch (IOException e) {
                            requestFailed(request, e);
                            return;
                        }

                        response.headers()
                            .set(HttpHeaderNames.CONTENT_TYPE, charSource.mediaType())
                            .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                    }
                    case FormattableDataResponse formattable -> {
                        response = new DefaultFullHttpResponse(version, formattable.status());
                        final var content = response.content();

                        try (var os = new ByteBufOutputStream(content)) {
                            formattable.writeTo(os);
                        } catch (IOException e) {
                            requestFailed(request, e);
                            return;
                        }

                        final var headers = response.headers();
                        final var extra = formattable.headers();
                        if (extra != null) {
                            headers.set(extra);
                        }
                        headers
                            .set(HttpHeaderNames.CONTENT_TYPE, formattable.encoding().dataMediaType())
                            .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                    }
                }

                onSuccess(response);
            }
        }, content.readableBytes() == 0 ? InputStream.nullInputStream() : new ByteBufInputStream(content));
    }
}
