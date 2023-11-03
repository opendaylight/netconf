/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import java.time.Instant;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A set of subscribers attached to an {@link AbstractStream}.
 *
 * @param <T> event type
 */
abstract sealed class Subscribers<T> {
    private static final class Empty<T> extends Subscribers<T> {
        static final @NonNull Empty<?> INSTANCE = new Empty<>();

        @Override
        Subscribers<T> add(final Subscriber<T> toAdd) {
            return new Single<>(toAdd);
        }

        @Override
        Subscribers<T> remove(final Subscriber<T> toRemove) {
            return this;
        }

        @Override
        void formatAndPublish(final EffectiveModelContext modelContext, final T input, final Instant now) {
            // No-op
        }
    }

    private static final class Single<T> extends Subscribers<T> {
        private final Subscriber<T> subscriber;

        Single(final Subscriber<T> subscriber) {
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        Subscribers<T> add(final Subscriber<T> toAdd) {
            return new Multiple<>(ImmutableListMultimap.of(
                subscriber.formatter(), subscriber,
                toAdd.formatter(), toAdd));
        }

        @Override
        Subscribers<T> remove(final Subscriber<T> toRemove) {
            return toRemove.equals(subscriber) ? null : this;
        }

        @Override
        void formatAndPublish(final EffectiveModelContext modelContext, final T input, final Instant now) {
            final var formatted = format(subscriber.formatter(), modelContext, input, now);
            if (formatted != null) {
                subscriber.handler().sendDataMessage(formatted);
            }
        }
    }

    private static final class Multiple<T> extends Subscribers<T> {
        private final ImmutableListMultimap<EventFormatter<T>, Subscriber<T>> subscribers;

        Multiple(final ListMultimap<EventFormatter<T>, Subscriber<T>> subscribers) {
            this.subscribers = ImmutableListMultimap.copyOf(subscribers);
        }

        @Override
        Subscribers<T> add(final Subscriber<T> toAdd) {
            final var newSubscribers = ArrayListMultimap.create(subscribers);
            newSubscribers.put(toAdd.formatter(), toAdd);
            return new Multiple<>(newSubscribers);
        }

        @Override
        Subscribers<T> remove(final Subscriber<T> toRemove) {
            final var newSubscribers = ArrayListMultimap.create(subscribers);
            return newSubscribers.remove(toRemove.formatter(), toRemove)
                ? switch (newSubscribers.size()) {
                    case 0 -> throw new VerifyException("Unexpected empty subscribers");
                    case 1 -> new Single<>(newSubscribers.values().iterator().next());
                    default -> new Multiple<>(newSubscribers);
                } : this;
        }

        @Override
        void formatAndPublish(final EffectiveModelContext modelContext, final T input, final Instant now) {
            for (var entry : subscribers.asMap().entrySet()) {
                final var formatted = format(entry.getKey(), modelContext, input, now);
                if (formatted != null) {
                    for (var subscriber : entry.getValue()) {
                        subscriber.handler().sendDataMessage(formatted);
                    }
                }
            }
        }
    }

    private Subscribers() {
        // Hidden on purpose
    }

    @SuppressWarnings("unchecked")
    static <T> @NonNull Subscribers<T> empty() {
        return (Subscribers<T>) Empty.INSTANCE;
    }

    abstract @NonNull Subscribers<T> add(Subscriber<T> toAdd);

    abstract @Nullable Subscribers<T> remove(Subscriber<T> toRemove);

    abstract void formatAndPublish(EffectiveModelContext modelContext, T input, Instant now);

    @Nullable String format(final EventFormatter<T> formatter, final EffectiveModelContext modelContext,
            final T input, final Instant now) {
        try {
            return formatter.eventData(modelContext, input, now);
        } catch (Exception e) {
            // FIXME: better error handling
            throw new IllegalStateException(e);
        }
    }
}
