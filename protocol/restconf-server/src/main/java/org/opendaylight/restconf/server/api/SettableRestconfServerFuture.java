/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RestconfServerFuture} which allows the result to be set via {@link #set(Object)} and
 * {@link #setFailure(RestconfServerException)}.
 *
 * @param <V> resulting value type
 */
public final class SettableRestconfServerFuture<V> extends RestconfServerFuture<V> {
    @Override
    public boolean set(final V value) {
        return super.set(requireNonNull(value));
    }

    /**
     * Set the failure cause if this {@link RestconfServerFuture} has not completed yet.
     *
     * @param cause A {@link RestconfServerException}
     * @return {@code true} if the attempt was accepted, completing this {@code RestconfServerFuture}
     * @see #setException(Throwable)
     */
    public boolean setFailure(final RestconfServerException cause) {
        return setException(requireNonNull(cause));
    }
}
