/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Operation(boolean deprecated, ArrayNode tags, ArrayNode parameters, ArrayNode security, ArrayNode servers,
        ObjectNode callbacks, ObjectNode externalDocs, ObjectNode requestBody, ObjectNode responses,
        String description, String operationId, String summary) {
}
