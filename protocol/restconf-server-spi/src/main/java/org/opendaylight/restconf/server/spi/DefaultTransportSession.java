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
     * Our resources state.
     */
    private sealed interface Resources {
        // nothing else
    }

    /**
     * The channel is inactive. We do not allow new resources to be registered.
     */
    @NonNullByDefault
    private static final class InactiveResources implements Resources {
        static final InactiveResources INSTANCE = new InactiveResources();
    }

    /**
     * We have some resources that need to be cleaned up.
     */
    @NonNullByDefault
    private record SomeResources(ArrayList<Registration> registrations) implements Resources {
        SomeResources {
            requireNonNull(registrations);
        }

        boolean register(final Registration reg) {
            registrations.add(reg);
            return false;
        }
    }

    private static final VarHandle RESOURCES;

    static {
        try {
            RESOURCES = MethodHandles.lookup().findVarHandle(
                DefaultTransportSession.class, "resources", Resources.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull Description description;

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile Resources resources;

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
            release = switch (resources) {
                case null -> registerFirst(reg);
                case InactiveResources inactive -> true;
                case SomeResources some -> some.register(reg);
            };
        }

        // cannot register: close the registration without holding lock
        if (release) {
            reg.close();
        }
    }

    private boolean registerFirst(final @NonNull Registration reg) {
        // Start off small, grow as needed
        final var created = new SomeResources(new ArrayList<>(2));
        final var witness = (Resources) RESOURCES.compareAndExchange(this, null, created);
        return switch (witness) {
            case null -> created.register(reg);
            case InactiveResources inactive -> true;
            case SomeResources some -> some.register(reg);
        };
    }

    @Override
    protected void removeRegistration() {
        // optimistic side-step of creating the object monitor when there were no resources registered
        final var prev = (Resources) RESOURCES.compareAndExchange(this, null, InactiveResources.INSTANCE);
        final var toRelease = switch (prev) {
            case SomeResources some -> {
                // synchronize with registerResource()
                synchronized (this) {
                    yield some.registrations;
                }
            }
            // nothing to release
            case null, default -> List.<Registration>of();
        };

        // callbacks without holding the lock
        toRelease.forEach(Registration::close);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
        return super.addToStringAttributes(toStringHelper.add("transport", description.toFriendlyString()));
    }
}
