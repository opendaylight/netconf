/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.swagger;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Implementation of swagger spec (see <a href=
 * "https://github.com/wordnik/swagger-spec/blob/master/versions/1.2.md#52-api-declaration"
 * > https://github.com/wordnik/swagger-spec/blob/master/versions/1.2.md#52-api-
 * declaration</a>).
 */
public class SwaggerObject {
    private String apiVersion;
    private String swagger;
    private List<String> schemes;
    private String host;
    private String basePath;
    private String resourcePath;
    private List<String> produces;
//    private List<Api> apis;
    private ObjectNode paths;
    private ObjectNode definitions;

    public ObjectNode getDefinitions() {
        return definitions;
    }

    public void setDefinitions(ObjectNode definitions) {
        this.definitions = definitions;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSwagger() {
        return swagger;
    }

    public void setSwagger(String swagger) {
        this.swagger = swagger;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

//    public List<Api> getApis() {
//        return apis;
//    }
//
//    public void setApis(List<Api> apis) {
//        this.apis = apis;
//    }

//    public boolean hasApi() {
//        return (apis != null && !apis.isEmpty());
//    }

    public boolean hasModel() {
        return (definitions != null && definitions.size() > 0);
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
}
