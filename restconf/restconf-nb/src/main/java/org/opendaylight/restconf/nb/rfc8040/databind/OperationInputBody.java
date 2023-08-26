/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Access to an {code rpc}'s or an {@code action}'s input.
 */
public abstract sealed class OperationInputBody extends AbstractBody
        permits JsonOperationInputBody, XmlOperationInputBody {
    OperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Stream the {@code input} into a {@link NormalizedNodeStreamWriter}.
     *
     * @param inference An {@link Inference} of parent {@code rpc} or {@code action} statement
     * @param writer Target writer
     * @throws IOException when an I/O error occurs
     */
    // TODO: pass down DatabindContext corresponding to inference
    public final void streamTo(final @NonNull Inference inference, final @NonNull NormalizedNodeStreamWriter writer)
            throws IOException {
        streamTo(acquireStream(), inference, writer);
    }

    abstract void streamTo(@NonNull InputStream inputStream, @NonNull Inference inference,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;
}
