/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.model.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendaylight.netconf.sal.rest.doc.util.JsonUtil;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class NewOperationBuilder {
    private static final String DESCRIPTION = "description";
    private static final String OPERATION_ID = "operationId";
    private static final String SUMMARY = "summary";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";
    private static final String NAME = "name";
    private static final String IN = "in";
    private static final String SCHEMA = "schema";
    private static final String REF = "$ref";
    private static final String PARAMETERS = "parameters";
    private static final String CONFIG = "(config)";
    private static final String OPERATIONAL = "(operational)";
    public static final String TOP = "_TOP";
    public static final String DEFINITIONS_PREFIX = "#/definitions/";

    public static ObjectNode buildPost(final String nodeName, final String parentName, final String description,
                                       final DataNodeContainer dataNodeContainer, final ArrayNode pathParams) {
        String newParentName = parentName.replace("_module", "");
        ObjectNode spec = JsonNodeFactory.instance.objectNode();
        spec.put(DESCRIPTION, description);
        String operationId = POST + "-" + nodeName;
//        spec.put(OPERATION_ID, operationId);
        spec.put(SUMMARY, operationId);
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        final Collection<DataSchemaNode> childNodes = dataNodeContainer.getChildNodes();

        final List<DataSchemaNode> payloadSource = childNodes.stream()
                .filter((n) -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
                .collect(Collectors.toList());
        //only one body parameter allowed

        if (payloadSource.size() == 1) {
            DataSchemaNode node = payloadSource.get(0);
            final ObjectNode payload = JsonNodeFactory.instance.objectNode();
            payload.put(IN, "body");
            payload.put("name", "**" + CONFIG + node.getQName().getLocalName());
            ObjectNode ref = JsonNodeFactory.instance.objectNode();
            ref.put(REF, DEFINITIONS_PREFIX + newParentName + CONFIG + node.getQName().getLocalName() + TOP);
            payload.set("schema", ref);
            parameters.add(payload);
        } else {
            for (final DataSchemaNode node : payloadSource) {
                final ObjectNode payload = JsonNodeFactory.instance.objectNode();
                payload.put(IN, "formData");
                payload.put("name", "**" + CONFIG + node.getQName().getLocalName());
                payload.put("type", "string");
                parameters.add(payload);
            }
            ArrayNode produces = JsonNodeFactory.instance.arrayNode(1);
            produces.add("application/x-www-form-urlencoded");
            spec.set("produces", produces);
        }
        spec.set("parameters", parameters);
        return spec;
    }

    public static ObjectNode buildGet(final DataSchemaNode node, final boolean isConfig, ArrayNode pathParams) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION, node.getDescription().orElse(""));
        final String operationId = GET + "-" + node.getQName().getLocalName();
//        value.put(OPERATION_ID, operationId);
        value.put(SUMMARY, operationId);
        ArrayNode parameters = JsonUtil.copy(pathParams);
        value.set(PARAMETERS, parameters);
        return value;
    }

    public static ObjectNode buildPut(final String nodeName, final String description, final String parentName, ArrayNode pathParams) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION, description);
        final String operationId = PUT + "-" + nodeName;
//        value.put(OPERATION_ID, operationId);
        value.put(SUMMARY, operationId);

        ArrayNode parameters = JsonUtil.copy(pathParams);

        final ObjectNode bodyParam = JsonNodeFactory.instance.objectNode();
        bodyParam.put("name", CONFIG + nodeName);
        bodyParam.put(IN, "body");
        ObjectNode ref = JsonNodeFactory.instance.objectNode();
        ref.put(REF, DEFINITIONS_PREFIX + parentName + CONFIG + nodeName + TOP);
        bodyParam.set(SCHEMA, ref);
        parameters.add(bodyParam);
        value.set(PARAMETERS, parameters);

        return value;
    }

    public static ObjectNode buildDelete(final DataSchemaNode node, ArrayNode pathParams) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(NAME, DELETE + "-" + node.getQName().getLocalName());
        value.put(DESCRIPTION, node.getDescription().orElse(""));
        ArrayNode parameters = JsonUtil.copy(pathParams);
        value.set(PARAMETERS, parameters);
        return value;
    }
}
