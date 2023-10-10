/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model.builder;

import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.INPUT_SUFFIX;
import static org.opendaylight.restconf.openapi.impl.DefinitionGenerator.OUTPUT_SUFFIX;

import java.util.AbstractMap.SimpleEntry;
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
import org.opendaylight.restconf.openapi.model.Property;
import org.opendaylight.restconf.openapi.model.RequestBody;
import org.opendaylight.restconf.openapi.model.ResponseObject;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.restconf.openapi.model.Xml;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String INPUT_KEY = "input";
    public static final String SUMMARY_TEMPLATE = "%s - %s - %s - %s";
    private static final List<String> MIME_TYPES = List.of(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON);
    private static final String OBJECT = "object";
    private static final String POST_DESCRIPTION = """
        \n
        Note:
        In example payload, you can see only the first data node child of the resource to be created, following the
        guidelines of RFC 8040, which allows us to create only one resource in POST request.
        """;

    private OperationBuilder() {
        // Hidden on purpose
    }

    public static Operation buildPost(final DataSchemaNode childNode, final String parentName, final String nodeName,
            final String discriminator, final String moduleName, final @NonNull String deviceName,
            final String description, final List<Parameter> pathParams) {
        final var summary = SUMMARY_TEMPLATE.formatted(HttpMethod.POST, deviceName, moduleName, nodeName);
        final List<String> tags = List.of(deviceName + " " + moduleName);
        final List<Parameter> parameters = new ArrayList<>(pathParams);
        final RequestBody requestBody;
        final List<String> nameElements = new ArrayList<>();
        if (parentName != null) {
            nameElements.add(parentName);
        }
        if (childNode != null && childNode.isConfiguration()) {
            final String childNodeName = childNode.getQName().getLocalName();
            nameElements.add(nodeName);
            nameElements.add(childNodeName + discriminator);
            final String childDefName = String.join("_", nameElements);
            requestBody = createRequestBodyParameter(childDefName, childNodeName, childNode instanceof ListSchemaNode,
                summary, childNodeName);
        } else {
            nameElements.add(nodeName + discriminator);
            final String defName = String.join("_", nameElements);
            requestBody = createPostDataRequestBodyParameter(defName, nodeName);
        }
        final Map<String, ResponseObject> responses = Map.of(String.valueOf(Response.Status.CREATED.getStatusCode()),
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
        final boolean isList = node instanceof ListSchemaNode;
        final ResponseObject response = createResponse(defName, nodeName, isList,
            String.valueOf(OK.getStatusCode()), summary);
        final Map<String, ResponseObject> responses = Map.of(String.valueOf(OK.getStatusCode()),
            response);

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
        final RequestBody requestBody = createRequestBodyParameter(defName, fullName, isList, summary, nodeName);

        final var created = new SimpleEntry<>(String.valueOf(Response.Status.CREATED.getStatusCode()),
            buildResponse(Response.Status.CREATED.getReasonPhrase()));
        final var updated = new SimpleEntry<>(String.valueOf(NO_CONTENT.getStatusCode()),
            buildResponse("Updated"));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(Map.of(created.getKey(), created.getValue(), updated.getKey(), updated.getValue()))
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

        final SimpleEntry<String, ResponseObject> created = new SimpleEntry<>(String.valueOf(OK.getStatusCode()),
            buildResponse(OK.getReasonPhrase()));
        final SimpleEntry<String, ResponseObject> updated = new SimpleEntry<>(String.valueOf(NO_CONTENT
            .getStatusCode()), buildResponse("Updated"));

        return new Operation.Builder()
            .tags(tags)
            .parameters(parameters)
            .requestBody(requestBody)
            .responses(Map.of(created.getKey(), created.getValue(), updated.getKey(), updated.getValue()))
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
        final Map<String, ResponseObject> responses = Map.of(String.valueOf(NO_CONTENT.getStatusCode()),
            buildResponse("Deleted"));

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
            final Map<String, Property> properties = Map.of(INPUT_KEY, new Property.Builder().type(OBJECT).build());
            final Schema jsonSchema = new Schema.Builder()
                .type(OBJECT)
                .properties(properties)
                .build();
            final MediaTypeObject jsonTypeValue = new MediaTypeObject.Builder()
                .schema(jsonSchema)
                .build();
            final SimpleEntry<String, MediaTypeObject> jsonEntry = new SimpleEntry<>(MediaType.APPLICATION_JSON,
                jsonTypeValue);

            final Xml xml = new Xml(INPUT_KEY, input.getQName().getNamespace().toString(), null);
            final Schema xmlSchema = new Schema.Builder()
                .type(OBJECT)
                .xml(xml)
                .build();
            final MediaTypeObject xmlTypeValue = new MediaTypeObject.Builder()
                .schema(xmlSchema)
                .build();
            final SimpleEntry<String, MediaTypeObject> xmlEntry = new SimpleEntry<>(MediaType.APPLICATION_XML,
                xmlTypeValue);

            requestBody = new RequestBody.Builder()
                .content(Map.of(jsonEntry.getKey(), jsonEntry.getValue(), xmlEntry.getKey(), xmlEntry.getValue()))
                .description(inputName)
                .build();
        }
        final String description = String.format("RPC %s success", operationName);

        final Map<String, ResponseObject> responses;
        if (!output.getChildNodes().isEmpty()) {
            final String defName = parentName + "_" + operationName + OUTPUT_SUFFIX
                + definitionNames.getDiscriminator(output);
            final Schema schema = new Schema.Builder()
                .ref(COMPONENTS_PREFIX + defName)
                .build();
            responses = Map.of(String.valueOf(OK.getStatusCode()), buildResponse(description, schema));
        } else {
            responses = Map.of(String.valueOf(NO_CONTENT.getStatusCode()), buildResponse(description));
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
        final MediaTypeObject value = buildMediaTypeObject(defName);
        final Map<String, MediaTypeObject> content = Map.of(MediaType.APPLICATION_JSON, value,
            MediaType.APPLICATION_XML, value);
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
        final Map<String, Property> properties;
        if (isList) {
            properties = Map.of(name, new Property.Builder()
                .type("array")
                .items(new Property.Builder()
                    .type(OBJECT)
                    .ref(COMPONENTS_PREFIX + defName)
                    .build())
                .build());
        } else {
            properties = Map.of(name, new Property.Builder()
                .type(OBJECT)
                .ref(COMPONENTS_PREFIX + defName)
                .build());
        }
        final MediaTypeObject jsonSchema = new MediaTypeObject.Builder()
            .schema(new Schema.Builder()
                .properties(properties)
                .build())
            .build();
        final Map<String, MediaTypeObject> content;
        if (summary != null && summary.contains(HttpMethod.PATCH)) {
            content = Map.of("application/yang-data+json", jsonSchema,
                "application/yang-data+xml", buildMediaTypeObject(defName));
        } else {
            content = Map.of(MediaType.APPLICATION_JSON, jsonSchema,
                MediaType.APPLICATION_XML, buildMediaTypeObject(defName));
        }
        return content;
    }

    private static Schema buildRefSchema(final String defName) {
        return new Schema.Builder()
            .ref(COMPONENTS_PREFIX + defName)
            .build();
    }

    private static MediaTypeObject buildMediaTypeObject(final String defName) {
        return new MediaTypeObject.Builder()
            .schema(buildRefSchema(defName))
            .build();
    }

    private static ResponseObject buildResponse(final String description) {
        return new ResponseObject.Builder()
            .description(description)
            .build();
    }

    private static ResponseObject buildResponse(final String description, final Schema schema) {
        final MediaTypeObject body = new MediaTypeObject.Builder()
            .schema(schema)
            .build();
        final Map<String, MediaTypeObject> content = new HashMap<>();
        for (final String mimeType : MIME_TYPES) {
            content.put(mimeType, body);
        }
        return new ResponseObject.Builder()
            .content(content)
            .description(description)
            .build();
    }
}
