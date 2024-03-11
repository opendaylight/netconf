/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.netty;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.RequestDispatcher;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.netty.QueryParams;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class NettyRestconf implements RequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NettyRestconf.class);

    // TODO make this configurable
    private static final String BASE_PATH = "/rests/";
    private static final Pattern DATA_IDENTIFIER = Pattern.compile(BASE_PATH + "data/.+");

    private final RestconfServer server;

    public NettyRestconf(final RestconfServer server) {
        this.server = requireNonNull(server);
    }

    @Override
    public ListenableFuture<FullHttpResponse> dispatch(final FullHttpRequest request) {
        LOG.info("Sending response to {}", request);

        final var method = request.method();
        final var uri = request.uri();
        final var decoder = new QueryStringDecoder(uri);

        final ApiPath identifier;
        try {
            identifier = ApiPath.parseUrl(decoder.path().replace(BASE_PATH, ""));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        if ("GET".equals(method.name())) {
            if (DATA_IDENTIFIER.matcher(uri).matches()) {
                final var future = SettableFuture.<FullHttpResponse>create();
                final var data = server.dataGET(identifier, QueryParams.newDataGetParams(decoder));
                data.addCallback(new RestconfCallback<>() {
                    @Override
                    public void onSuccess(final @NonNull DataGetResult result) {
                        final var response = new DefaultFullHttpResponse(request.protocolVersion(), OK,
                            wrappedBuffer(result.toString().getBytes(StandardCharsets.UTF_8)));
                        response.headers().set(CONTENT_TYPE, TEXT_PLAIN)
                            .setInt(CONTENT_LENGTH, response.content().readableBytes());
                        future.set(response);
                    }

                    @Override
                    protected void onFailure(final @NonNull RestconfDocumentedException failure) {
                        future.setException(failure);
                    }
                });
                return future;
            }
        }
        return null;
    }
}
