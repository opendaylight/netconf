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
import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver.State;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Abstract base class for {@link RestconfStream.Subscription}s.
 */
public abstract class AbstractRestconfStreamSubscription extends RestconfStream.Subscription {
    @NonNullByDefault
    private sealed interface CurrentState {

        default @Nullable State state() {
            return switch (subscriptionState()) {
                case ACTIVE -> State.Active;
                case SUSPENDED -> State.Suspended;
                case END -> null;
            };
        }

        SubscriptionState subscriptionState();

        @Nullable Instant stopTime();
    }

    @NonNullByDefault
    private static final class Ended implements CurrentState {
        static final Ended INSTANCE = new Ended();

        private Ended() {
            // Hidden on purpose
        }

        @Override
        public @Nullable Instant stopTime() {
            return null;
        }

        @Override
        public SubscriptionState subscriptionState() {
            return SubscriptionState.END;
        }
    }

    @NonNullByDefault
    private record WithStopTime(SubscriptionState subscriptionState, Instant stopTime) implements CurrentState {
        WithStopTime {
            requireNonNull(subscriptionState);
            requireNonNull(stopTime);
        }

        public SubscriptionState subscriptionState() {
            return subscriptionState;
        }
    }

    @NonNullByDefault
    private enum WithoutStopTime implements CurrentState {
        ACTIVE(State.Active),
        SUSPENDED(State.Suspended);

        private State receiverState;

        WithoutStopTime(State receiverState) {
            this.receiverState = requireNonNull(receiverState);
        }

        @Override
        public @NonNull State state() {
            return receiverState;
        }

        @Override
        public @Nullable Instant stopTime() {
            return null;
        }

        @Override
        public SubcriptionState subscriptionState() {
            // TODO Auto-generated method stub
            return null;
        }
    }

    private final @NonNull Uint32 id;
    private final @NonNull QName encoding;
    private final @NonNull EncodingName encodingName;
    private final @NonNull String streamName;
    private final @NonNull String receiverName;
    private final @NonNull TransportSession session;

    private @NonNull CurrentState currentState;

    protected AbstractRestconfStreamSubscription(final Uint32 id, final QName encoding, final EncodingName encodingName,
            final String streamName, final String receiverName, final TransportSession session,
            final @Nullable Instant stopTime) {
        this.id = requireNonNull(id);
        this.encoding = requireNonNull(encoding);
        this.encodingName = requireNonNull(encodingName);
        this.session = requireNonNull(session);
        this.streamName = requireNonNull(streamName);
        this.receiverName = requireNonNull(receiverName);

        currentState = stopTime == null ? WithoutStopTime.ACTIVE : new WithStopTime(State.Active, stopTime);
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
        return currentState.subscriptionState();
    }

    @Override
    public @Nullable Instant stopTime() {
        return currentState.stopTime();
    }

    @Override
    public void setState(final SubscriptionState newState) {
        final var state = state();
        if (!state.canMoveTo(newState)) {
            throw new IllegalStateException("Cannot transition from " + state + " to " + newState);
        }
        this.state = newState;
    }

    @Override
    public final TransportSession session() {
        return session;
    }

    protected abstract @Nullable EventStreamFilter filter();

    protected final @NonNull EncodingName encodingName() {
        return encodingName;
    }

    @Override
    protected void stopTimeReached()  {
        if (state != SubscriptionState.END) {
            setState(SubscriptionState.END);
            stopTimeRemoveSubscription();
        }
    }

    public void updateStopTime(final Instant newStopTime) {
        stopTime = newStopTime;
    }

    abstract void stopTimeRemoveSubscription();

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        helper.add("id", id)
            .add("encoding", encoding)
            .add("stream", streamName)
            .add("receiver", receiverName)
            .add("filter", filter());
        if (stopTime != null) {
            helper.add("stopTime", stopTime);
        }
        return super.addToStringAttributes(helper);
    }
}
