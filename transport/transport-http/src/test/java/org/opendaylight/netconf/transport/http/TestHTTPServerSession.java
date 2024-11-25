/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class TestHTTPServerSession extends HTTPServerSession {
    TestHTTPServerSession(final HTTPScheme scheme) {
        super(scheme);
    }

    @Override
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return new PendingRequest<>() {
            @Override
            public void execute(final PendingRequestListener listener, final InputStream body) {
                // we should be executing on a virtual thread
                assertTrue(Thread.currentThread().isVirtual());
                assertThat(Thread.currentThread().getName()).contains("-http-server-req-");

                // return 200 response with a content built from request parameters
                final String payload;
                if (body != null) {
                    try {
                        payload = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        listener.requestFailed(this, e);
                        return;
                    }
                } else {
                    payload = "";
                }

                // emulate delay in server processing
                Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100));

                final var content = ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT,
                    "Method: %s URI: %s Payload: %s".formatted(method, targetUri.getPath(), payload));

                listener.requestComplete(this, new ByteBufRequestResponse(HttpResponseStatus.OK, content,
                    DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                        .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                        .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())));
            }

            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
                return helper;
            }
        };
    }
}
