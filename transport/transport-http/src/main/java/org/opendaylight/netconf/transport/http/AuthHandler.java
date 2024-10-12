/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpRequest;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * {@link ChannelHandler} serving HTTP requests authentication and authorization.
 *
 * <p>When assigned to channel pipeline expected to respond with status
 * {@link io.netty.handler.codec.http.HttpResponseStatus#UNAUTHORIZED} if client cannot be authenticated
 * or {@link io.netty.handler.codec.http.HttpResponseStatus#FORBIDDEN} if access to requested resource is not granted
 * to authenticated client.
 *
 * @param <T> authentication information carrier type
 */
@NonNullByDefault
public interface AuthHandler<T> extends ChannelHandler {
    /**
     * Authenticates user for requests.
     *
     * @param request http request object
     * @return user authentication information if authenticated, null otherwise
     */
    @Nullable T authenticate(HttpRequest request);

    /**
     * Authorizes authenticated user access to resource requested.
     *
     * @param request http request object
     * @param authn user authentication information
     * @return true if access granted, false otherwise
     */
    default boolean isAuthorized(final HttpRequest request, final T authn) {
        requireNonNull(request);
        requireNonNull(authn);
        // grant by default - the access permissions are usually checked on service layer, not transport
        return true;
    }
}
