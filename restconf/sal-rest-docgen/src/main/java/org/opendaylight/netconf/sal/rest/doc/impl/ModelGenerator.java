/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.util.RestDocgenUtil.resolveNodesName;

import com.google.common.base.Optional;
import com.mifmif.common.regex.Generex;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.concurrent.NotThreadSafe;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder;
import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.Post;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RevisionAwareXPath;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition.Bit;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.DecimalTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EmptyTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition.EnumPair;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IntegerTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LengthConstraint;
import org.opendaylight.yangtools.yang.model.api.type.PatternConstraint;
import org.opendaylight.yangtools.yang.model.api.type.StringTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnsignedIntegerTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.RevisionAwareXPathImpl;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates JSON Schema for data defined in YANG.
 */
@NotThreadSafe
public class ModelGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGenerator.class);

    private static final Pattern STRIP_PATTERN = Pattern.compile("\\[[^\\[\\]]*\\]");
    private static final String BASE_64 = "base64";
    private static final String BINARY_ENCODING_KEY = "binaryEncoding";
    private static final String MEDIA_KEY = "media";
    private static final String UNIQUE_ITEMS_KEY = "uniqueItems";
    private static final String MAX_ITEMS = "maxItems";
    private static final String MIN_ITEMS = "minItems";
    private static final String SCHEMA_URL = "http://json-schema.org/draft-04/schema";
    private static final String SCHEMA_KEY = "$schema";
    private static final String MAX_LENGTH_KEY = "maxLength";
    private static final String MIN_LENGTH_KEY = "minLength";
    private static final String REQUIRED_KEY = "required";
    private static final String REF_KEY = "$ref";
    private static final String ITEMS_KEY = "items";
    private static final String TYPE_KEY = "type";
    private static final String PROPERTIES_KEY = "properties";
    private static final String DESCRIPTION_KEY = "description";
    private static final String OBJECT_TYPE = "object";
    private static final String ARRAY_TYPE = "array";
    private static final String ENUM = "enum";
    private static final String ID_KEY = "id";
    private static final String SUB_TYPES_KEY = "subTypes";
    private static final String UNIQUE_EMPTY_IDENTIFIER = "unique_empty_identifier";

    private Module topLevelModule;

    public ModelGenerator() {
    }

    /**
     * Creates Json models from provided module according to swagger spec
     *
     * @param module        - Yang module to be converted
     * @param schemaContext - SchemaContext of all Yang files used by Api Doc
     * @return JSONObject containing data used for creating examples and models in Api Doc
     * @throws IOException
     * @throws JSONException
     */
    public JSONObject convertToJsonSchema(final Module module, final SchemaContext schemaContext) throws IOException, JSONException {
        final JSONObject models = new JSONObject();
        models.put(UNIQUE_EMPTY_IDENTIFIER, new JSONObject());
        topLevelModule = module;
        processModules(module, models, schemaContext);
        processContainersAndLists(module, models, schemaContext);
        processRPCs(module, models, schemaContext);
        processIdentities(module, models);
        return models;
    }

    private void processModules(final Module module, final JSONObject models, final SchemaContext schemaContext) throws JSONException {
        createConcreteModelForPost(models, module.getName() + BaseYangSwaggerGenerator.MODULE_NAME_SUFFIX,
                createPropertiesForPost(module, schemaContext, module.getName()));
    }

    private void processContainersAndLists(final Module module, final JSONObject models, final SchemaContext schemaContext)
            throws IOException, JSONException {
        final String moduleName = module.getName();

        for (final DataSchemaNode childNode : module.getChildNodes()) {
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, models, true, schemaContext);
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, models, false, schemaContext);
            }
        }
    }

    /**
     * Process the RPCs for a Module Spits out a file each of the name <rpcName>-input.json and <rpcName>-output.json
     * for each RPC that contains input & output elements
     *
     * @param module
     * @throws JSONException
     * @throws IOException
     */
    private void processRPCs(final Module module, final JSONObject models, final SchemaContext schemaContext) throws JSONException,
            IOException {
        final Set<RpcDefinition> rpcs = module.getRpcs();
        final String moduleName = module.getName();
        for (final RpcDefinition rpc : rpcs) {
            final ContainerSchemaNode input = rpc.getInput();
            if (input != null) {
                final JSONObject properties = processChildren(input.getChildNodes(), moduleName, models, true, schemaContext);

                final String filename = "(" + rpc.getQName().getLocalName() + ")input";
                final JSONObject childSchema = getSchemaTemplate();
                childSchema.put(TYPE_KEY, OBJECT_TYPE);
                childSchema.put(PROPERTIES_KEY, properties);
                childSchema.put(ID_KEY, filename);
                models.put(filename, childSchema);

                processTopData(filename, models, input);
            }

            final ContainerSchemaNode output = rpc.getOutput();
            if (output != null) {
                final JSONObject properties = processChildren(output.getChildNodes(), moduleName, models, true, schemaContext);
                final String filename = "(" + rpc.getQName().getLocalName() + ")output";
                final JSONObject childSchema = getSchemaTemplate();
                childSchema.put(TYPE_KEY, OBJECT_TYPE);
                childSchema.put(PROPERTIES_KEY, properties);
                childSchema.put(ID_KEY, filename);
                models.put(filename, childSchema);

                processTopData(filename, models, output);
            }
        }
    }

    private JSONObject processTopData(final String filename, final JSONObject models, final SchemaNode schemaNode) {
        final JSONObject items = new JSONObject();

        items.put(REF_KEY, filename);
        final JSONObject dataNodeProperties = new JSONObject();
        dataNodeProperties.put(TYPE_KEY, schemaNode instanceof ListSchemaNode ? ARRAY_TYPE : OBJECT_TYPE);
        dataNodeProperties.put(ITEMS_KEY, items);

        dataNodeProperties.putOpt(DESCRIPTION_KEY, schemaNode.getDescription());
        final JSONObject properties = new JSONObject();
        properties.put(topLevelModule.getName() + ":" + schemaNode.getQName().getLocalName(), dataNodeProperties);
        final JSONObject finalChildSchema = getSchemaTemplate();
        finalChildSchema.put(TYPE_KEY, OBJECT_TYPE);
        finalChildSchema.put(PROPERTIES_KEY, properties);
        finalChildSchema.put(ID_KEY, filename + OperationBuilder.TOP);
        models.put(filename + OperationBuilder.TOP, finalChildSchema);

        return dataNodeProperties;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     *
     * @param module The module from which the identity stmt will be processed
     * @param models The JSONObject in which the parsed identity will be put as a 'model' obj
     */
    private static void processIdentities(final Module module, final JSONObject models) throws JSONException {

        final String moduleName = module.getName();
        final Set<IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final JSONObject identityObj = new JSONObject();
            final String identityName = idNode.getQName().getLocalName();
            LOG.debug("Processing Identity: {}", identityName);

            identityObj.put(ID_KEY, identityName);
            identityObj.put(DESCRIPTION_KEY, idNode.getDescription());

            final JSONObject props = new JSONObject();
            final IdentitySchemaNode baseId = idNode.getBaseIdentity();

            if (baseId == null) {
                /**
                 * This is a base identity. So lets see if it has sub types. If it does, then add them to the model
                 * definition.
                 */
                final Set<IdentitySchemaNode> derivedIds = idNode.getDerivedIdentities();

                if (derivedIds != null) {
                    final JSONArray subTypes = new JSONArray();
                    for (final IdentitySchemaNode derivedId : derivedIds) {
                        subTypes.put(derivedId.getQName().getLocalName());
                    }
                    identityObj.put(SUB_TYPES_KEY, subTypes);

                }
            } else {
                /**
                 * This is a derived entity. Add it's base type & move on.
                 */
                props.put(TYPE_KEY, baseId.getQName().getLocalName());
            }

            // Add the properties. For a base type, this will be an empty object as required by the Swagger spec.
            identityObj.put(PROPERTIES_KEY, props);
            models.put(identityName, identityObj);
        }
    }

    private JSONObject processDataNodeContainer(final DataNodeContainer dataNode, final String parentName, final JSONObject models,
                                                final boolean isConfig, final SchemaContext schemaContext) throws JSONException, IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Iterable<DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final String localName = ((SchemaNode) dataNode).getQName().getLocalName();
            final JSONObject properties = processChildren(containerChildren, parentName + "/" + localName, models, isConfig, schemaContext);
            final String nodeName = parentName + (isConfig ? OperationBuilder.CONFIG : OperationBuilder.OPERATIONAL)
                    + localName;

            final JSONObject childSchema = getSchemaTemplate();
            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.put(PROPERTIES_KEY, properties);

            childSchema.put(ID_KEY, nodeName);
            models.put(nodeName, childSchema);

            if (isConfig) {
                createConcreteModelForPost(models, localName,
                        createPropertiesForPost(dataNode, schemaContext, parentName + "/" + localName));
            }

            return processTopData(nodeName, models, (SchemaNode) dataNode);
        }
        return null;
    }

    private static void createConcreteModelForPost(final JSONObject models, final String localName,
                                                   final JSONObject properties) throws JSONException {
        final String nodePostName = OperationBuilder.CONFIG + localName + Post.METHOD_NAME;
        final JSONObject postSchema = getSchemaTemplate();
        postSchema.put(TYPE_KEY, OBJECT_TYPE);
        postSchema.put(ID_KEY, nodePostName);
        postSchema.put(PROPERTIES_KEY, properties);
        models.put(nodePostName, postSchema);
    }

    private JSONObject createPropertiesForPost(final DataNodeContainer dataNodeContainer, final SchemaContext schemaContext, final String parentName)
            throws JSONException {
        final JSONObject properties = new JSONObject();
        for (final DataSchemaNode childNode : dataNodeContainer.getChildNodes()) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final JSONObject items = new JSONObject();
                items.put(REF_KEY, parentName + "(config)" + childNode.getQName().getLocalName());
                final JSONObject property = new JSONObject();
                property.put(TYPE_KEY, childNode instanceof ListSchemaNode ? ARRAY_TYPE : OBJECT_TYPE);
                property.put(ITEMS_KEY, items);
                properties.put(childNode.getQName().getLocalName(), property);
            } else if (childNode instanceof LeafSchemaNode) {
                final JSONObject property = processLeafNode((LeafSchemaNode) childNode, schemaContext);
                properties.put(childNode.getQName().getLocalName(), property);
            }
        }
        return properties;
    }

    /**
     * Processes the nodes.
     */
    private JSONObject processChildren(final Iterable<DataSchemaNode> nodes, final String parentName, final JSONObject models,
                                       final boolean isConfig, final SchemaContext schemaContext)
            throws JSONException, IOException {
        final JSONObject properties = new JSONObject();
        for (final DataSchemaNode node : nodes) {
            if (node.isConfiguration() == isConfig) {
                final String name = resolveNodesName(node, topLevelModule, schemaContext);
                final JSONObject property;
                if (node instanceof LeafSchemaNode) {
                    property = processLeafNode((LeafSchemaNode) node, schemaContext);

                } else if (node instanceof ListSchemaNode) {
                    property = processDataNodeContainer((ListSchemaNode) node, parentName, models, isConfig,
                            schemaContext);

                } else if (node instanceof LeafListSchemaNode) {
                    property = processLeafListNode((LeafListSchemaNode) node, schemaContext);

                } else if (node instanceof ChoiceSchemaNode) {
                    if (((ChoiceSchemaNode) node).getCases().iterator().hasNext()) {
                        processChoiceNode(((ChoiceSchemaNode) node).getCases().iterator().next().getChildNodes(),
                                parentName, models, schemaContext, isConfig, properties);
                    }
                    continue;

                } else if (node instanceof AnyXmlSchemaNode) {
                    property = processAnyXMLNode((AnyXmlSchemaNode) node);

                } else if (node instanceof ContainerSchemaNode) {
                    property = processDataNodeContainer((ContainerSchemaNode) node, parentName, models, isConfig,
                            schemaContext);

                } else {
                    throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
                }
                property.putOpt(DESCRIPTION_KEY, node.getDescription());
                properties.put(topLevelModule.getName() + ":" + name, property);
            }
        }
        return properties;
    }

    private JSONObject processLeafListNode(final LeafListSchemaNode listNode, final SchemaContext schemaContext) throws JSONException {
        final JSONObject props = new JSONObject();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final JSONObject itemsVal = new JSONObject();
        final ConstraintDefinition constraints = listNode.getConstraints();
        final Optional<Integer> maxOptional = Optional.fromNullable(constraints.getMaxElements());
        if (maxOptional.or(2) >= 2) {
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
        } else {
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
        }
        props.put(ITEMS_KEY, itemsVal);


        processConstraints(constraints, props);

        return props;
    }

    private void processChoiceNode(final Iterable<DataSchemaNode> nodes, final String moduleName, final JSONObject models,
                                   final SchemaContext schemaContext, final boolean isConfig, final JSONObject properties)
            throws JSONException, IOException {
        for (final DataSchemaNode node : nodes) {
            final String name = resolveNodesName(node, topLevelModule, schemaContext);
            final JSONObject property;

            if (node instanceof LeafSchemaNode) {
                property = processLeafNode((LeafSchemaNode) node, schemaContext);

            } else if (node instanceof ListSchemaNode) {
                property = processDataNodeContainer((ListSchemaNode) node, moduleName, models, isConfig,
                        schemaContext);

            } else if (node instanceof LeafListSchemaNode) {
                property = processLeafListNode((LeafListSchemaNode) node, schemaContext);

            } else if (node instanceof ChoiceSchemaNode) {
                if (((ChoiceSchemaNode) node).getCases().iterator().hasNext())
                    processChoiceNode(((ChoiceSchemaNode) node).getCases().iterator().next().getChildNodes(),
                            moduleName, models, schemaContext, isConfig, properties);
                continue;

            } else if (node instanceof AnyXmlSchemaNode) {
                property = processAnyXMLNode((AnyXmlSchemaNode) node);

            } else if (node instanceof ContainerSchemaNode) {
                property = processDataNodeContainer((ContainerSchemaNode) node, moduleName, models, isConfig,
                        schemaContext);

            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }

            property.putOpt(DESCRIPTION_KEY, node.getDescription());
            properties.put(name, property);
        }
    }

    private static void processConstraints(final ConstraintDefinition constraints, final JSONObject props) throws JSONException {
        final boolean isMandatory = constraints.isMandatory();
        props.put(REQUIRED_KEY, isMandatory);

        final Integer minElements = constraints.getMinElements();
        final Integer maxElements = constraints.getMaxElements();
        if (minElements != null) {
            props.put(MIN_ITEMS, minElements);
        }
        if (maxElements != null) {
            props.put(MAX_ITEMS, maxElements);
        }
    }

    private JSONObject processLeafNode(final LeafSchemaNode leafNode, final SchemaContext schemaContext) throws JSONException {
        final JSONObject property = new JSONObject();

        final String leafDescription = leafNode.getDescription();
        property.put(DESCRIPTION_KEY, leafDescription);
        processConstraints(leafNode.getConstraints(), property);
        processTypeDef(leafNode.getType(), leafNode, property, schemaContext);

        return property;
    }

    private static JSONObject processAnyXMLNode(final AnyXmlSchemaNode leafNode) throws JSONException {
        final JSONObject property = new JSONObject();

        final String leafDescription = leafNode.getDescription();
        property.put(DESCRIPTION_KEY, leafDescription);

        processConstraints(leafNode.getConstraints(), property);
        final String localName = leafNode.getQName().getLocalName();
        property.put(TYPE_KEY, "example of anyxml " + localName);

        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final JSONObject property, final SchemaContext schemaContext) throws JSONException {
        final String jsonType;
        if (leafTypeDef.getDefaultValue() == null) {
            if (leafTypeDef instanceof BinaryTypeDefinition) {
                jsonType = processBinaryType(property);

            } else if (leafTypeDef instanceof BitsTypeDefinition) {
                jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, property);

            } else if (leafTypeDef instanceof EnumTypeDefinition) {
                jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, property);

            } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
                final String name = topLevelModule.getName();
                jsonType = name + ":" + ((IdentityrefTypeDefinition) leafTypeDef).getIdentity().getQName().getLocalName();

            } else if (leafTypeDef instanceof StringTypeDefinition) {
                jsonType = processStringType(leafTypeDef, property, node.getQName().getLocalName());

            } else if (leafTypeDef instanceof UnionTypeDefinition) {
                jsonType = processUnionType((UnionTypeDefinition) leafTypeDef, property, schemaContext, node);

            } else if (leafTypeDef instanceof EmptyTypeDefinition) {
                jsonType = UNIQUE_EMPTY_IDENTIFIER;

            } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
                return processLeafRef(node, property, schemaContext, leafTypeDef);

            } else if (leafTypeDef instanceof BooleanTypeDefinition) {
                jsonType = "true";

            } else if (leafTypeDef instanceof DecimalTypeDefinition) {
                jsonType = String.valueOf(((DecimalTypeDefinition) leafTypeDef).getRangeConstraints()
                        .iterator().next().getMin());

            } else if (leafTypeDef instanceof IntegerTypeDefinition) {
                jsonType = String.valueOf(((IntegerTypeDefinition) leafTypeDef).getRangeConstraints()
                        .iterator().next().getMin());

            } else if (leafTypeDef instanceof UnsignedIntegerTypeDefinition) {
                jsonType = String.valueOf(((UnsignedIntegerTypeDefinition) leafTypeDef).getRangeConstraints()
                        .iterator().next().getMin());

            } else {
                jsonType = OBJECT_TYPE;

            }
        } else {
            jsonType = String.valueOf(leafTypeDef.getDefaultValue());
        }
        property.putOpt(TYPE_KEY, jsonType);
        return jsonType;
    }

    private String processLeafRef(final DataSchemaNode node, final JSONObject property, final SchemaContext schemaContext,
                                  final TypeDefinition<?> leafTypeDef) {
        RevisionAwareXPath xPath = ((LeafrefTypeDefinition) leafTypeDef).getPathStatement();
        final URI namespace = leafTypeDef.getQName().getNamespace();
        final Date revision = leafTypeDef.getQName().getRevision();
        final Module module = schemaContext.findModuleByNamespaceAndRevision(namespace, revision);
        final SchemaNode schemaNode;

        final String xPathString = STRIP_PATTERN.matcher(xPath.toString()).replaceAll("");
        xPath = new RevisionAwareXPathImpl(xPathString, xPath.isAbsolute());

        if (xPath.isAbsolute()) {
            schemaNode = SchemaContextUtil.findDataSchemaNode(schemaContext, module, xPath);
        } else {
            schemaNode = SchemaContextUtil.findDataSchemaNodeForRelativeXPath(schemaContext, module, node, xPath);
        }

        return processTypeDef(((TypedSchemaNode) schemaNode).getType(), (DataSchemaNode) schemaNode, property, schemaContext);
    }

    private static String processBinaryType(final JSONObject property) throws JSONException {
        final JSONObject media = new JSONObject();
        media.put(BINARY_ENCODING_KEY, BASE_64);
        property.put(MEDIA_KEY, media);
        return "bin1 bin2";
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType, final JSONObject property) throws JSONException {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        final List<String> enumNames = new ArrayList<>();
        for (final EnumPair enumPair : enumPairs) {
            enumNames.add(enumPair.getName());
        }

        property.putOpt(ENUM, new JSONArray(enumNames));
        return enumLeafType.getValues().iterator().next().getName();
    }

    private static String processBitsType(final BitsTypeDefinition bitsType, final JSONObject property) throws JSONException {
        property.put(MIN_ITEMS, 0);
        property.put(UNIQUE_ITEMS_KEY, true);
        final List<String> enumNames = new ArrayList<>();
        final List<Bit> bits = bitsType.getBits();
        for (final Bit bit : bits) {
            enumNames.add(bit.getName());
        }
        property.put(ENUM, new JSONArray(enumNames));

        return enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1);
    }

    private static String processStringType(final TypeDefinition<?> stringType, final JSONObject property, final String nodeName)
            throws JSONException {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        List<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraints();
        while (lengthConstraints.isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
            lengthConstraints = type.getLengthConstraints();
        }

        // FIXME: json-schema is not expressive enough to capture min/max laternatives. We should find the true minimum
        //        and true maximum implied by the constraints and use that.
        for (final LengthConstraint lengthConstraint : lengthConstraints) {
            final Number min = lengthConstraint.getMin();
            final Number max = lengthConstraint.getMax();
            property.putOpt(MIN_LENGTH_KEY, min);
            property.putOpt(MAX_LENGTH_KEY, max);
        }
        if (type.getPatternConstraints().iterator().hasNext()) {
            final PatternConstraint pattern = type.getPatternConstraints().iterator().next();
            String regex = pattern.getRegularExpression();
            regex = regex.substring(1, regex.length() - 1);
            final Generex generex = new Generex(regex);
            return generex.random();
        } else {
            return "Some " + nodeName;
        }
    }

    private String processUnionType(final UnionTypeDefinition unionType, final JSONObject property,
                                    final SchemaContext schemaContext, final DataSchemaNode node)
            throws JSONException {
        final List<String> unionNames = new ArrayList<>();
        for (final TypeDefinition<?> typeDef : unionType.getTypes()) {
            unionNames.add(processTypeDef(typeDef, node, property, schemaContext));
        }
        property.put(ENUM, new JSONArray(unionNames));
        return unionNames.iterator().next();
    }

    /**
     * Helper method to generate a pre-filled JSON schema object.
     */
    private static JSONObject getSchemaTemplate() throws JSONException {
        final JSONObject schemaJSON = new JSONObject();
        schemaJSON.put(SCHEMA_KEY, SCHEMA_URL);

        return schemaJSON;
    }

}