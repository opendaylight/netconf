/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.RestconfNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * A {@link NormalizedNodeWriterFactory} returning a {@link RestconfNormalizedNodeWriter} which emits the data only to
 *  a certain
 * depth.
 */
@NonNullByDefault
final class MaxDepthNormalizedNodeWriterFactory extends NormalizedNodeWriterFactory {
    private final DepthParam depth;

    MaxDepthNormalizedNodeWriterFactory(final DepthParam depth) {
        this.depth = requireNonNull(depth);
    }

    @Override
    protected RestconfNormalizedNodeWriter newWriter(final NormalizedNodeStreamWriter streamWriter) {
        return RestconfNormalizedNodeWriter.forStreamWriter(streamWriter, depth);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("depth", depth.value());
    }
}