/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JSON-encoded {@link ResourceBody}.
 */
public final class JsonResourceBody extends ResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(JsonResourceBody.class);

    public JsonResourceBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final Data path, final PathArgument name, final InputStream inputStream,
            final NormalizedNodeStreamWriter writer) throws RequestException {
        try (var jsonParser = newParser(path, writer)) {
            try (var reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                jsonParser.parse(reader);
            }
            // FIXME: The JsonParserStream throw IllegalArgumentException and IllegalStateException with invalid data.
            //        Remove these exceptions when YANGTOOLS-1662 is resolved.
        } catch (IllegalArgumentException | IllegalStateException | IOException | JsonParseException e) {
            LOG.debug("Error parsing JSON input", e);
            throw newProtocolMalformedMessageServerException(path, "Error parsing input", unmaskIOException(e));
        }
    }

    private static JsonParserStream newParser(final DatabindPath.Data path, final NormalizedNodeStreamWriter writer) {
        final var codecs = path.databind().jsonCodecs();
        final var inference = path.inference();
        if (inference.isEmpty()) {
            return JsonParserStream.create(writer, codecs);
        }

        final var stack = inference.toSchemaInferenceStack();
        stack.exit();
        return JsonParserStream.create(writer, codecs, stack.toInference());
    }
}
