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
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;

/**
 * A RFC8040 overlay from our marriage to NormalizedNodeContext. This represents a NormalizedNode along with further
 * messy details needed to deal with the payload.
 */
public final class NormalizedNodePayload {
    private final InstanceIdentifierContext context;
    private final ImmutableMap<String, Object> headers;
    private final QueryParameters writerParameters;
    private final NormalizedNode data;

    private NormalizedNodePayload(final InstanceIdentifierContext context, final NormalizedNode data,
            final QueryParameters writerParameters, final ImmutableMap<String, Object> headers) {
        this.context = context;
        this.data = data;
        this.writerParameters = requireNonNull(writerParameters);
        this.headers = requireNonNull(headers);
    }

    public static @NonNull NormalizedNodePayload empty(final InstanceIdentifierContext path) {
        return new NormalizedNodePayload(requireNonNull(path), null, QueryParameters.empty(), ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload of(final InstanceIdentifierContext path, final NormalizedNode data) {
        return new NormalizedNodePayload(requireNonNull(path), requireNonNull(data), QueryParameters.empty(),
            ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload ofNullable(final InstanceIdentifierContext path,
            final NormalizedNode data) {
        return data == null ? empty(path) : of(path, data);
    }

    public static @NonNull NormalizedNodePayload ofLocation(final InstanceIdentifierContext path,
            final NodeIdentifier leafId, final URI location) {
        return new NormalizedNodePayload(requireNonNull(path), ImmutableNodes.leafNode(leafId, location.toString()),
            QueryParameters.empty(), ImmutableMap.of("Location", location));
    }

    public static Object ofReadData(final InstanceIdentifierContext path, final NormalizedNode data,
            final QueryParameters parameters) {
        return new NormalizedNodePayload(requireNonNull(path), requireNonNull(data), parameters, ImmutableMap.of());
    }

    public InstanceIdentifierContext getInstanceIdentifierContext() {
        return context;
    }

    public NormalizedNode getData() {
        return data;
    }

    /**
     * Return headers response headers.
     *
     * @return map of headers
     */
    // FIXME: this is only used for redirect on subscribe
    public ImmutableMap<String, Object> getNewHeaders() {
        return headers;
    }

    public QueryParameters getWriterParameters() {
        return writerParameters;
    }
}
