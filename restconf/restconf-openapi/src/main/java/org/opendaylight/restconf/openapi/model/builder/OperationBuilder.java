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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String CONFIG = "_config";
    public static final String CONTENT_KEY = "content";
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String DESCRIPTION_KEY = "description";
    public static final String IN_KEY = "in";
    public static final String INPUT_KEY = "input";
    public static final String NAME_KEY = "name";
    public static final String PROPERTIES_KEY = "properties";
    public static final String REF_KEY = "$ref";
    public static final String SCHEMA_KEY = "schema";
    public static final String SUMMARY_SEPARATOR = " - ";
    public static final String TOP = "_TOP";
    public static final String XML_KEY = "xml";
    private static final ArrayNode CONSUMES_PUT_POST;
    private static final String ENUM_KEY = "enum";
    private static final List<String> MIME_TYPES = List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON);
    private static final String OBJECT = "object";
    private static final String REQUIRED_KEY = "required";
    private static final String TYPE_KEY = "type";

    static {
        CONSUMES_PUT_POST = JsonNodeFactory.instance.arrayNode();
        for (final String mimeType : MIME_TYPES) {
            CONSUMES_PUT_POST.add(mimeType);
        }
    }

    private OperationBuilder() {

    }

    public static Operation buildPost(final DataSchemaNode node, final String parentName, final String nodeName,
            final String discriminator, final String moduleName, final Optional<String> deviceName,
            final String description, final ArrayNode pathParams) {
        final var summary = buildSummaryValue(HttpMethod.POST, moduleName, deviceName, nodeName);
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final ObjectNode requestBody;
        final DataSchemaNode childNode = getListOrContainerChildNode(Optional.ofNullable(node));

        final List<String> nameElements = new ArrayList<>();
        if (childNode != null && childNode.isConfiguration()) {
            final String childNodeName = childNode.getQName().getLocalName();
            if (parentName != null) {
                nameElements.add(parentName);
            }
            nameElements.add(nodeName + CONFIG);
            nameElements.add(childNodeName + discriminator);
            final String childDefName = String.join("_", nameElements);
            requestBody = createPostRequestBodyParameter(childNode, childDefName, childNodeName);
        } else {
            if (parentName != null) {
                nameElements.add(parentName + CONFIG);
            }
            nameElements.add(nodeName + discriminator);
            final String defName = String.join("_", nameElements);
            requestBody = createRequestBodyParameter(defName, defName, nodeName + CONFIG, summary);
        }

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    public static Operation buildGet(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams, final String defName,
            final String defNameTop, final boolean isConfig) {
        final String description = node.getDescription().orElse("");
        final String summary = buildSummaryValue(HttpMethod.GET, moduleName, deviceName,
                node.getQName().getLocalName());
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        parameters.add(buildQueryParameters(isConfig));
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        final ObjectNode xmlSchema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, COMPONENTS_PREFIX + defNameTop);
        xmlSchema.put(REF_KEY, COMPONENTS_PREFIX + defName);

        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), schema, xmlSchema));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    private static ObjectNode buildQueryParameters(final boolean isConfig) {
        final ObjectNode contentParam = JsonNodeFactory.instance.objectNode();
        final ArrayNode cases = JsonNodeFactory.instance.arrayNode();
        if (isConfig) {
            cases.add("config");
            cases.add("nonconfig");
            cases.add("all");
        } else {
            cases.add("nonconfig");
            contentParam.put(REQUIRED_KEY, true);
        }
        contentParam.put(IN_KEY, "query");
        contentParam.put(NAME_KEY, "content");

        final ObjectNode typeParent = getTypeParentNode(contentParam);
        typeParent.put(TYPE_KEY, "string");
        typeParent.set(ENUM_KEY, cases);

        return contentParam;
    }

    public static Operation buildPut(final String parentName, final String nodeName, final String discriminator,
            final String moduleName, final Optional<String> deviceName, final String description,
            final ArrayNode pathParams) {
        final String summary = buildSummaryValue(HttpMethod.PUT, moduleName, deviceName, nodeName);
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        final ObjectNode requestBody = createRequestBodyParameter(defName, xmlDefName, nodeName + CONFIG, summary);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    public static Operation buildPatch(final String parentName, final String nodeName, final String moduleName,
            final Optional<String> deviceName, final String description, final ArrayNode pathParams) {
        final String summary = buildSummaryValue(HttpMethod.PATCH, moduleName, deviceName, nodeName);
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        final ObjectNode requestBody = createRequestBodyParameter(defName, xmlDefName, nodeName + CONFIG, summary);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), Optional.empty()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    public static Operation buildDelete(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams) {
        final String summary = buildSummaryValue(HttpMethod.DELETE, moduleName, deviceName,
                node.getQName().getLocalName());
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        final String description = node.getDescription().orElse("");
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(pathParams);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Deleted", Optional.empty()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    public static Operation buildPostOperation(final OperationDefinition operDef, final String moduleName,
            final Optional<String> deviceName, final String parentName, final DefinitionNames definitionNames,
            final ArrayNode parentPathParameters) {
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(parentPathParameters);
        final String operationName = operDef.getQName().getLocalName();
        final String inputName = operationName + INPUT_SUFFIX;
        final String summary = buildSummaryValue(HttpMethod.POST, moduleName, deviceName, operationName);

        final InputSchemaNode input = operDef.getInput();
        final OutputSchemaNode output = operDef.getOutput();
        ObjectNode requestBody;
        if (!input.getChildNodes().isEmpty()) {
            final String discriminator = definitionNames.getDiscriminator(input);
            final String clearDefName = parentName + "_" + operationName + INPUT_SUFFIX;
            final String defName = clearDefName + discriminator;
            final String defTopName = clearDefName + TOP + discriminator;
            requestBody = createRequestBodyParameter(defTopName, defName, inputName, summary);
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
            xml.put("namespace", input.getQName().getNamespace().toString());
            xmlSchema.set(XML_KEY, xml);
            final ObjectNode xmlTypeValue = JsonNodeFactory.instance.objectNode();
            xmlTypeValue.set(SCHEMA_KEY, xmlSchema);
            content.set(MediaType.APPLICATION_XML, xmlTypeValue);

            payload.set(CONTENT_KEY, content);
            payload.put(DESCRIPTION_KEY, inputName);
            requestBody = payload;
        }
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final String description = String.format("RPC %s success", operationName);

        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            final String defName = parentName + "_" + operationName + OUTPUT_SUFFIX + TOP
                    + definitionNames.getDiscriminator(output);
            schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
            responses.set(String.valueOf(Response.Status.OK.getStatusCode()), buildResponse(description,
                    Optional.of(schema)));
        } else {
            responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse(description,
                    Optional.empty()));
        }
        final String desc = operDef.getDescription().orElse("");
        final ArrayNode tags = buildTagsValue(deviceName, moduleName);
        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(desc)
            .summary(summary)
            .build();
    }

    private static ObjectNode createRequestBodyParameter(final String defName, final String xmlDefName,
            final String name, final String summary) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        if (summary != null && summary.contains(HttpMethod.PATCH)) {
            content.set("application/yang-data+json", buildMimeTypeValue(defName));
            content.set("application/yang-data+xml", buildMimeTypeValue(xmlDefName));
        } else {
            content.set(MediaType.APPLICATION_JSON, buildMimeTypeValue(defName));
            content.set(MediaType.APPLICATION_XML, buildMimeTypeValue(xmlDefName));
        }
        payload.set(CONTENT_KEY, content);
        payload.put(DESCRIPTION_KEY, name);
        return payload;
    }

    private static ObjectNode createPostRequestBodyParameter(final DataSchemaNode childNode, final String defName,
        final String name) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        if (childNode instanceof ListSchemaNode) {
            final ObjectNode list = JsonNodeFactory.instance.objectNode();
            final ObjectNode listValue = JsonNodeFactory.instance.objectNode();
            listValue.put(TYPE_KEY, "array");
            listValue.set("items", buildRefSchema(defName));
            list.set(name, listValue);
            properties.set(PROPERTIES_KEY, list);
        } else {
            final ObjectNode container = JsonNodeFactory.instance.objectNode();
            container.set(name, buildRefSchema(defName));
            properties.set(PROPERTIES_KEY, container);
        }
        final ObjectNode jsonSchema = JsonNodeFactory.instance.objectNode();
        jsonSchema.set(SCHEMA_KEY, properties);
        content.set(MediaType.APPLICATION_JSON, jsonSchema);
        content.set(MediaType.APPLICATION_XML, buildMimeTypeValue(defName));
        payload.set(CONTENT_KEY, content);
        payload.put(DESCRIPTION_KEY, name);
        return payload;
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

    public static ObjectNode buildResponse(final String description, final ObjectNode schema,
            final ObjectNode xmlSchema) {
        final ObjectNode response = JsonNodeFactory.instance.objectNode();

        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final ObjectNode body = JsonNodeFactory.instance.objectNode();
        final ObjectNode xmlBody = JsonNodeFactory.instance.objectNode();

        body.set(SCHEMA_KEY, schema);
        xmlBody.set(SCHEMA_KEY, xmlSchema);
        content.set(MediaType.APPLICATION_JSON, body);
        content.set(MediaType.APPLICATION_XML, xmlBody);

        response.set(CONTENT_KEY, content);

        response.put(DESCRIPTION_KEY, description);
        return response;
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

    private static DataSchemaNode getListOrContainerChildNode(final Optional<DataSchemaNode> node) {
        return node.flatMap(schemaNode -> ((DataNodeContainer) schemaNode).getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst()).orElse(null);
    }
}
