/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

final class UncancellableFuture<V> extends AbstractFuture<V> {
    private volatile boolean uncancellable = false;

    UncancellableFuture(final boolean uncancellable) {
        this.uncancellable = uncancellable;
    }

    public boolean setUncancellable() {
        if (isCancelled()) {
            return false;
        }

        uncancellable = true;
        return true;
    }

    public boolean isUncancellable() {
        return uncancellable;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return !uncancellable && super.cancel(mayInterruptIfRunning);
    }

    @SuppressFBWarnings(value = "NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE",
            justification = "Unrecognised NullableDecl")
    @Override
    public boolean set(@Nullable final V value) {
        Preconditions.checkState(uncancellable);
        return super.set(value);
    }

    @Override
    protected boolean setException(final Throwable throwable) {
        Preconditions.checkState(uncancellable);
        return super.setException(throwable);
    }
}
