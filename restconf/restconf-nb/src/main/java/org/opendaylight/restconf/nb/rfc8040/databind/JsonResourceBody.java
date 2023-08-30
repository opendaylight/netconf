/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * A JSON-encoded {@link ResourceBody}.
 */
public final class JsonResourceBody extends ResourceBody {
    public JsonResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final InputStream inputStream, final Inference inference, final PathArgument name,
            final NormalizedNodeStreamWriter writer) throws IOException {
        try (var jsonParser = newParser(inference, writer)) {
            try (var reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                jsonParser.parse(reader);
            }
        }
    }

    private static JsonParserStream newParser(final Inference inference, final NormalizedNodeStreamWriter writer) {
        final var codecs = JSONCodecFactorySupplier.RFC7951.getShared(inference.getEffectiveModelContext());
        final var stack = inference.toSchemaInferenceStack();
        if (stack.isEmpty()) {
            return JsonParserStream.create(writer, codecs);
        }

        stack.exit();
        return JsonParserStream.create(writer, codecs, stack.toInference());
    }
}
