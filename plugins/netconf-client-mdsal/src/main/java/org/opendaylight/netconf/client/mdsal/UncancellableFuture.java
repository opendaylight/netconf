/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import com.google.common.util.concurrent.AbstractFuture;

/**
 * A future that cannot be cancelled. Used to communicate with the device and wait for a response.
 */
final class UncancellableFuture<V> extends AbstractFuture<V> {

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean set(final V value) {
        return super.set(value);
    }
}
