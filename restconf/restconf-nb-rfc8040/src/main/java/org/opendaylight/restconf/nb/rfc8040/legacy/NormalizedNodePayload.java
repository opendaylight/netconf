/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * A RFC8040 overlay over {@link NormalizedNodeContext}. This represents a NormalizedNode along with further messy
 * details needed to deal with the payload.
 */
public final class NormalizedNodePayload extends NormalizedNodeContext {
    private NormalizedNodePayload(final InstanceIdentifierContext<?> context,
            final NormalizedNode data, final QueryParameters writerParameters,
            final ImmutableMap<String, Object> headers) {
        super(context, data, writerParameters, headers);
    }

    public static @NonNull NormalizedNodePayload empty(final InstanceIdentifierContext<?> path) {
        return new NormalizedNodePayload(requireNonNull(path), null, QueryParameters.empty(), ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload of(final InstanceIdentifierContext<?> path,
            final NormalizedNode data) {
        return new NormalizedNodePayload(requireNonNull(path), requireNonNull(data), QueryParameters.empty(),
            ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload ofNullable(final InstanceIdentifierContext<?> path,
            final NormalizedNode data) {
        return data == null ? empty(path) : of(path, data);
    }

    public static @NonNull NormalizedNodePayload ofLocation(final InstanceIdentifierContext<?> path,
            final NodeIdentifier leafId, final URI location) {
        return new NormalizedNodePayload(requireNonNull(path), ImmutableNodes.leafNode(leafId, location.toString()),
            QueryParameters.empty(), ImmutableMap.of("Location", location));
    }

    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE", justification = "Ensured via constructor")
    @Override
    public QueryParameters getWriterParameters() {
        return (QueryParameters) super.getWriterParameters();
    }
}
