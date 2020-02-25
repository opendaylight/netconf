/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.swagger;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Response {

    private static final String SCHEMA_KEY = "schema";
    private static final String REF_KEY = "$ref";
    private static final String DESCRIPTION_KEY = "description";
    private static final String HEADERS_KEY = "headers";

    private ObjectNode value = JsonNodeFactory.instance.objectNode();
    private ObjectNode responseBody = JsonNodeFactory.instance.objectNode();

    public Response(int statusCode) {
        value.set(String.valueOf(statusCode), responseBody);
        //required parameter
        responseBody.put(DESCRIPTION_KEY, "");
    }

    public Response setSchemaWithRef(String refName) {
        ObjectNode refNode  = JsonNodeFactory.instance.objectNode();
        refNode.put(REF_KEY, addDefinitionsPrefix(refName));
        responseBody.set(SCHEMA_KEY, refNode);
        return this;
    }

    public String addDefinitionsPrefix(String refName) {
        final String prefix = "#/definitions/";
        return prefix + refName;
    }
}
