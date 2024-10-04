/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link PreparedRequest} which is pending execution. This object knows what needs to be executed, where an now based
 * on having seen the contents of the HTTP request line and HTTP headers.
 *
 * <p>
 * The pipeline-side of wiring is using these objects to represent and track asynchronous execution. Once the pipeline
 * has set up everything it needs, it will invoke {@link #execute(PendingRequestListener, InputStream)} to kick off this
 * request. Subclasses are required to notify of request completion by invoking the appropriate method on the supplied
 * {@link PendingRequestListener}.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract non-sealed class PendingRequest<T> implements PreparedRequest {
    /**
     * Execute this request. Implementations are required to (eventually) signal completion via supplied
     * {@link PendingRequestListener}.
     *
     * @param listener the {@link PendingRequestListener} to notify on completion
     * @param body the HTTP request body, {@code null} if not present or empty
     */
    abstract void execute(PendingRequestListener listener, @Nullable InputStream body);

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
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);
}
