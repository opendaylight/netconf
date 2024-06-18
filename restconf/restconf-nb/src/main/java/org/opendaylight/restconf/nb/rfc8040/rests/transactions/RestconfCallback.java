/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * A {@link FutureCallback} tied to a {@link RestconfFuture}.
 *
 * @param <V> value type
 */
abstract class RestconfCallback<V> implements FutureCallback<@NonNull V> {
    @Override
    public final void onFailure(final Throwable cause) {
        onFailure(cause instanceof RestconfDocumentedException rde ? rde
            : new RestconfDocumentedException("Unexpected failure", cause));
    }

    abstract void onFailure(@NonNull RestconfDocumentedException failure);
}
