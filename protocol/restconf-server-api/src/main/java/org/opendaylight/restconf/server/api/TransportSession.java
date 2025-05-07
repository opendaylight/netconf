/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.annotations.Beta;
import java.net.SocketAddress;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A transport session on which a {@link ServerRequest} is occurring. This typically the TCP or TLS connection
 * underlying an HTTP request.
 */
@Beta
@NonNullByDefault
public interface TransportSession {
    /**
     * A description of a {@link TransportSession}.
     *
     * <p>Beware: strings returned by implementations of this contract are NOT part of their contract and SHOULD NOT be
     * should be used by external tools. Notably an implementation can change the format of the returned string between
     * any releases.
     *
     * <p>Implementations are encouraged to use well-known transport details and take semantic versioning into
     * consideration when changing these formats.
     *
     * @apiNote This type uses strings on purpose. We could have used {@link SocketAddress} for
     *          {@link #transportRemote()}, but explicitly do not. This interface's sole purpose is to provide a
     *          description of the details, not the details themselves.
     */
    interface Description extends Immutable {
        /**
         * Returns a description of what message protocol the transport session is using. This could be something like
         * {@code HTTP/0.9}, {@code NETCONF/1.1} or similar.
         *
         * @return a description of what message protocol the transport session is using
         */
        String messageLayer();

        /**
         * Returns a description of what transport protocol the transport session is using. This could be something like
         * {@code TCP}, {@code TLSv3}, {@code SSHv2} or similar.
         *
         * @return a description of what protocol the transport session is using
         */
        String transportLayer();

        /**
         * Returns a description of where the transport session is from. This could be something like the remote TCP
         * address and source port, or similar.
         *
         * @return a description of where the transport session is from
         */
        String transportRemote();

        /**
         * Returns a short, friendly string summarizing this description. The purpose of this string is to provide
         * something an administrator can use to reason about where this session came from. Taking the example of a
         * HTTPS session, this would include the remote address and source port, but not details about TLS encryption,
         * authenticated user or anything of that kind.
         *
         * <p>Beware: provided default implementation is only a guideline. Implementations are free to use differently-
         * formatted strings.
         *
         * @return a short, friendly description of this session
         */
        default String toFriendlyString() {
            return messageLayer() + " over " + transportLayer() + " from " + transportRemote();
        }
    }

    /**
     * Return this session's {@link Description}.
     *
     * @return this session's {@link Description}
     */
    Description description();

    /**
     * Register resource to be closed on session end. Implementation should guarantee all registration are closed
     * when session ends.
     *
     * @param registration resource to be registered
     */
    void registerResource(Registration registration);
}
