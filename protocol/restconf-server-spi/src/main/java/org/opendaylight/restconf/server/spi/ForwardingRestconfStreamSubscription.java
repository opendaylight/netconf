/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Abstract base class for {@link RestconfStream.Subscription}s which delegate to another subscription.
 */
public abstract non-sealed class ForwardingRestconfStreamSubscription<T extends RestconfStream.Subscription>
        extends RestconfStream.Subscription {
    protected final @NonNull T delegate;

    protected ForwardingRestconfStreamSubscription(final T delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public final Uint32 id() {
        return delegate.id();
    }

    @Override
    public final String receiverName() {
        return delegate.receiverName();
    }

    @Override
    public final QName encoding() {
        return delegate.encoding();
    }

    @Override
    public final String streamName() {
        return delegate.streamName();
    }

    @Override
    public final RestconfStream.Receiver receiver(){
        return delegate.receiver();
    }

    protected final @NonNull T delegate() {
        return delegate;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper.add("delegate", delegate));
    }
}
