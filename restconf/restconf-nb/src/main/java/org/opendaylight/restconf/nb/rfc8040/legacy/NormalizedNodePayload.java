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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A RFC8040 overlay from our marriage to NormalizedNodeContext. This represents a NormalizedNode along with further
 * messy details needed to deal with the payload.
 */
public final class NormalizedNodePayload {
    private final @NonNull ImmutableMap<String, Object> headers;
    private final @NonNull QueryParameters writerParameters;
    private final @NonNull Inference inference;
    private final NormalizedNode data;

    private NormalizedNodePayload(final Inference inference, final NormalizedNode data,
            final QueryParameters writerParameters, final ImmutableMap<String, Object> headers) {
        this.inference = requireNonNull(inference);
        this.data = data;
        this.writerParameters = requireNonNull(writerParameters);
        this.headers = requireNonNull(headers);
    }

    public static @NonNull NormalizedNodePayload empty(final Inference inference) {
        return new NormalizedNodePayload(inference, null, QueryParameters.empty(), ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload of(final Inference inference, final NormalizedNode data) {
        return new NormalizedNodePayload(inference, requireNonNull(data), QueryParameters.empty(), ImmutableMap.of());
    }

    public static @NonNull NormalizedNodePayload ofNullable(final Inference inference, final NormalizedNode data) {
        return data == null ? empty(inference) : of(inference, data);
    }

    // FIXME: can we get rid of this, please? Whoever is using this should be setting a Response instead
    @Deprecated
    public static @NonNull NormalizedNodePayload ofLocation(final Inference inference, final NodeIdentifier leafId,
            final URI location) {
        return new NormalizedNodePayload(inference, ImmutableNodes.leafNode(leafId, location.toString()),
            QueryParameters.empty(), ImmutableMap.of("Location", location));
    }

    public static Object ofReadData(final Inference inference, final NormalizedNode data,
            final QueryParameters parameters) {
        return new NormalizedNodePayload(inference, requireNonNull(data), parameters, ImmutableMap.of());
    }

    public @NonNull Inference inference() {
        return inference;
    }

    public @Nullable NormalizedNode getData() {
        return data;
    }

    /**
     * Return headers response headers.
     *
     * @return map of headers
     */
    // FIXME: this is only used for redirect on subscribe
    public @NonNull ImmutableMap<String, Object> getNewHeaders() {
        return headers;
    }

    public @NonNull QueryParameters getWriterParameters() {
        return writerParameters;
    }
}
