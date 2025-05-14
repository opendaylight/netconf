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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.notifications.mdsal.SubscriptionStateService;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry.EventStreamFilter;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.NoSuchSubscription;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for {@link RestconfStream.Subscription}s.
 */
public abstract class AbstractRestconfStreamSubscription extends RestconfStream.Subscription {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamSubscription.class);

    private final @NonNull Uint32 id;
    private final @NonNull QName encoding;
    private final @NonNull EncodingName encodingName;
    private final @NonNull String streamName;
    private final @NonNull String receiverName;
    private final @NonNull TransportSession session;
    private final @NonNull SubscriptionStateService stateService;

    private @NonNull SubscriptionState state;
    private @Nullable Instant stopTime;
    private @Nullable ScheduledFuture<?> stopTimeTask;

    protected AbstractRestconfStreamSubscription(final Uint32 id, final QName encoding, final EncodingName encodingName,
            final String streamName, final String receiverName, final SubscriptionState state,
            final TransportSession session, final SubscriptionStateService stateService, @Nullable Instant stopTime) {
        this.id = requireNonNull(id);
        this.encoding = requireNonNull(encoding);
        this.encodingName = requireNonNull(encodingName);
        this.state = requireNonNull(state);
        this.session = requireNonNull(session);
        this.streamName = requireNonNull(streamName);
        this.receiverName = requireNonNull(receiverName);
        this.stateService = stateService;
        this.stopTime = stopTime;
        stopTimeTask = null;
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

    protected abstract @Nullable EventStreamFilter filter();

    protected final @NonNull EncodingName encodingName() {
        return encodingName;
    }

    @Override
    public @Nullable Instant stopTime() {
        return stopTime;
    }

    void startStopTimeTask() {
        stopTimeTask = SCHEDULER.schedule(this::stopTimeReached,
            Duration.between(Instant.now(), stopTime).toSeconds(), TimeUnit.SECONDS);
    }

    private void stopTimeReached()  {
        if (state != SubscriptionState.END) {
            try {
                // FIXME: replace by subscription-completed, when it works
                stateService.subscriptionTerminated(Instant.now(), id, NoSuchSubscription.QNAME);
            } catch (InterruptedException e) {
                LOG.warn("Could not send subscription completed notification", e);
            }
            setState(SubscriptionState.END);
            stopTimeRemoveSubscription();
        }
        stopTimerTask();
    }

    void updateStopTime(@NonNull final Instant newStopTime) {
        stopTime = newStopTime;
        stopTimerTask();
        startStopTimeTask();
    }

    abstract void stopTimeRemoveSubscription();

    final void stopTimerTask() {
        if (stopTimeTask != null) {
            stopTimeTask.cancel(false);
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper
            .add("id", id)
            .add("encoding", encoding)
            .add("stream", streamName)
            .add("receiver", receiverName)
            .add("filter", filter()));
    }
}
