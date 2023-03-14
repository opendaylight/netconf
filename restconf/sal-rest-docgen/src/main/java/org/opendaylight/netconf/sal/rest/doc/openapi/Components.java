/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.openapi;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class Components implements AutoCloseable {

    private Map<String, ObjectNode> schemas;

    private final JsonFactory factory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonGenerator jsonGenerator;
    private final SecuritySchemes securitySchemes;
    private final StringWriter jsonObjectWriter;

    public Components(final SecuritySchemes securitySchemes) {
        this.securitySchemes = securitySchemes;
        factory = new JsonFactory();
        jsonObjectWriter = new StringWriter();
        try {
            jsonGenerator = factory.createGenerator(jsonObjectWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, ObjectNode> getSchemas() {
        return schemas;
    }

    public void setSchemas(final Map<String, ObjectNode> schemas) {
        this.schemas = schemas;
    }

    public SecuritySchemes getSecuritySchemes() {
        return securitySchemes;
    }

    public JsonGenerator getJsonGenerator() {
        return jsonGenerator;
    }

    public Map<String, ObjectNode> convertDataToSchema(final String schema) throws IOException {
        final Map<String, ObjectNode> map = new HashMap<>();
        try (JsonParser parser = factory.createParser(schema)) {
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    final String fieldName = parser.getText();
                    parser.nextToken();
                    map.put(fieldName, mapper.readTree(parser));
                }
            }
        }
        return map;
    }

    @Override
    public void close() throws IOException {
        jsonGenerator.close();
        jsonObjectWriter.close();
        schemas = convertDataToSchema(jsonObjectWriter.toString());
    }
}
