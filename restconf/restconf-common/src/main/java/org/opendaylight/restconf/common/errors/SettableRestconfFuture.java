/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RestconfFuture} which allows the result to be set via {@link #set(Object)} and
 * {@link #setFailure(RestconfDocumentedException)}.
 *
 * @param <V> resulting value type
 */
public final class SettableRestconfFuture<V> extends RestconfFuture<V> {
    @Override
    public boolean set(final V value) {
        return super.set(requireNonNull(value));
    }

    public boolean setFailure(final RestconfDocumentedException cause) {
        return setException(requireNonNull(cause));
    }
}
