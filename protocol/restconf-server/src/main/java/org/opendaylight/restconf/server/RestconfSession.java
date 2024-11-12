/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpScheme;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.AbstractObjectRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends HTTPServerSession implements TransportSession {
    @NonNullByDefault
    private final class ResourceReg<T extends Registration> extends AbstractObjectRegistration<T> {
        ResourceReg(final T instance) {
            super(instance);
        }

        @Override
        protected void removeRegistration() {
            unregister(this);
        }
    }

    /**
     * Our resources state.
     */
    private sealed interface Resources {
        // nothing else
    }

    /**
     * The channel is inactive. We do not allow new resources to be registered.
     */
    private static final class InactiveResources implements Resources {
        static final InactiveResources INSTANCE = new InactiveResources();
    }

    /**
     * We have some resources that need to be cleaned up.
     */
    private record SomeResources(ArrayList<ResourceReg<?>> registrations) implements Resources {
        SomeResources {
            requireNonNull(registrations);
        }
    }

    private static final VarHandle RESOURCES;

    static {
        try {
            RESOURCES = MethodHandles.lookup().findVarHandle(RestconfSession.class, "resources", Resources.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfSession.class);

    private final EndpointRoot root;

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile Resources resources;

    RestconfSession(final HttpScheme scheme, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        try {
            super.channelInactive(ctx);
        } finally {
            cleanupResources();
        }
    }

    private void cleanupResources() {
        // Optimistic side-step of creating the object monitor
        final var prev = (Resources) RESOURCES.compareAndExchange(this, null, InactiveResources.INSTANCE);
        final var toRelease = switch (prev) {
            case SomeResources some -> {
                // synchronize with doRegister() and unregister()
                synchronized (this) {
                    yield some.registrations;
                }
            }
            // nothing to release
            case null, default -> List.<ResourceReg<?>>of();
        };

        // callbacks without holding the lock
        toRelease.forEach(reg -> reg.getInstance().close());
    }

    @Override
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(this, method, targetUri, headers);
    }

    @Override
    @NonNullByDefault
    public synchronized <T extends Registration> @Nullable ObjectRegistration<T> registerResource(final T resource) {
        final var reg = new ResourceReg<>(resource);

        // complicated because we want need to hold the lock during the entire inflate operation
        return switch (resources) {
            case null -> registerFirst(reg);
            case InactiveResources inactive -> null;
            case SomeResources some -> doRegister(some, reg);
        };
    }

    @Holding("this")
    @NonNullByDefault
    private <T extends Registration> @Nullable ResourceReg<T> registerFirst(final ResourceReg<T> reg) {
        // Start off small, grow as needed
        final var created = new SomeResources(new ArrayList<>(2));
        final var witness = (Resources) RESOURCES.compareAndExchange(this, null, created);
        return switch (witness) {
            case null -> doRegister(created, reg);
            case InactiveResources inactive -> null;
            case SomeResources some -> doRegister(some, reg);
        };
    }

    @Holding("this")
    @NonNullByDefault
    private static <T extends Registration> ResourceReg<T> doRegister(final SomeResources some,
            final ResourceReg<T> reg) {
        some.registrations.add(reg);
        return reg;
    }

    private synchronized void unregister(final ResourceReg<?> reg) {
        final var local = (Resources) RESOURCES.getAcquire(this);
        if (local instanceof SomeResources some) {
            if (!some.registrations.remove(reg)) {
                LOG.debug("Did not find registration {} in {}", reg, some);
            }
        } else {
            LOG.warn("Uregistering on resources {}, should never happen", local, new Throwable());
        }
    }
}
