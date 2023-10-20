/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.RestconfResponse;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A RFC8040 overlay from our marriage to NormalizedNodeContext. This represents a NormalizedNode along with further
 * messy details needed to deal with the payload.
 */
@NonNullByDefault
public record NormalizedNodePayload(Inference inference, NormalizedNode data, QueryParameters writerParameters)
        implements RestconfResponse.Body {
    public NormalizedNodePayload {
        requireNonNull(inference);
        requireNonNull(data);
        requireNonNull(writerParameters);
    }

    public NormalizedNodePayload(final Inference inference, final NormalizedNode data) {
        this(inference, data, QueryParameters.empty());
    }
}
