/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.client.SslContextFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.parameters.protocol.Specification;

/**
 * A provider for {@link SslContextFactory} implementations. This allows the factory to be tailored with a
 * {@link Specification}.
 */
public interface SslContextFactoryProvider {
    /**
     * Return a {@link SslContextFactory}, optionally conforming to a particular specification.
     *
     * @param specification A {@link Specification}, may be {@code null}
     * @return A {@link SslContextFactory}
     * @throws IllegalArgumentException if {@code specification} is not {@code null} and it is not supported by this
     *         provider.
     */
    @NonNull SslContextFactory getSslContextFactory(@Nullable Specification specification);
}
