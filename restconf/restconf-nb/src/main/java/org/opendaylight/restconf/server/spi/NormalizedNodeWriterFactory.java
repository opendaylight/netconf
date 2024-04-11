/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * Interface for instantiating a {@link NormalizedNodeWriter} to handle the data writeout to a
 * {@link NormalizedNodeStreamWriter}.
 */
@NonNullByDefault
public abstract class NormalizedNodeWriterFactory {
    /**
     * Return the default {@link NormalizedNodeWriterFactory}.
     *
     * @return the default {@link NormalizedNodeWriterFactory}
     */
    public static final NormalizedNodeWriterFactory of() {
        return DefaultNormalizedNodeWriterFactory.INSTANCE;
    }

    public static final NormalizedNodeWriterFactory of(final @Nullable DepthParam depth) {
        return depth == null ? of() : new MaxDepthNormalizedNodeWriterFactory(depth);
    }

    /**
     * Create a new {@link NormalizedNodeWriter} for specified {@link NormalizedNodeStreamWriter}.
     *
     * @param streamWriter target {@link NormalizedNodeStreamWriter}
     * @return A {@link NormalizedNodeWriter}
     */
    protected abstract NormalizedNodeWriter newWriter(NormalizedNodeStreamWriter streamWriter);

    protected abstract ToStringHelper addToStringAttributes(ToStringHelper helper);

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }
}