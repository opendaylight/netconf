/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Mutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An body backed by an {@link InputStream}, which can be consumed exactly once.
 */
@NonNullByDefault
public abstract class ConsumableBody implements AutoCloseable, Mutable {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumableBody.class);

    private final AtomicReference<@Nullable InputStream> inputStream;

    protected ConsumableBody(final InputStream inputStream) {
        this.inputStream = new AtomicReference<>(requireNonNull(inputStream));
    }

    @Override
    public final void close() {
        final var is = inputStream.getAndSet(null);
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                LOG.info("Failed to close input", e);
            }
        }
    }

    /**
     * Consume this body..
     *
     * @return An {@link InputStream}
     * @throws IllegalStateException if this body has already been consumed
     */
    protected final InputStream consume() {
        final var is = inputStream.getAndSet(null);
        if (is == null) {
            throw new IllegalStateException("Input stream has already been consumed");
        }
        return is;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this)).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        final var stream = inputStream.get();
        return helper.add("stream", stream != null ? stream : "CONSUMED");
    }
}
