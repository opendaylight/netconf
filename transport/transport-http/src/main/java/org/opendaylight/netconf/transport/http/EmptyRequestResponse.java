/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link CompletedRequest} which has no body.
 */
@Beta
@NonNullByDefault
public final class EmptyRequestResponse extends AbstractRequestResponse implements ReadyResponse {
    public static final EmptyRequestResponse NO_CONTENT = new EmptyRequestResponse(HttpResponseStatus.NO_CONTENT);
    public static final EmptyRequestResponse NOT_FOUND = new EmptyRequestResponse(HttpResponseStatus.NOT_FOUND);

    public EmptyRequestResponse(final HttpResponseStatus status, final @Nullable HttpHeaders headers) {
        super(status, headers);
    }

    public EmptyRequestResponse(final HttpResponseStatus status) {
        this(status, null);
    }

    @Override
    public FullHttpResponse toHttpResponse(final HttpVersion version) {
        return toHttpResponse(version, status, headers);
    }
}
