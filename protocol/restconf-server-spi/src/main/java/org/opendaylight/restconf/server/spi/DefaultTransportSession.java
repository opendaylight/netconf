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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * Default implementation of {@link TransportSession}. Users should call {@link #close()} when the underlying transport
 * goes away.
 */
public final class DefaultTransportSession extends AbstractRegistration implements TransportSession {
    /**
     * Our internal state.
     */
    private sealed interface State permits Closed, Open, Resources {
        // nothing else
    }

    /**
     * The session has been terminated. We do not allow new resources to be registered.
     */
    @NonNullByDefault
    private static final class Closed implements State {
        static final Closed INSTANCE = new Closed();
    }

    /**
     * We do not have any resources yet.
     */
    @NonNullByDefault
    private static final class Open implements State {
        static final Open INSTANCE = new Open();
    }

    /**
     * We have some resources that need to be cleaned up.
     */
    @NonNullByDefault
    private record Resources(ArrayList<Registration> registrations) implements State {
        Resources {
            requireNonNull(registrations);
        }

        boolean register(final Registration reg) {
            registrations.add(reg);
            return false;
        }
    }

    private static final VarHandle VH;

    static {
        try {
            VH = MethodHandles.lookup().findVarHandle(DefaultTransportSession.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull Description description;

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile @NonNull State state = Open.INSTANCE;

    @NonNullByDefault
    public DefaultTransportSession(final Description description) {
        this.description = requireNonNull(description);
    }

    @Override
    public Description description() {
        return description;
    }

    @Override
    public void registerResource(final Registration registration) {
        final var reg = requireNonNull(registration);

        // complicated because we need to hold the lock during the entire inflate operation
        final boolean release;
        synchronized (this) {
            release = switch (state) {
                case Closed closed -> true;
                case Open open -> registerFirst(reg);
                case Resources resources -> resources.register(reg);
            };
        }

        // cannot register: close the registration without holding lock
        if (release) {
            reg.close();
        }
    }

    @NonNullByDefault
    private boolean registerFirst(final Registration reg) {
        // Start off small, grow as needed
        final var created = new Resources(new ArrayList<>(2));
        final var witness = (State) VH.compareAndExchange(this, Open.INSTANCE, created);
        return switch (witness) {
            case Closed closed -> true;
            case Open open -> created.register(reg);
            case Resources resources -> resources.register(reg);
        };
    }

    @Override
    protected void removeRegistration() {
        // optimistic side-step of creating the object monitor when there were no resources registered
        final var prev = (State) VH.compareAndExchange(this, Open.INSTANCE, Closed.INSTANCE);
        final var toRelease = switch (prev) {
            case Resources resources -> {
                // synchronize with registerResource()
                synchronized (this) {
                    yield resources.registrations;
                }
            }
            // nothing to release
            default -> List.<Registration>of();
        };

        // callbacks without holding the lock
        toRelease.forEach(Registration::close);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
        return super.addToStringAttributes(toStringHelper.add("transport", description.toFriendlyString()));
    }
}
