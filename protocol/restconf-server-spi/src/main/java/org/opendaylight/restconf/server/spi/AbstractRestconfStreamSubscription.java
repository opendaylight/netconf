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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Abstract base class for {@link RestconfStream.Subscription}s.
 */
public abstract non-sealed class AbstractRestconfStreamSubscription extends RestconfStream.Subscription {
    private final @NonNull Uint32 id;
    private final @NonNull QName encoding;
    private final @NonNull String streamName;
    private final @NonNull String receiverName;
    private final @NonNull TransportSession session;
    private final @Nullable EventStreamFilter filter;
    private RestconfStream.@Nullable Sender sender;

    private @NonNull SubscriptionState state;

    protected AbstractRestconfStreamSubscription(final Uint32 id, final QName encoding, final String streamName,
            final String receiverName, final SubscriptionState state, final TransportSession session,
            final @Nullable EventStreamFilter filter) {
        this.id = requireNonNull(id);
        this.encoding = requireNonNull(encoding);
        this.state = requireNonNull(state);
        this.session = requireNonNull(session);
        this.streamName = requireNonNull(streamName);
        this.receiverName = requireNonNull(receiverName);
        this.filter = filter;
    }

    @Override
    public final Uint32 id() {
        return id;
    }

    @Override
    public final QName encoding() {
        return encoding;
    }

    @Override
    public final String streamName() {
        return streamName;
    }

    @Override
    public final String receiverName() {
        return receiverName;
    }

    @Override
    public final SubscriptionState state() {
        return state;
    }

    @Override
    public void setState(final SubscriptionState newState) {
        if (state.canMoveTo(newState)) {
            state = newState;
        } else {
            throw new IllegalStateException("Cannot transition from " + state + " to " + newState);
        }
    }

    @Override
    public final TransportSession session() {
        return session;
    }

    final @Nullable EventStreamFilter filter() {
        return filter;
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper
            .add("id", id)
            .add("encoding", encoding)
            .add("stream", streamName)
            .add("receiver", receiverName)
            .add("filter", filter));
    }
}
