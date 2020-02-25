/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.model.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionNames;
import org.opendaylight.netconf.sal.rest.doc.util.JsonUtil;
import org.opendaylight.yangtools.yang.model.api.*;

import static org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator.INPUT_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator.OUTPUT_SUFFIX;

public class OperationBuilder {
    public static final String BODY = "body";
    public static final String CONFIG = "_config";
    public static final String CONFIG_QUERY_PARAM = "config";
    public static final String CONSUMES_KEY = "consumes";
    public static final String DEFINITIONS_PREFIX = "#/definitions/";
    public static final String DESCRIPTION_KEY = "description";
    public static final String IN_KEY = "in";
    public static final String NAME_KEY = "name";
    public static final String NONCONFIG_QUERY_PARAM = "nonconfig";
    public static final String PARAMETERS_KEY = "parameters";
    public static final String PROPERTIES_KEY = "properties";
    public static final String REF_KEY = "$ref";
    public static final String RESPONSES_KEY = "responses";
    public static final String SCHEMA_KEY = "schema";
    public static final String SUMMARY_KEY = "summary";
    public static final String TOP = "_TOP";

    private static final String QUERY = "query";
    private static final String STRING = "string";
    private static final String CONTENT = "content";
    private static final String TYPE_KEY = "type";
    private static final String ENUM_KEY = "enum";
    private static final String REQUIRED_KEY = "required";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String DELETE = "DELETE";

    public static final ArrayNode CONSUMES_PUT_POST;

    static {
        CONSUMES_PUT_POST = JsonNodeFactory.instance.arrayNode();
        CONSUMES_PUT_POST.add("application/xml");
        CONSUMES_PUT_POST.add("application/json");
    }

    public static ObjectNode buildPost(final String nodeName, final String description,
                                       final String defName, final ArrayNode pathParams) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        final String operationId = POST + "-" + nodeName;
        value.put(DESCRIPTION_KEY, description);
        value.put(SUMMARY_KEY, operationId);
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode ref = JsonNodeFactory.instance.objectNode();
        payload.put(IN_KEY, BODY);
        payload.put(NAME_KEY, nodeName + CONFIG);
        ref.put(REF_KEY, DEFINITIONS_PREFIX + defName);
        payload.set(SCHEMA_KEY, ref);

        parameters.add(payload);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode createdResponse = JsonNodeFactory.instance.objectNode();
        createdResponse.put(DESCRIPTION_KEY, "Created");
        responses.set("201", createdResponse);

