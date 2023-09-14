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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Parameter;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String CONTENT_KEY = "content";
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String DESCRIPTION_KEY = "description";
    public static final String INPUT_KEY = "input";
    public static final String NAME_KEY = "name";
    public static final String PROPERTIES_KEY = "properties";
    public static final String REF_KEY = "$ref";
    public static final String SCHEMA_KEY = "schema";
    public static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    public static final String XML_KEY = "xml";
    private static final List<String> MIME_TYPES = List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON);
    private static final String OBJECT = "object";
    private static final String TYPE_KEY = "type";
    private static final String POST_DESCRIPTION = """
        \n
        Note:
        In example payload, you can see only the first data node child of the resource to be created, following the
        guidelines of RFC 8040, which allows us to create only one resource in POST request.
        """;

    private OperationBuilder() {
        // Hidden on purpose
    }

    public static Operation buildPost(final DataSchemaNode node, final String parentName,
        final String nodeName,
            final String discriminator, final String moduleName, final @NonNull String deviceName,
            final String description, final List<Parameter> pathParams) {
        final var summary = SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName, moduleName, nodeName);
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final List<Parameter> parameters = new ArrayList<>(pathParams);
        final ObjectNode requestBody;
        final DataSchemaNode childNode = node == null ? null : getListOrContainerChildNode(node);
        if (childNode != null && childNode.isConfiguration()) {
            final String childNodeName = childNode.getQName().getLocalName();
            final String childDefName = parentName + "_" + nodeName + "_" + childNodeName + discriminator;
            requestBody = createRequestBodyParameter(childDefName, childNodeName, childNode instanceof ListSchemaNode,
                summary, childNodeName);
        } else {
            final String defName = parentName + "_" + nodeName + discriminator;
            requestBody = createPostDataRequestBodyParameter(defName, nodeName);
        }
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(description + POST_DESCRIPTION)
            .summary(summary)
            .build();
    }

    public static Operation buildGet(final DataSchemaNode node, final String parentName, final String moduleName,
            final @NonNull String deviceName, final List<Parameter> pathParams, final boolean isConfig) {
        final String nodeName = node.getQName().getLocalName();
        final String defName = parentName + "_" + nodeName;
        final String description = node.getDescription().orElse("");
        final String summary = SUMMARY_TEMPLATE.formatted(HttpMethod.GET, deviceName, moduleName,
                node.getQName().getLocalName());
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final List<Parameter> parameters = new ArrayList<>(pathParams);
        parameters.add(buildQueryParameters(isConfig));
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();

        final boolean isList = node instanceof ListSchemaNode;
        final ObjectNode response = createRequestBodyParameter(defName, nodeName, isList, summary,
                String.valueOf(Response.Status.OK.getStatusCode()));
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()), response);

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    private static Parameter buildQueryParameters(final boolean isConfig) {
        final List<String> cases = List.of("config", "nonconfig", "all");

        return new Parameter.Builder()
            .in("query")
            .name("content")
            .required(!isConfig)
            .schema(new Schema.Builder().type("string").schemaEnum(cases).build())
            .build();
    }

    public static Operation buildPut(final DataSchemaNode node, final String parentName, final String moduleName,
            final @NonNull String deviceName, final List<Parameter> pathParams, final String fullName) {
        final String nodeName = node.getQName().getLocalName();
        final String summary = SUMMARY_TEMPLATE.formatted(HttpMethod.PUT, moduleName, deviceName, nodeName);
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final List<Parameter> parameters = new ArrayList<>(pathParams);
        final String defName = parentName + "_" + nodeName;
        final boolean isList = node instanceof ListSchemaNode;
        final ObjectNode requestBody = createRequestBodyParameter(defName, fullName, isList, summary, nodeName);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
            buildResponse(Response.Status.CREATED.getReasonPhrase()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Updated"));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(node.getDescription().orElse(""))
            .summary(summary)
            .build();
    }

    public static Operation buildPatch(final DataSchemaNode node, final String parentName, final String moduleName,
            final @NonNull String deviceName, final List<Parameter> pathParams, final String fullName) {
        final String nodeName = node.getQName().getLocalName();
        final String summary = SUMMARY_TEMPLATE.formatted(HttpMethod.PATCH, moduleName, deviceName, nodeName);
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final List<Parameter> parameters = new ArrayList<>(pathParams);
        final String defName = parentName + "_" + nodeName;
        final boolean isList = node instanceof ListSchemaNode;
        final ObjectNode requestBody = createRequestBodyParameter(defName, fullName, isList, summary, nodeName);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase()));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Updated"));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(node.getDescription().orElse(""))
            .summary(summary)
            .build();
    }

    public static Operation buildDelete(final DataSchemaNode node, final String moduleName,
            final @NonNull String deviceName, final List<Parameter> pathParams) {
        final String summary = SUMMARY_TEMPLATE.formatted(HttpMethod.DELETE, deviceName, moduleName,
                node.getQName().getLocalName());
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final String description = node.getDescription().orElse("");
        final List<Parameter> parameters = new ArrayList<>(pathParams);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Deleted"));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    public static Operation buildPostOperation(final OperationDefinition operDef, final String moduleName,
            final @NonNull String deviceName, final String parentName, final DefinitionNames definitionNames,
            final List<Parameter> parentPathParameters) {
        final List<Parameter> parameters = new ArrayList<>(parentPathParameters);
        final String operationName = operDef.getQName().getLocalName();
        final String inputName = operationName + INPUT_SUFFIX;
        final String summary = SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName, moduleName, operationName);

        final InputSchemaNode input = operDef.getInput();
        final OutputSchemaNode output = operDef.getOutput();
        ObjectNode requestBody;
        if (!input.getChildNodes().isEmpty()) {
            final String discriminator = definitionNames.getDiscriminator(input);
            final String clearDefName = parentName + "_" + operationName + INPUT_SUFFIX;
            final String defName = clearDefName + discriminator;
            requestBody = createRequestBodyParameter(defName, INPUT_KEY, false, summary, inputName);
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
            requestBody = payload;
        }
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final String description = String.format("RPC %s success", operationName);

        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            final String defName = parentName + "_" + operationName + OUTPUT_SUFFIX
                    + definitionNames.getDiscriminator(output);
            schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
            responses.set(String.valueOf(Response.Status.OK.getStatusCode()), buildResponse(description, schema));
        } else {
            responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse(description));
        }
        final String desc = operDef.getDescription().orElse("");
        final List<String> tags = List.of(deviceName + " " + moduleName);
        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(desc)
            .summary(summary)
            .build();
    }

    private static ObjectNode createPostDataRequestBodyParameter(final String defName, final String name) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final ObjectNode value = buildMimeTypeValue(defName);
        content.set(MediaType.APPLICATION_JSON, value);
        content.set(MediaType.APPLICATION_XML, value);
        payload.set(CONTENT_KEY, content);
        payload.put(DESCRIPTION_KEY, name);
        return payload;
    }

    private static ObjectNode createRequestBodyParameter(final String defName, final String name,
            final boolean isList, final String summary, final String description) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        if (isList) {
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
        if (summary != null && summary.contains(HttpMethod.PATCH)) {
            content.set("application/yang-data+json", jsonSchema);
            content.set("application/yang-data+xml", buildMimeTypeValue(defName));
        } else {
            content.set(MediaType.APPLICATION_JSON, jsonSchema);
            content.set(MediaType.APPLICATION_XML, buildMimeTypeValue(defName));
        }
        payload.set(CONTENT_KEY, content);
        payload.put(DESCRIPTION_KEY, description);
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

    private static ObjectNode buildResponse(final String description) {
        final ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put(DESCRIPTION_KEY, description);
        return response;
    }

    private static ObjectNode buildResponse(final String description, final ObjectNode schema) {
        final ObjectNode response = JsonNodeFactory.instance.objectNode();
        final ObjectNode content = JsonNodeFactory.instance.objectNode();
        final ObjectNode body = JsonNodeFactory.instance.objectNode();
        for (final String mimeType : MIME_TYPES) {
            content.set(mimeType, body);
        }
        body.set(SCHEMA_KEY, schema);
        response.set(CONTENT_KEY, content);
        response.put(DESCRIPTION_KEY, description);
        return response;
    }

    private static DataSchemaNode getListOrContainerChildNode(final DataSchemaNode node) {
        return ((DataNodeContainer) node).getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }
}
