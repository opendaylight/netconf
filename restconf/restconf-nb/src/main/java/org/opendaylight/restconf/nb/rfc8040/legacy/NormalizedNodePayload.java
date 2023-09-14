/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A RFC8040 overlay from our marriage to NormalizedNodeContext. This represents a NormalizedNode along with further
 * messy details needed to deal with the payload.
 */
public final class NormalizedNodePayload {
    private final @NonNull QueryParameters writerParameters;
    private final @NonNull Inference inference;
    private final NormalizedNode data;

    private NormalizedNodePayload(final Inference inference, final NormalizedNode data,
            final QueryParameters writerParameters) {
        this.inference = requireNonNull(inference);
        this.data = data;
        this.writerParameters = requireNonNull(writerParameters);
    }

    public static @NonNull NormalizedNodePayload empty(final Inference inference) {
        return new NormalizedNodePayload(inference, null, QueryParameters.empty());
    }

    public static @NonNull NormalizedNodePayload of(final Inference inference, final NormalizedNode data) {
        return new NormalizedNodePayload(inference, requireNonNull(data), QueryParameters.empty());
    }

    public static @NonNull NormalizedNodePayload ofNullable(final Inference inference, final NormalizedNode data) {
        return data == null ? empty(inference) : of(inference, data);
    }

    public static Object ofReadData(final Inference inference, final NormalizedNode data,
            final QueryParameters parameters) {
        return new NormalizedNodePayload(inference, requireNonNull(data), parameters);
    }

    public @NonNull Inference inference() {
        return inference;
    }

    public @Nullable NormalizedNode getData() {
        return data;
    }

    public @NonNull QueryParameters getWriterParameters() {
        return writerParameters;
    }
}
