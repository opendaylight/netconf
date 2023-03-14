/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.swagger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

public class SwaggerObject implements CommonApiObject, Closeable {
    private String swagger;
    private Info info;
    private List<String> schemes;
    private String host;
    private String basePath;
    private List<String> produces;
    private ObjectNode paths;
    private final JsonGenerator definitions;
    private final StringWriter jsonObjectWriter;

    public SwaggerObject() {
        final JsonFactory factory = new JsonFactory();
        jsonObjectWriter = new StringWriter();
        try {
            definitions = factory.createGenerator(jsonObjectWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonGenerator getDefinitions() {
        return definitions;
    }

    public StringWriter getJsonObjectWriter() {
        return jsonObjectWriter;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public List<String> getSchemes() {
        return schemes;
    }

    public void setSchemes(List<String> schemes) {
        this.schemes = schemes;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPaths(ObjectNode paths) {
        this.paths = paths;
    }

    public ObjectNode getPaths() {
        return paths;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public String getSwagger() {
        return swagger;
    }

    public void setSwagger(String swagger) {
        this.swagger = swagger;
    }

    @Override
    public void close() throws IOException {
        jsonObjectWriter.close();
    }
}
