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
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class ResourceBody extends AbstractBody permits JsonResourceBody, XmlResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceBody.class);

    ResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Stream the body into a {@link NormalizedNodeStreamWriter}.
     *
     * @param inference An {@link Inference} the statement corresponding to the body
     * @param writer Target writer
     * @throws IOException when an I/O error occurs
     */
    // TODO: pass down DatabindContext corresponding to inference
    public @NonNull NormalizedNode toNormalizedNode(final Inference inference) {
        final var holder = new NormalizationResultHolder();
        try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
            streamTo(acquireStream(), inference, streamWriter);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }

        final var parsedData = holder.getResult().data();
        if (parsedData instanceof MapNode map) {
            // TODO: This is a weird special case: a PUT target cannot specify the entire map, but the body parser
            //       always produces a single-entry map for entries. We need to undo that damage here.
            return map.body().iterator().next();
        } else {
            return parsedData;
        }
    }

    abstract void streamTo(InputStream inputStream, Inference inference, NormalizedNodeStreamWriter writer)
        throws IOException;
}