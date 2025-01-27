/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A response of a completed requests. It implements {@link CompletedRequest} by returning self from
 * {@link #asResponse()}. It has a {@link #status()} and can be turned into (a series of) HTTP codec objects.
 */
@NonNullByDefault
public sealed interface Response extends CompletedRequest permits ReadyResponse, FiniteResponse, SSEResponse {
    @Override
    default Response asResponse() {
        return this;
    }

    /**
     * Returns the response status.
     *
     * @return the response status
     */
    HttpResponseStatus status();
}