        value.set(RESPONSES_KEY, responses);
        value.set(CONSUMES_KEY, CONSUMES_PUT_POST);
        return value;
    }

    public static ObjectNode buildGet(final DataSchemaNode node, ArrayNode pathParams, String defName,
                                      boolean isConfig, ApiDocServiceImpl.URIType uriType) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, node.getDescription().orElse(""));
        final String operationId = GET + "-" + node.getQName().getLocalName();
        value.put(SUMMARY_KEY, operationId);
        ArrayNode parameters = JsonUtil.copy(pathParams);

        addQueryParameters(parameters, isConfig, uriType);

        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode okResponse = JsonNodeFactory.instance.objectNode();
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, DEFINITIONS_PREFIX + defName);
        okResponse.put(DESCRIPTION_KEY, "OK");
        okResponse.set(SCHEMA_KEY, schema);
        responses.set("200", okResponse);

        value.set(RESPONSES_KEY, responses);
        return value;
    }

    private static void addQueryParameters(ArrayNode parameters, boolean isConfig, ApiDocServiceImpl.URIType uriType) {
        if (uriType.equals(ApiDocServiceImpl.URIType.RFC8040)) {
            ObjectNode contentParam = JsonNodeFactory.instance.objectNode();
            ArrayNode cases = JsonNodeFactory.instance.arrayNode();
            cases.add(NONCONFIG_QUERY_PARAM);
            if (isConfig) {
                cases.add(CONFIG_QUERY_PARAM);
            } else {
                contentParam.put(REQUIRED_KEY, true);
            }
            contentParam.put(IN_KEY, QUERY);
            contentParam.put(NAME_KEY, CONTENT);
            contentParam.put(TYPE_KEY, STRING);
            contentParam.set(ENUM_KEY, cases);
            parameters.add(contentParam);
        }
    }

    public static ObjectNode buildPut(final String nodeName, final String description, final String defName, ArrayNode pathParams) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, description);
        final String operationId = PUT + "-" + nodeName;
        value.put(SUMMARY_KEY, operationId);

        ArrayNode parameters = JsonUtil.copy(pathParams);

        final ObjectNode bodyParam = JsonNodeFactory.instance.objectNode();
        bodyParam.put(NAME_KEY, nodeName + CONFIG);
        bodyParam.put(IN_KEY, BODY);
        ObjectNode ref = JsonNodeFactory.instance.objectNode();
        ref.put(REF_KEY, DEFINITIONS_PREFIX + defName);
        bodyParam.set(SCHEMA_KEY, ref);
        parameters.add(bodyParam);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode createdResponse = JsonNodeFactory.instance.objectNode();
        createdResponse.put(DESCRIPTION_KEY, "Created");
        responses.set("201", createdResponse);
        final ObjectNode updatedResponse = JsonNodeFactory.instance.objectNode();
        updatedResponse.put(DESCRIPTION_KEY, "Updated");
        responses.set("204", updatedResponse);

        value.set(RESPONSES_KEY, responses);
        value.set(CONSUMES_KEY, CONSUMES_PUT_POST);
        return value;
    }

    public static ObjectNode buildDelete(final DataSchemaNode node, ArrayNode pathParams) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(SUMMARY_KEY, DELETE + "-" + node.getQName().getLocalName());
        value.put(DESCRIPTION_KEY, node.getDescription().orElse(""));
        ArrayNode parameters = JsonUtil.copy(pathParams);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode deletedResponse = JsonNodeFactory.instance.objectNode();
        deletedResponse.put(DESCRIPTION_KEY, "Deleted");
        responses.set("204", deletedResponse);

        value.set(RESPONSES_KEY, responses);
        return value;
    }

    public static ObjectNode buildPostOperation(final OperationDefinition operDef,
                                                final String parentName,
                                                final DefinitionNames definitionNames) {
        final ObjectNode postOperation = JsonNodeFactory.instance.objectNode();
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode();
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode schemaRequest = JsonNodeFactory.instance.objectNode();
        final String operName = operDef.getQName().getLocalName();

        final ContainerSchemaNode input = operDef.getInput();
        final ContainerSchemaNode output = operDef.getOutput();
        if (!input.getChildNodes().isEmpty()) {
            final String defName = parentName + "_" + operName + INPUT_SUFFIX + TOP + definitionNames.getDiscriminator(input);
            schemaRequest.put(REF_KEY, DEFINITIONS_PREFIX + defName);
        } else {
            final ObjectNode properties = JsonNodeFactory.instance.objectNode();
            properties.set("input", JsonNodeFactory.instance.objectNode());
            schemaRequest.set(PROPERTIES_KEY, properties);
        }
        payload.put(IN_KEY, BODY);
        payload.put(NAME_KEY, operName + INPUT_SUFFIX);
        payload.set(SCHEMA_KEY, schemaRequest);
        parameters.add(payload);
        postOperation.set(CONSUMES_KEY, CONSUMES_PUT_POST);
        postOperation.set(PARAMETERS_KEY, parameters);
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put(DESCRIPTION_KEY, String.format("RPC %s success", operName));

        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            final String defName = parentName + "_" + operName + OUTPUT_SUFFIX + TOP + definitionNames.getDiscriminator(output);
            schema.put(REF_KEY, DEFINITIONS_PREFIX + defName);
            response.set(SCHEMA_KEY, schema);
            responses.set("200", response);
        } else {
            responses.set("204", response);
        }
        postOperation.set(RESPONSES_KEY, responses);
        postOperation.put(DESCRIPTION_KEY, operDef.getDescription().orElse(""));
        postOperation.put(SUMMARY_KEY, operDef.getQName().getLocalName());
        return postOperation;
    }
}
