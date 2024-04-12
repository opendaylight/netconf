/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link FutureCallback} tied to a {@link RestconfFuture}.
 *
 * @param <V> value type
 */
@Deprecated(since = "7.0.5", forRemoval = true)
public abstract class RestconfCallback<V> implements FutureCallback<@NonNull V> {
    @Override
    public final void onFailure(final Throwable cause) {
        onFailure(cause instanceof RestconfDocumentedException rde ? rde
            : new RestconfDocumentedException("Unexpected failure", cause));
    }

    protected abstract void onFailure(@NonNull RestconfDocumentedException failure);
}
