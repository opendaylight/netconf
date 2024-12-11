/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Streaming interface for emitting the contents of a {@link FiniteResponse} to the session. It supports only a single
 * operation, {@link #start(HttpResponseStatus, HttpHeaders)}, from which the caller will get a
 * {@link ResponseBodyOutputStream}, to which they can append the response body.
 */
@Beta
@NonNullByDefault
public final class ResponseOutput {
    private final Channel channel;

    ResponseOutput(final Channel channel) {
        this.channel = requireNonNull(channel);
    }

    public ResponseBodyOutputStream start(final HttpResponseStatus status) {
        return start(status, null);
    }

    public ResponseBodyOutputStream start(final HttpResponseStatus status, final CharSequence name,
            final CharSequence value) {
        return start(status, DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders().set(name, value));
    }

    public ResponseBodyOutputStream start(final HttpResponseStatus status, final @Nullable HttpHeaders headers) {
        return new ResponseBodyOutputStream(channel, status,
            headers != null ? headers : DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("channel", channel).toString();
    }
}
