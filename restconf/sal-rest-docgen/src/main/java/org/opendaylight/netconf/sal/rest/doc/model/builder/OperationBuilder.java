/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.rest.doc.model.builder;

import static org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator.INPUT;
import static org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator.INPUT_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.impl.DefinitionGenerator.OUTPUT_SUFFIX;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionNames;
import org.opendaylight.netconf.sal.rest.doc.util.JsonUtil;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.InputSchemaNode;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.OutputSchemaNode;

public final class OperationBuilder {
    public static final String BODY = "body";
    public static final String CONFIG = "_config";
    public static final String CONFIG_QUERY_PARAM = "config";
    public static final String CONSUMES_KEY = "consumes";
    public static final String CONTENT_KEY = "content";
    public static final String COMPONENTS_PREFIX = "#/components/schemas/";
    public static final String DEFINITIONS_PREFIX = "#/definitions/";
    public static final String DESCRIPTION_KEY = "description";
    public static final String IN_KEY = "in";
    public static final String INPUT_KEY = "input";
    public static final String NAME_KEY = "name";
    public static final String NONCONFIG_QUERY_PARAM = "nonconfig";
    public static final String PARAMETERS_KEY = "parameters";
    public static final String POST_SUFFIX = "_post";
    public static final String PROPERTIES_KEY = "properties";
    public static final String REF_KEY = "$ref";
    public static final String REQUEST_BODY_KEY = "requestBody";
    public static final String RESPONSES_KEY = "responses";
    public static final String SCHEMA_KEY = "schema";
    public static final String SUMMARY_KEY = "summary";
    public static final String SUMMARY_SEPARATOR = " - ";
    public static final String TAGS_KEY = "tags";
    public static final String TOP = "_TOP";
    public static final String XML_KEY = "xml";
    public static final String XML_SUFFIX = "_xml";
    private static final String CONTENT = "content";
    private static final ArrayNode CONSUMES_PUT_POST;
    private static final String ENUM_KEY = "enum";
    private static final List<String> MIME_TYPES = ImmutableList.of(MediaType.APPLICATION_XML,
            MediaType.APPLICATION_JSON);
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

