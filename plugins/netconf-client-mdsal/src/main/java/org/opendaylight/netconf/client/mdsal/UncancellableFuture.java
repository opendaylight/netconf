/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.util.concurrent.AbstractFuture;

final class UncancellableFuture<V> extends AbstractFuture<V> {
    private volatile boolean uncancellable = false;

    // FIXME rework this class be have uncancellable always true
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

    @Override
    public boolean set(final V value) {
        checkState(uncancellable);
        return super.set(value);
    }

    @Override
    protected boolean setException(final Throwable throwable) {
        checkState(uncancellable);
        return super.setException(throwable);
    }
}
