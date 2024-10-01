/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.base.MoreObjects;
import java.io.InputStream;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link PreparedRequest} which is pending execution. The request is known to be bound to invoke some HTTP method on
 * some resource.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract non-sealed class PendingRequest<T> implements PreparedRequest {
    /**
     * Execute this request.
     *
     * @param completer the {@link RequestCompleter} to notify on completion
     * @param principal the {@link Principal} making this request
     * @param body the HTTP request body
     */
    abstract void execute(RequestCompleter completer, @Nullable Principal principal, InputStream body);

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(final @Nullable Object obj) {
        return super.equals(obj);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
