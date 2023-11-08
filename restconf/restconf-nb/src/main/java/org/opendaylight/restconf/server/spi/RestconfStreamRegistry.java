/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.Source;

/**
 * SPI exposed for things that integrate with {@link RestconfStream}.
 */
public interface RestconfStreamRegistry {
    /**
     * Get a {@link RestconfStream} by its name.
     *
     * @param name Stream name.
     * @return A {@link RestconfStream}, or {@code null} if the stream with specified name does not exist.
     * @throws NullPointerException if {@code name} is {@code null}
     */
    @Nullable RestconfStream<?> lookupStream(String name);

    /**
     * Create a {@link RestconfStream} with a unique name. This method will atomically generate a stream name, create
     * the corresponding instance and register it.
     *
     * @param <T> Stream type
     * @param restconfURI resolved {@code {+restconf}} resource name
     * @param source Stream instance
     * @param description Stream descriptiion
     * @return A future {@link RestconfStream} instance
     * @throws NullPointerException if any argument is {@code null}
     */
    <T> @NonNull RestconfFuture<RestconfStream<T>> createStream(URI restconfURI, Source<T> source, String description);
}
