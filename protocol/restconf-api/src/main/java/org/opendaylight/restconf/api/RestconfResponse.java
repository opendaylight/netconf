/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The result of a RESTCONF request. It contains two things: a {@link #statusCode()} and a {@link #body()}
 *
 * @param statusCode HTTP status code
 * @param body Response body, {@code null} if not present
 */
@Beta
// FIXME: this is a server-side thing, but it should be possible to make it more general, which may require the use
//        of generics (for example to specialze server-side and client-side request/response body pairings)
public record RestconfResponse(int statusCode, RestconfResponse.@Nullable Body body) {
    /**
     * The body of a {@link RestconfResponse}.
     */
    // FIXME: sealed with permits towards RestconfStrategy.CreateOrReplaceResult and others, but that in turn forces us
    //        to define what a RESTCONF request model looks like from the standpoint of both client (i.e. I want to fire
    //        of a request and get a response) and server (I want to implement this operation). We are not quite ready
    //        for that level of refinement, but we will get there.
    public interface Body {

    }

    public RestconfResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid status code " + statusCode);
        }
    }
}
