/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

public abstract sealed class ChildBody extends AbstractBody permits JsonChildBody, XmlChildBody {
    public record PrefixAndBody(@NonNull ImmutableList<PathArgument> prefix, @NonNull NormalizedNode body) {
        public PrefixAndBody {
            requireNonNull(prefix);
            requireNonNull(body);
        }
    }

    ChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull PrefixAndBody toPayload(final @NonNull DatabindContext databind,
            final @NonNull Inference parentInference, final @NonNull YangInstanceIdentifier parentPath) {
        return toPayload(databind, parentInference, parentPath, acquireStream());
    }

    abstract @NonNull PrefixAndBody toPayload(@NonNull DatabindContext databind, @NonNull Inference parentInference,
        @NonNull YangInstanceIdentifier parentPath, @NonNull InputStream inputStream);
}