    public static ObjectNode buildPost(final String parentName, final String nodeName, final String discriminator,
            final String moduleName, final Optional<String> deviceName, final String description,
            final ArrayNode pathParams, final OAversion oaversion) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, description);
        value.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.POST, moduleName, deviceName, nodeName));
        value.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        final ObjectNode ref = JsonNodeFactory.instance.objectNode();
        final String cleanDefName = parentName + CONFIG + "_" + nodeName + POST_SUFFIX;
        final String defName = cleanDefName + discriminator;
        final String xmlDefName = cleanDefName + XML_SUFFIX + discriminator;
        ref.put(REF_KEY, getAppropriateModelPrefix(oaversion) + defName);
        insertRequestBodyParameter(parameters, value, defName, xmlDefName, nodeName + CONFIG, oaversion);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty(), oaversion));

        value.set(RESPONSES_KEY, responses);
        setConsumesIfNeeded(value, oaversion);
        return value;
    }

    public static ObjectNode buildGet(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams, final String defName,
            final boolean isConfig, final OAversion oaversion) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, node.getDescription().orElse(""));
        value.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.GET, moduleName, deviceName,
                node.getQName().getLocalName()));
        value.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonUtil.copy(pathParams);

        addQueryParameters(parameters, isConfig, oaversion);

        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, getAppropriateModelPrefix(oaversion) + defName);
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), Optional.of(schema), oaversion));

        value.set(RESPONSES_KEY, responses);
        return value;
    }

    private static void addQueryParameters(final ArrayNode parameters, final boolean isConfig,
            final OAversion oaversion) {
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

        final ObjectNode typeParent = getTypeParentNode(contentParam, oaversion);
        typeParent.put(TYPE_KEY, STRING);
        typeParent.set(ENUM_KEY, cases);

        parameters.add(contentParam);
    }

    public static ObjectNode buildPut(final String parentName, final String nodeName, final String discriminator,
            final String moduleName, final Optional<String> deviceName, final String description,
            final ArrayNode pathParams, final OAversion oaversion) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, description);
        value.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.PUT, moduleName, deviceName, nodeName));
        value.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        insertRequestBodyParameter(parameters, value, defName, xmlDefName, nodeName + CONFIG, oaversion);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.CREATED.getStatusCode()),
                buildResponse(Response.Status.CREATED.getReasonPhrase(), Optional.empty(), oaversion));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty(), oaversion));

        value.set(RESPONSES_KEY, responses);
        setConsumesIfNeeded(value, oaversion);
        return value;
    }

    public static ObjectNode buildPatch(final String parentName, final String nodeName, final String moduleName,
            final Optional<String> deviceName, final String description, final ArrayNode pathParams,
            final OAversion oaversion) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(DESCRIPTION_KEY, description);
        value.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.PATCH, moduleName, deviceName, nodeName));
        value.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        final String defName = parentName + CONFIG + "_" + nodeName + TOP;
        final String xmlDefName = parentName + CONFIG + "_" + nodeName;
        insertRequestBodyParameter(parameters, value, defName, xmlDefName, nodeName + CONFIG, oaversion);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()),
                buildResponse(Response.Status.OK.getReasonPhrase(), Optional.empty(), oaversion));
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Updated", Optional.empty(), oaversion));

        value.set(RESPONSES_KEY, responses);
        setConsumesIfNeeded(value, oaversion);
        return value;
    }

    public static ObjectNode buildDelete(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams, final OAversion oaversion) {
        final ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.DELETE, moduleName, deviceName,
                node.getQName().getLocalName()));
        value.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        value.put(DESCRIPTION_KEY, node.getDescription().orElse(""));
        final ArrayNode parameters = JsonUtil.copy(pathParams);
        value.set(PARAMETERS_KEY, parameters);

        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()),
                buildResponse("Deleted", Optional.empty(), oaversion));

        value.set(RESPONSES_KEY, responses);
        return value;
    }

    public static ObjectNode buildPostOperation(final OperationDefinition operDef, final String moduleName,
            final Optional<String> deviceName, final String parentName, final DefinitionNames definitionNames,
            final OAversion oaversion, final ArrayNode parentPathParameters) {
        final ObjectNode postOperation = JsonNodeFactory.instance.objectNode();
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode().addAll(parentPathParameters);
        final String operName = operDef.getQName().getLocalName();
        final String inputName = operName + INPUT_SUFFIX;

        final InputSchemaNode input = operDef.getInput();
        final OutputSchemaNode output = operDef.getOutput();
        if (!input.getChildNodes().isEmpty()) {
            final String discriminator = definitionNames.getDiscriminator(input);
            final String clearDefName = parentName + "_" + operName + INPUT_SUFFIX;
            final String defName = clearDefName + discriminator;
            final String defTopName = clearDefName + TOP + discriminator;
            insertRequestBodyParameter(parameters, postOperation, defTopName, defName, inputName, oaversion);
        } else {
            final ObjectNode payload = JsonNodeFactory.instance.objectNode();
            final ObjectNode jsonSchema = JsonNodeFactory.instance.objectNode();
            final ObjectNode properties = JsonNodeFactory.instance.objectNode();
            final ObjectNode inputSchema = JsonNodeFactory.instance.objectNode();
            inputSchema.put(TYPE_KEY, OBJECT);
            properties.set(INPUT_KEY, inputSchema);
            jsonSchema.put(TYPE_KEY, OBJECT);
            jsonSchema.set(PROPERTIES_KEY, properties);
            if (oaversion.equals(OAversion.V3_0)) {
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
                postOperation.set(REQUEST_BODY_KEY, payload);
            } else {
                payload.put(IN_KEY, BODY);
                payload.put(NAME_KEY, inputName);
                payload.set(SCHEMA_KEY, jsonSchema);
                parameters.add(payload);
            }

        }
        setConsumesIfNeeded(postOperation, oaversion);
        postOperation.set(PARAMETERS_KEY, parameters);
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        final String description = String.format("RPC %s success", operName);

        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            final String defName = parentName + "_" + operName + OUTPUT_SUFFIX + TOP
                    + definitionNames.getDiscriminator(output);
            schema.put(REF_KEY, getAppropriateModelPrefix(oaversion) + defName);
            responses.set(String.valueOf(Response.Status.OK.getStatusCode()), buildResponse(description,
                    Optional.of(schema), oaversion));
        } else {
            responses.set(String.valueOf(Response.Status.NO_CONTENT.getStatusCode()), buildResponse(description,
                    Optional.empty(), oaversion));
        }
        postOperation.set(RESPONSES_KEY, responses);
        postOperation.put(DESCRIPTION_KEY, operDef.getDescription().orElse(""));
        postOperation.put(SUMMARY_KEY, buildSummaryValue(HttpMethod.POST, moduleName, deviceName, operName));
        postOperation.set(TAGS_KEY, buildTagsValue(deviceName, moduleName));
        return postOperation;
    }

    private static void insertRequestBodyParameter(final ArrayNode parameters, final ObjectNode operation,
            final String defName, final String xmlDefName, final String name, final OAversion oaversion) {
        final ObjectNode payload = JsonNodeFactory.instance.objectNode();
        if (oaversion.equals(OAversion.V3_0)) {
            final ObjectNode content = JsonNodeFactory.instance.objectNode();
            final JsonNode node = operation.get(SUMMARY_KEY);
            if (node != null && node.asText().contains(HttpMethod.PATCH)) {
                content.set("application/yang-data+json", buildMimeTypeValue(defName));
                content.set("application/yang-data+xml", buildMimeTypeValue(xmlDefName));
            } else {
                content.set(MediaType.APPLICATION_JSON, buildMimeTypeValue(defName));
                content.set(MediaType.APPLICATION_XML, buildMimeTypeValue(xmlDefName));
            }
            payload.set(CONTENT_KEY, content);
            payload.put(DESCRIPTION_KEY, name);
            operation.set(REQUEST_BODY_KEY, payload);
        } else {
            payload.put(IN_KEY, BODY);
            payload.put(NAME_KEY, name);
            payload.set(SCHEMA_KEY, buildRefSchema(defName, OAversion.V2_0));
            parameters.add(payload);
        }
    }

    private static ObjectNode buildRefSchema(final String defName, final OAversion oaversion) {
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put(REF_KEY, getAppropriateModelPrefix(oaversion) + defName);
        return schema;
    }

    private static ObjectNode buildMimeTypeValue(final String defName) {
        final ObjectNode mimeTypeValue = JsonNodeFactory.instance.objectNode();
        mimeTypeValue.set(SCHEMA_KEY, buildRefSchema(defName, OAversion.V3_0));
        return mimeTypeValue;
    }

    public static ObjectNode buildResponse(final String description, final Optional<ObjectNode> schema,
            final OAversion oaversion) {
        final ObjectNode response = JsonNodeFactory.instance.objectNode();

        if (schema.isPresent()) {
            final ObjectNode schemaValue = schema.orElseThrow();
            if (oaversion.equals(OAversion.V3_0)) {
                final ObjectNode content = JsonNodeFactory.instance.objectNode();
                final ObjectNode body = JsonNodeFactory.instance.objectNode();
                for (final String mimeType : MIME_TYPES) {
                    content.set(mimeType, body);
                }
                body.set(SCHEMA_KEY, schemaValue);
                response.set(CONTENT_KEY, content);
            } else {
                response.set(SCHEMA_KEY, schemaValue);
            }
        }
        response.put(DESCRIPTION_KEY, description);
        return response;
    }

    private static void setConsumesIfNeeded(final ObjectNode operation, final OAversion oaversion) {
        if (oaversion.equals(OAversion.V2_0)) {
            operation.set(CONSUMES_KEY, CONSUMES_PUT_POST);
        }
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

    public static String getAppropriateModelPrefix(final OAversion oaversion) {
        if (oaversion.equals(OAversion.V3_0)) {
            return COMPONENTS_PREFIX;
        }
        return DEFINITIONS_PREFIX;
    }

    public static ObjectNode getTypeParentNode(final ObjectNode parameter, final OAversion oaversion) {
        if (oaversion.equals(OAversion.V3_0)) {
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            parameter.set(SCHEMA_KEY, schema);
            return schema;
        }
        return parameter;
    }
}
