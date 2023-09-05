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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.model.MediaTypeObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Parameter;
import org.opendaylight.restconf.openapi.model.RequestBody;
import org.opendaylight.restconf.openapi.model.ResponseObject;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String INPUT_KEY = "input";
    public static final String NAME_KEY = "name";
    public static final String REF_KEY = "$ref";
    public static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    public static final String XML_KEY = "xml";
    private static final List<String> MIME_TYPES = List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON);
    private static final String OBJECT = "object";
    private static final String TYPE_KEY = "type";

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
        final RequestBody requestBody;
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
        final Map<String, ResponseObject> responses = new HashMap<>();
        responses.put(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase()));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(responses)
            .description(description)
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
        final Map<String, ResponseObject> responses = new HashMap<>();

        final boolean isList = node instanceof ListSchemaNode;
        final ResponseObject response = createResponse(defName, nodeName, isList,
                String.valueOf(Response.Status.OK.getStatusCode()), summary);
        responses.put(String.valueOf(Response.Status.OK.getStatusCode()), response);

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    private static Parameter buildQueryParameters(final boolean isConfig) {
        final ArrayNode cases = JsonNodeFactory.instance.arrayNode()
            .add("config")
            .add("nonconfig")
            .add("all");

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
        final RequestBody requestBody = createRequestBodyParameter(defName, fullName, isList, summary, nodeName);

        final Map<String, ResponseObject> responses = new HashMap<>();
        responses.put(String.valueOf(Response.Status.CREATED.getStatusCode()),
            buildResponse(Response.Status.CREATED.getReasonPhrase()));
        responses.put(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Updated"));

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
        final RequestBody requestBody = createRequestBodyParameter(defName, fullName, isList, summary, nodeName);

        final Map<String, ResponseObject> responses = new HashMap<>();
        responses.put(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase()));
        responses.put(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Updated"));

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

        final Map<String, ResponseObject> responses = new HashMap<>();
        responses.put(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse("Deleted"));

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
        final RequestBody requestBody;
        if (!input.getChildNodes().isEmpty()) {
            final String discriminator = definitionNames.getDiscriminator(input);
            final String clearDefName = parentName + "_" + operationName + INPUT_SUFFIX;
            final String defName = clearDefName + discriminator;
            requestBody = createRequestBodyParameter(defName, INPUT_KEY, false, summary, inputName);
        } else {
            final ObjectNode properties = JsonNodeFactory.instance.objectNode();
            final ObjectNode inputSchema = JsonNodeFactory.instance.objectNode();
            inputSchema.put(TYPE_KEY, OBJECT);
            properties.set(INPUT_KEY, inputSchema);
            final Schema jsonSchema = new Schema.Builder()
                .type(OBJECT)
                .properties(properties)
                .build();
            final Map<String, MediaTypeObject> content = new HashMap<>();
            final MediaTypeObject jsonTypeValue = new MediaTypeObject.Builder()
                .schema(jsonSchema)
                .build();
            content.put(MediaType.APPLICATION_JSON, jsonTypeValue);

            final ObjectNode xml = JsonNodeFactory.instance.objectNode();
            xml.put(NAME_KEY, INPUT);
            final Schema xmlSchema = new Schema.Builder()
                .type(OBJECT)
                .xml(xml)
                .build();
            final MediaTypeObject xmlTypeValue = new MediaTypeObject.Builder()
                .schema(xmlSchema)
                .build();
            content.put(MediaType.APPLICATION_XML, xmlTypeValue);
            requestBody = new RequestBody.Builder()
                .content(content)
                .description(inputName)
                .build();
        }
        final Map<String, ResponseObject> responses = new HashMap<>();
        final String description = String.format("RPC %s success", operationName);

        if (!output.getChildNodes().isEmpty()) {
            final String defName = parentName + "_" + operationName + OUTPUT_SUFFIX
                + definitionNames.getDiscriminator(output);
            final Schema schema = new Schema.Builder()
                .ref(COMPONENTS_PREFIX + defName)
                .build();
            responses.put(String.valueOf(Response.Status.OK.getStatusCode()), buildResponse(description, schema));
        } else {
            responses.put(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse(description));
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

    private static RequestBody createPostDataRequestBodyParameter(final String defName, final String name) {
        final Map<String, MediaTypeObject> content = new HashMap<>();
        final MediaTypeObject value = buildMediaTypeObject(defName);
        content.put(MediaType.APPLICATION_JSON, value);
        content.put(MediaType.APPLICATION_XML, value);
        return new RequestBody.Builder()
            .content(content)
            .description(name)
            .build();
    }

    private static RequestBody createRequestBodyParameter(final String defName, final String name,
            final boolean isList, final String summary, final String description) {
        final Map<String, MediaTypeObject> content = getStringMediaTypeObjectMap(defName, name, isList, summary);
        return new RequestBody.Builder()
            .content(content)
            .description(description)
            .build();
    }

    private static ResponseObject createResponse(final String defName, final String name,
        final boolean isList, final String description, final String summary) {
        final Map<String, MediaTypeObject> content = getStringMediaTypeObjectMap(defName, name, isList, summary);
        return new ResponseObject.Builder()
            .content(content)
            .description(description)
            .build();
    }

    private static Map<String, MediaTypeObject> getStringMediaTypeObjectMap(final String defName, final String name,
            final boolean isList, final String summary) {
        final Map<String, MediaTypeObject> content = new HashMap<>();
        final Schema.Builder schemaBuilder = new Schema.Builder();
        if (isList) {
            final ObjectNode list = JsonNodeFactory.instance.objectNode();
            final ObjectNode listValue = JsonNodeFactory.instance.objectNode();
            listValue.put(TYPE_KEY, "array");
            listValue.set("items", buildRefSchema(defName));
            list.set(name, listValue);
            schemaBuilder.properties(list);
        } else {
            final ObjectNode container = JsonNodeFactory.instance.objectNode();
            container.set(name, buildRefSchema(defName));
            schemaBuilder.properties(container);
        }
        final MediaTypeObject jsonSchema = new MediaTypeObject.Builder()
            .schema(schemaBuilder.build())
            .build();
        if (summary != null && summary.contains(HttpMethod.PATCH)) {
            content.put("application/yang-data+json", jsonSchema);
            content.put("application/yang-data+xml", buildMediaTypeObject(defName));
        } else {
            content.put(MediaType.APPLICATION_JSON, jsonSchema);
            content.put(MediaType.APPLICATION_XML, buildMediaTypeObject(defName));
        }
        return content;
    }

    private static ObjectNode buildRefSchema(final String defName) {
        //TODO this should return Schema object but this can't be done now because wo don't have Properties object
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, COMPONENTS_PREFIX + defName);
        return schema;
    }

    private static MediaTypeObject buildMediaTypeObject(final String defName) {
        final ObjectNode refSchema = buildRefSchema(defName);
        final Schema schema = new Schema.Builder()
            .ref(refSchema.path("$ref").textValue())
            .build();
        return new MediaTypeObject.Builder()
            .schema(schema)
            .build();
    }

    private static ResponseObject buildResponse(final String description) {
        return new ResponseObject.Builder()
            .description(description)
            .build();
    }

    private static ResponseObject buildResponse(final String description, final Schema schema) {
        final Map<String, MediaTypeObject> content = new HashMap<>();
        final MediaTypeObject body = new MediaTypeObject.Builder()
            .schema(schema)
            .build();
        for (final String mimeType : MIME_TYPES) {
            content.put(mimeType, body);
        }
        return new ResponseObject.Builder()
            .content(content)
            .description(description)
            .build();
    }

    private static DataSchemaNode getListOrContainerChildNode(final DataSchemaNode node) {
        return ((DataNodeContainer) node).getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }
}
