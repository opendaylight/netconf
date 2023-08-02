/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 *
 */
public interface RestconfFuture<V> extends ListenableFuture<@NonNull V> {
    /**
     * Get the result.
     *
     * @return The result
     * @throws RestconfDocumentedException if this future failed or this call is interrupted.
     */
    @NonNull V getOrThrow() throws RestconfDocumentedException;
}
