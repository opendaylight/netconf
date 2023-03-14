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
import java.io.IOException;
import java.io.StringWriter;

public class Components implements AutoCloseable {
    private String schemas;

    private final JsonGenerator jsonGenerator;
    private final SecuritySchemes securitySchemes;
    private final StringWriter jsonObjectWriter;

    public Components(final SecuritySchemes securitySchemes) {
        this.securitySchemes = securitySchemes;
        final JsonFactory factory = new JsonFactory();
        jsonObjectWriter = new StringWriter();
        try {
            jsonGenerator = factory.createGenerator(jsonObjectWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSchemas() {
        return schemas;
    }

    public void setSchemas(final String schemas) {
        this.schemas = schemas;
    }

    public SecuritySchemes getSecuritySchemes() {
        return securitySchemes;
    }

    public JsonGenerator getJsonGenerator() {
        return jsonGenerator;
    }

    @Override
    public void close() throws IOException {
        jsonGenerator.close();
        jsonObjectWriter.close();
        schemas = jsonObjectWriter.toString();
    }
}
