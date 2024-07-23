/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.data;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriter;
import org.opendaylight.restconf.server.spi.NormalizedNodeWriterFactory;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * A {@link NormalizedNodeWriterFactory} which restricts output to specified fields and potentially the specified depth.
 */
@NonNullByDefault
final class MdsalNormalizedNodeWriterFactory extends NormalizedNodeWriterFactory {
    private final @Nullable DepthParam depth;
    private final List<Set<QName>> fields;

    MdsalNormalizedNodeWriterFactory(final List<Set<QName>> fields,  final @Nullable DepthParam depth) {
        this.fields = requireNonNull(fields);
        this.depth = depth;
    }

    @Override
    protected NormalizedNodeWriter newWriter(final NormalizedNodeStreamWriter streamWriter) {
        return NormalizedNodeWriter.forStreamWriter(streamWriter, depth, fields);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        final var local = depth;
        if (local != null) {
            helper.add("depth", local.value());
        }
        return helper.add("fields", fields);
    }
}
