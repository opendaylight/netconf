/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.openapi.model.builder;

import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.INPUT;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.INPUT_SUFFIX;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.OUTPUT_SUFFIX;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String CONFIG = "_config";
    public static final String CONFIG_QUERY_PARAM = "config";
    public static final String CONTENT_KEY = "content";
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String DESCRIPTION_KEY = "description";
    public static final String IN_KEY = "in";
    public static final String INPUT_KEY = "input";
    public static final String NAME_KEY = "name";
    public static final String NONCONFIG_QUERY_PARAM = "nonconfig";
    public static final String PROPERTIES_KEY = "properties";
    public static final String REF_KEY = "$ref";
    public static final String SCHEMA_KEY = "schema";
    public static final String SUMMARY_SEPARATOR = " - ";
    public static final String TOP = "_TOP";
    public static final String XML_KEY = "xml";
    private static final String CONTENT = "content";
    private static final ArrayNode CONSUMES_PUT_POST;
    private static final String ENUM_KEY = "enum";
    private static final List<String> MIME_TYPES = List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON);
    private static final String OBJECT = "object";
    private static final String REQUIRED_KEY = "required";
    private static final String STRING = "string";
    private static final String TYPE_KEY = "type";
    private static final String QUERY = "query";

    static {
        CONSUMES_PUT_POST = JsonNodeFactory.instance.arrayNode();
        for (final String mimeType : MIME_TYPES) {
            CONSUMES_PUT_POST.add(mimeType);
        }
    }

    private OperationBuilder() {

    }

    public static Operation buildPost(final String parentName, final String nodeName, final String discriminator,
            final String moduleName, final Optional<String> deviceName, final String description,
            final ArrayNode pathParams) {
        final Operation postOperation = new Operation();
        postOperation.setDescription(description);
        postOperation.setSummary(buildSummaryValue(HttpMethod.POST, moduleName, deviceName, nodeName));
        postOperation.setTags(buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final ObjectNode ref = JsonNodeFactory.instance.objectNode();
        final String cleanDefName = parentName + CONFIG + "_" + nodeName;
        final String defName = cleanDefName + discriminator;
        final String xmlDefName = cleanDefName + discriminator;
        ref.put(REF_KEY, COMPONENTS_PREFIX + defName);
        insertRequestBodyParameter(postOperation, defName, xmlDefName, nodeName + CONFIG);
        postOperation.setParameters(parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty()));

        postOperation.setResponses(responses);
        return postOperation;
    }

    public static Operation buildGet(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams, final String defName,
            final boolean isConfig) {
        final Operation getOperation = new Operation();
        getOperation.setDescription(node.getDescription().orElse(""));
        getOperation.setSummary(buildSummaryValue(HttpMethod.GET, moduleName, deviceName,
                node.getQName().getLocalName()));
        getOperation.setTags(buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);

        addQueryParameters(parameters, isConfig);

        getOperation.setParameters(parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), Optional.of(schema)));

        getOperation.setResponses(responses);
        return getOperation;
    }

    private static void addQueryParameters(final ArrayNode parameters, final boolean isConfig) {
        final ObjectNode contentParam = JsonNodeFactory.instance.objectNode();
        final ArrayNode cases = JsonNodeFactory.instance.arrayNode();
        cases.add(NONCONFIG_QUERY_PARAM);
        if (isConfig) {
            cases.add(CONFIG_QUERY_PARAM);
        } else {
            contentParam.put(REQUIRED_KEY, true);
        }
        contentParam.put(IN_KEY, QUERY);
        contentParam.put(NAME_KEY, CONTENT);

        final ObjectNode typeParent = getTypeParentNode(contentParam);
        typeParent.put(TYPE_KEY, STRING);
        typeParent.set(ENUM_KEY, cases);

        parameters.add(contentParam);
    }

    public static Operation buildPut(final String parentName, final String nodeName, final String discriminator,
            final String moduleName, final Optional<String> deviceName, final String description,
            final ArrayNode pathParams) {
        final Operation putOperation = new Operation();
        putOperation.setDescription(description);
        putOperation.setSummary(buildSummaryValue(HttpMethod.PUT, moduleName, deviceName, nodeName));
        putOperation.setTags(buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        insertRequestBodyParameter(putOperation, defName, xmlDefName, nodeName + CONFIG);
        putOperation.setParameters(parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty()));

        putOperation.setResponses(responses);
        return putOperation;
    }

    public static Operation buildPatch(final String parentName, final String nodeName, final String moduleName,
            final Optional<String> deviceName, final String description, final ArrayNode pathParams) {
        final Operation patchOperation = new Operation();
        patchOperation.setDescription(description);
        patchOperation.setSummary(buildSummaryValue(HttpMethod.PATCH, moduleName, deviceName, nodeName));
        patchOperation.setTags(buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        insertRequestBodyParameter(patchOperation, defName, xmlDefName, nodeName + CONFIG);
        patchOperation.setParameters(parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), Optional.empty()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty()));

        patchOperation.setResponses(responses);
        return patchOperation;
    }

    public static Operation buildDelete(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams) {
        final Operation value = new Operation();
        value.setSummary(buildSummaryValue(HttpMethod.DELETE, moduleName, deviceName,
                node.getQName().getLocalName()));
        value.setTags(buildTagsValue(deviceName, moduleName));
        value.setDescription(node.getDescription().orElse(""));
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        value.setParameters(parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Deleted", Optional.empty()));

        value.setResponses(responses);
        return value;
    }

    public static Operation buildPostOperation(final OperationDefinition operDef, final String moduleName,
            final Optional<String> deviceName, final String parentName, final DefinitionNames definitionNames) {
        final Operation postOperation = new Operation();
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode();
        final String operName = operDef.getQName().getLocalName();
        final String inputName = operName + INPUT_SUFFIX;

        final InputSchemaNode input = operDef.getInput();
        final OutputSchemaNode output = operDef.getOutput();
        if (!input.getChildNodes().isEmpty()) {
            final String discriminator = definitionNames.getDiscriminator(input);
            final String clearDefName = parentName + "_" + operName + INPUT_SUFFIX;
            final String defName = clearDefName + discriminator;
            final String defTopName = clearDefName + TOP + discriminator;
            insertRequestBodyParameter(postOperation, defTopName, defName, inputName);
        } else {
            final ObjectNode payload = JsonNodeFactory.instance.objectNode();
            final ObjectNode jsonSchema = JsonNodeFactory.instance.objectNode();
            final ObjectNode properties = JsonNodeFactory.instance.objectNode();
            final ObjectNode inputSchema = JsonNodeFactory.instance.objectNode();
            inputSchema.put(TYPE_KEY, OBJECT);
            properties.set(INPUT_KEY, inputSchema);
            jsonSchema.put(TYPE_KEY, OBJECT);
            jsonSchema.set(PROPERTIES_KEY, properties);
            final ObjectNode content = JsonNodeFactory.instance.objectNode();
            final ObjectNode jsonTypeValue = JsonNodeFactory.instance.objectNode();
            jsonTypeValue.set(SCHEMA_KEY, jsonSchema);
            content.set(MediaType.APPLICATION_JSON, jsonTypeValue);

            final ObjectNode xmlSchema = JsonNodeFactory.instance.objectNode();
            xmlSchema.put(TYPE_KEY, OBJECT);
            final ObjectNode xml = JsonNodeFactory.instance.objectNode();
            xml.put(NAME_KEY, INPUT);
            xmlSchema.set(XML_KEY, xml);
            final ObjectNode xmlTypeValue = JsonNodeFactory.instance.objectNode();
            xmlTypeValue.set(SCHEMA_KEY, xmlSchema);
            content.set(MediaType.APPLICATION_XML, xmlTypeValue);

            payload.set(CONTENT_KEY, content);
            payload.put(DESCRIPTION_KEY, inputName);
            postOperation.setRequestBody(payload);
        }
        postOperation.setParameters(parameters);
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final String description = String.format("RPC %s success", operName);

        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            final String defName = parentName + "_" + operName + OUTPUT_SUFFIX + TOP
                    + definitionNames.getDiscriminator(output);
            schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
            responses.set(String.valueOf(Response.Status.OK.getStatusCode()), buildResponse(description,
                    Optional.of(schema)));
        } else {
            responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse(description,
                    Optional.empty()));
        }
        postOperation.setResponses(responses);
        postOperation.setDescription(operDef.getDescription().orElse(""));
        postOperation.setSummary(buildSummaryValue(HttpMethod.POST, moduleName, deviceName, operName));
        postOperation.setTags(buildTagsValue(deviceName, moduleName));
        return postOperation;
    }

    private static void insertRequestBodyParameter(final Operation operation, final String defName,
            final String xmlDefName, final String name) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final String node = operation.getSummary();
        if (node != null && node.contains(HttpMethod.PATCH)) {
            content.set("application/yang-data+json", buildMimeTypeValue(defName));
            content.set("application/yang-data+xml", buildMimeTypeValue(xmlDefName));
        } else {
            content.set(MediaType.APPLICATION_JSON, buildMimeTypeValue(defName));
            content.set(MediaType.APPLICATION_XML, buildMimeTypeValue(xmlDefName));
        }
        payload.set(CONTENT_KEY, content);
        payload.put(DESCRIPTION_KEY, name);
        operation.setRequestBody(payload);
    }

    private static ObjectNode buildRefSchema(final String defName) {
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
        return schema;
    }

    private static ObjectNode buildMimeTypeValue(final String defName) {
        final ObjectNode mimeTypeValue = JsonNodeFactory.instance.objectNode();
        mimeTypeValue.set(SCHEMA_KEY, buildRefSchema(defName));
        return mimeTypeValue;
    }

    public static ObjectNode buildResponse(final String description, final Optional<ObjectNode> schema) {
        final ObjectNode response = JsonNodeFactory.instance.objectNode();

        if (schema.isPresent()) {
            final ObjectNode schemaValue = schema.orElseThrow();
            final ObjectNode content = JsonNodeFactory.instance.objectNode();
            final ObjectNode body = JsonNodeFactory.instance.objectNode();
            for (final String mimeType : MIME_TYPES) {
                content.set(mimeType, body);
            }
            body.set(SCHEMA_KEY, schemaValue);
            response.set(CONTENT_KEY, content);
        }
        response.put(DESCRIPTION_KEY, description);
        return response;
    }

    private static String buildSummaryValue(final String httpMethod, final String moduleName,
            final Optional<String> deviceName, final String nodeName) {
        return httpMethod + SUMMARY_SEPARATOR + deviceName.map(s -> s + SUMMARY_SEPARATOR).orElse("")
                + moduleName + SUMMARY_SEPARATOR + nodeName;
    }

    public static ArrayNode buildTagsValue(final Optional<String> deviceName, final String moduleName) {
        final ArrayNode tagsValue = JsonNodeFactory.instance.arrayNode();
        tagsValue.add(deviceName.map(s -> "mounted " + s).orElse("controller") + " " + moduleName);
        return tagsValue;
    }

    public static ObjectNode getTypeParentNode(final ObjectNode parameter) {
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        parameter.set(SCHEMA_KEY, schema);
        return schema;
    }
}
