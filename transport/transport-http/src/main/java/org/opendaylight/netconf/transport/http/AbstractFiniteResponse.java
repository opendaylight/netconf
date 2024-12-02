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
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Abstract base class for {@link FiniteResponse}.
 */
@NonNullByDefault
public abstract class AbstractFiniteResponse extends FiniteResponse {
    private final HttpResponseStatus status;

    protected AbstractFiniteResponse(final HttpResponseStatus status) {
        this.status = requireNonNull(status);
    }

    @Override
    public final HttpResponseStatus status() {
        return status;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("status", status);
    }
}