/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Operation {
    boolean deprecated;
    private ArrayNode tags;
    private ArrayNode parameters;
    private ArrayNode security;
    private ArrayNode servers;
    private ObjectNode callbacks;
    private ObjectNode externalDocs;
    private ObjectNode requestBody;
    private ObjectNode responses;
    private String description;
    private String operationId;
    private String summary;

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public ArrayNode getTags() {
        return tags;
    }

    public void setTags(ArrayNode tags) {
        this.tags = tags;
    }

    public ArrayNode getParameters() {
        return parameters;
    }

    public void setParameters(ArrayNode parameters) {
        this.parameters = parameters;
    }

    public ArrayNode getSecurity() {
        return security;
    }

    public void setSecurity(ArrayNode security) {
        this.security = security;
    }

    public ArrayNode getServers() {
        return servers;
    }

    public void setServers(ArrayNode servers) {
        this.servers = servers;
    }

    public ObjectNode getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(ObjectNode callbacks) {
        this.callbacks = callbacks;
    }

    public ObjectNode getExternalDocs() {
        return externalDocs;
    }

    public void setExternalDocs(ObjectNode externalDocs) {
        this.externalDocs = externalDocs;
    }

    public ObjectNode getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(ObjectNode requestBody) {
        this.requestBody = requestBody;
    }

    public ObjectNode getResponses() {
        return responses;
    }

    public void setResponses(ObjectNode responses) {
        this.responses = responses;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
