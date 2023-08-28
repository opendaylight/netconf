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
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class ChildBody extends AbstractBody permits JsonChildBody, XmlChildBody {
    private static final Logger LOG = LoggerFactory.getLogger(ChildBody.class);

    ChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull NormalizedNode toNormalizedNode() {
        final var resultHolder = new NormalizationResultHolder();
        try (var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder)) {
            streamTo(acquireStream(), null, null, writer);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, e);
        }
        return resultHolder.getResult().data();
    }

    abstract void streamTo(@NonNull InputStream inputStream, @NonNull Inference inference, @NonNull PathArgument name,
        @NonNull NormalizedNodeStreamWriter writer) throws IOException;
}
