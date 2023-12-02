/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.server.api.DataPostPath;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.impl.schema.ResultAlreadySetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonChildBody extends ChildBody {
    private static final Logger LOG = LoggerFactory.getLogger(JsonChildBody.class);

    public JsonChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    PrefixAndBody toPayload(final DataPostPath path, final InputStream inputStream) throws RestconfDocumentedException {
        NormalizedNode result;
        try {
            result = toNormalizedNode(path, inputStream);
        } catch (Exception e) {
            Throwables.throwIfInstanceOf(e, RestconfDocumentedException.class);
            LOG.debug("Error parsing json input", e);

            if (e instanceof ResultAlreadySetException) {
                throw new RestconfDocumentedException(
                    "Error parsing json input: Failed to create new parse result data. "
                        + "Are you creating multiple resources/subresources in POST request?", e);
            }

            throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, e);
        }

        final var iiToDataList = ImmutableList.<PathArgument>builder();
        while (result instanceof ChoiceNode choice) {
            final var childNode = choice.body().iterator().next();
            iiToDataList.add(result.name());
            result = childNode;
        }

        final var resultName = result.name();
        if (result instanceof MapEntryNode) {
            iiToDataList.add(new NodeIdentifier(resultName.getNodeType()));
        }
        iiToDataList.add(resultName);

        return new PrefixAndBody(iiToDataList.build(), result);
    }

    private static @NonNull NormalizedNode toNormalizedNode(final DataPostPath path, final InputStream inputStream) {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final var jsonParser = JsonParserStream.create(writer, path.databind().jsonCodecs(), path.inference());
        final var reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        jsonParser.parse(reader);

        return resultHolder.getResult().data();
    }
}
