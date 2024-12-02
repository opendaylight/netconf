/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Abstract base class for {@link RequestResponse}.
 */
public abstract class AbstractRequestResponse extends RequestResponse {
    protected final @NonNull HttpResponseStatus status;
    protected final @Nullable HttpHeaders headers;

    protected AbstractRequestResponse(final HttpResponseStatus status, final @Nullable HttpHeaders headers) {
        this.status = requireNonNull(status);
        this.headers = headers;
    }

    protected AbstractRequestResponse(final AbstractRequestResponse prev) {
        status = prev.status;
        headers = prev.headers;
    }

    @Override
    public final @NonNull HttpResponseStatus status() {
        return status;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("status", status).add("headers", headers);
    }
}