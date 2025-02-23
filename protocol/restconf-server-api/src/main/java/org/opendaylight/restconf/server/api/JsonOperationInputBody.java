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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opendaylight.restconf.server.api.DatabindPath.OperationPath;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonOperationInputBody extends OperationInputBody {
    private static final Logger LOG = LoggerFactory.getLogger(JsonOperationInputBody.class);

    public JsonOperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final OperationPath path, final InputStream inputStream, final NormalizedNodeStreamWriter writer)
            throws ServerException {
        try {
            JsonParserStream.create(writer, path.databind().jsonCodecs(), path.inference())
                .parse(new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
        } catch (JsonParseException e) {
            LOG.debug("Error parsing JSON input", e);
            throw newProtocolMalformedMessageServerException(path, "Invalid JSON", unmaskIOException(e));
        }
    }
}
