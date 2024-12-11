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
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link FiniteResponse} which has a {@link #cause()}.
 */
@NonNullByDefault
public final class ExceptionRequestResponse extends AbstractFiniteResponse {
    private final Throwable cause;

    public ExceptionRequestResponse(final HttpResponseStatus status, final Throwable cause) {
        super(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        this.cause = requireNonNull(cause);
    }

    public ExceptionRequestResponse(final Throwable cause) {
        this(HttpResponseStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public Throwable cause() {
        return cause;
    }

    @Override
    public void writeTo(final ResponseOutput output) throws IOException {
        try (var out = output.start(status())) {
            out.write(cause.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper).add("cause", cause);
    }
}
