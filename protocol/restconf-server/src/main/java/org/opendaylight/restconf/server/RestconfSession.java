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
import io.netty.handler.codec.http2.Http2Exception;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.lock.qual.Holding;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>. It acts
 * as glue between a Netty channel and a RESTCONF server and may be servicing one (HTTP/1.1) or more (HTTP/2) logical
 * connections.
 */
final class RestconfSession extends HTTPServerSession implements TransportSession {
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
    private record SomeResources(ArrayList<Registration> registrations) implements Resources {
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

    private final @NonNull Description description;
    private final @NonNull EndpointRoot root;

    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD",
        justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile Resources resources;

    @NonNullByDefault
    RestconfSession(final HTTPScheme scheme, final SocketAddress remoteAddress, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
        description = new HttpDescription(scheme, remoteAddress);
    }

    @Override
    public Description description() {
        return description;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof Http2Exception.StreamException) {
            return;
        }
        super.exceptionCaught(ctx, cause);
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
    @NonNullByDefault
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(this, method, targetUri, headers);
    }

    @Override
    public void registerResource(final Registration registration) {
        final var reg = requireNonNull(registration);

        // complicated because we want need to hold the lock during the entire inflate operation
        final boolean release;
        synchronized (this) {
            release = switch (resources) {
                case null -> registerFirst(reg);
                case InactiveResources inactive -> true;
                case SomeResources some -> doRegister(some, reg);
            };
        }

        // Cannot register: close the registration without holding lock
        if (release) {
            reg.close();
        }
    }

    @Holding("this")
    private boolean registerFirst(final @NonNull Registration reg) {
        // Start off small, grow as needed
        final var created = new SomeResources(new ArrayList<>(2));
        final var witness = (Resources) RESOURCES.compareAndExchange(this, null, created);
        return switch (witness) {
            case null -> doRegister(created, reg);
            case InactiveResources inactive -> true;
            case SomeResources some -> doRegister(some, reg);
        };
    }

    @Holding("this")
    @NonNullByDefault
    private static boolean doRegister(final SomeResources some, final Registration reg) {
        some.registrations.add(reg);
        return false;
    }
}
