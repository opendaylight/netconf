/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.util.RestDocgenUtil.resolveNodesName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mifmif.common.regex.Generex;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.concurrent.NotThreadSafe;
import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder;
import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.Post;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraint;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MandatoryAware;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RevisionAwareXPath;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition.Bit;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EmptyTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition.EnumPair;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LengthConstraint;
import org.opendaylight.yangtools.yang.model.api.type.PatternConstraint;
import org.opendaylight.yangtools.yang.model.api.type.RangeConstraint;
import org.opendaylight.yangtools.yang.model.api.type.RangeRestrictedTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.StringTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;
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
     * Creates Json models from provided module according to swagger spec.
     *
     * @param module        - Yang module to be converted
     * @param schemaContext - SchemaContext of all Yang files used by Api Doc
     * @return ObjectNode containing data used for creating examples and models in Api Doc
     * @throws IOException if I/O operation fails
     */
    public ObjectNode convertToJsonSchema(final Module module,
                                          final SchemaContext schemaContext) throws IOException {
        final ObjectNode models = JsonNodeFactory.instance.objectNode();
        final ObjectNode emptyIdentifier = JsonNodeFactory.instance.objectNode();
        models.put(UNIQUE_EMPTY_IDENTIFIER, emptyIdentifier);
        topLevelModule = module;
        processModules(module, models, schemaContext);
        processContainersAndLists(module, models, schemaContext);
        processRPCs(module, models, schemaContext);
        processIdentities(module, models);
        return models;
    }

    private void processModules(final Module module, final ObjectNode models,
                                final SchemaContext schemaContext) {
        createConcreteModelForPost(models, module.getName() + BaseYangSwaggerGenerator.MODULE_NAME_SUFFIX,
                createPropertiesForPost(module, schemaContext, module.getName()));
    }

    private void processContainersAndLists(final Module module, final ObjectNode models,
                                           final SchemaContext schemaContext) throws IOException {
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
     * Process the RPCs for a Module Spits out a file each of the name
     * {@code <rpcName>-input.json and <rpcName>-output.json}
     * for each RPC that contains input & output elements.
     *
     * @param module module
     * @throws IOException if I/O operation fails
     */
    private void processRPCs(final Module module, final ObjectNode models,
                             final SchemaContext schemaContext) throws IOException {
        final Set<RpcDefinition> rpcs = module.getRpcs();
        final String moduleName = module.getName();
        for (final RpcDefinition rpc : rpcs) {
            final ContainerSchemaNode input = rpc.getInput();
            if (!input.getChildNodes().isEmpty()) {
                final ObjectNode properties =
                        processChildren(input.getChildNodes(), moduleName, models, true, schemaContext);

                final String filename = "(" + rpc.getQName().getLocalName() + ")input";
                final ObjectNode childSchema = getSchemaTemplate();
                childSchema.put(TYPE_KEY, OBJECT_TYPE);
                childSchema.put(PROPERTIES_KEY, properties);
                childSchema.put(ID_KEY, filename);
                models.put(filename, childSchema);

                processTopData(filename, models, input);
            }

            final ContainerSchemaNode output = rpc.getOutput();
            if (!output.getChildNodes().isEmpty()) {
                final ObjectNode properties =
                        processChildren(output.getChildNodes(), moduleName, models, true, schemaContext);
                final String filename = "(" + rpc.getQName().getLocalName() + ")output";
                final ObjectNode childSchema = getSchemaTemplate();
                childSchema.put(TYPE_KEY, OBJECT_TYPE);
                childSchema.put(PROPERTIES_KEY, properties);
                childSchema.put(ID_KEY, filename);
                models.put(filename, childSchema);

                processTopData(filename, models, output);
            }
        }
    }

    private ObjectNode processTopData(final String filename, final ObjectNode models, final SchemaNode schemaNode) {
        final ObjectNode items = JsonNodeFactory.instance.objectNode();

        items.put(REF_KEY, filename);
        final ObjectNode dataNodeProperties = JsonNodeFactory.instance.objectNode();
        dataNodeProperties.put(TYPE_KEY, schemaNode instanceof ListSchemaNode ? ARRAY_TYPE : OBJECT_TYPE);
        dataNodeProperties.put(ITEMS_KEY, items);

        putIfNonNull(dataNodeProperties, DESCRIPTION_KEY, schemaNode.getDescription().orElse(null));
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.put(topLevelModule.getName() + ":" + schemaNode.getQName().getLocalName(), dataNodeProperties);
        final ObjectNode finalChildSchema = getSchemaTemplate();
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
     * @param models The ObjectNode in which the parsed identity will be put as a 'model' obj
     */
    private static void processIdentities(final Module module, final ObjectNode models) {

        final String moduleName = module.getName();
        final Set<IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final ObjectNode identityObj = JsonNodeFactory.instance.objectNode();
            final String identityName = idNode.getQName().getLocalName();
            LOG.debug("Processing Identity: {}", identityName);

            identityObj.put(ID_KEY, identityName);
            putIfNonNull(identityObj, DESCRIPTION_KEY, idNode.getDescription().orElse(null));

            final ObjectNode props = JsonNodeFactory.instance.objectNode();

            if (idNode.getBaseIdentities().isEmpty()) {
                /*
                 * This is a base identity. So lets see if it has sub types. If it does, then add them to the model
                 * definition.
                 */
                final Set<IdentitySchemaNode> derivedIds = idNode.getDerivedIdentities();

                if (derivedIds != null) {
                    final ArrayNode subTypes = new ArrayNode(JsonNodeFactory.instance);
                    for (final IdentitySchemaNode derivedId : derivedIds) {
                        subTypes.add(derivedId.getQName().getLocalName());
                    }
                    identityObj.put(SUB_TYPES_KEY, subTypes);

                }
            } else {
                /*
                 * This is a derived entity. Add it's base type & move on.
                 */
                props.put(TYPE_KEY, idNode.getBaseIdentities().iterator().next().getQName().getLocalName());
            }

            // Add the properties. For a base type, this will be an empty object as required by the Swagger spec.
            identityObj.put(PROPERTIES_KEY, props);
            models.put(identityName, identityObj);
        }
    }

    private ObjectNode processDataNodeContainer(
            final DataNodeContainer dataNode, final String parentName, final ObjectNode models, final boolean isConfig,
            final SchemaContext schemaContext) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Iterable<DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final String localName = ((SchemaNode) dataNode).getQName().getLocalName();
            final ObjectNode properties =
                    processChildren(containerChildren, parentName + "/" + localName, models, isConfig, schemaContext);
            final String nodeName = parentName + (isConfig ? OperationBuilder.CONFIG : OperationBuilder.OPERATIONAL)
                    + localName;

            final ObjectNode childSchema = getSchemaTemplate();
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

    private static void createConcreteModelForPost(final ObjectNode models, final String localName,
                                                   final JsonNode properties) {
        final String nodePostName = OperationBuilder.CONFIG + localName + Post.METHOD_NAME;
        final ObjectNode postSchema = getSchemaTemplate();
        postSchema.put(TYPE_KEY, OBJECT_TYPE);
        postSchema.put(ID_KEY, nodePostName);
        postSchema.put(PROPERTIES_KEY, properties);
        models.put(nodePostName, postSchema);
    }

    private JsonNode createPropertiesForPost(final DataNodeContainer dataNodeContainer,
                                               final SchemaContext schemaContext, final String parentName) {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        for (final DataSchemaNode childNode : dataNodeContainer.getChildNodes()) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final ObjectNode items = JsonNodeFactory.instance.objectNode();
                items.put(REF_KEY, parentName + "(config)" + childNode.getQName().getLocalName());
                final ObjectNode property = JsonNodeFactory.instance.objectNode();
                property.put(TYPE_KEY, childNode instanceof ListSchemaNode ? ARRAY_TYPE : OBJECT_TYPE);
                property.put(ITEMS_KEY, items);
                properties.put(childNode.getQName().getLocalName(), property);
            } else if (childNode instanceof LeafSchemaNode) {
                final ObjectNode property = processLeafNode((LeafSchemaNode) childNode, schemaContext);
                properties.put(childNode.getQName().getLocalName(), property);
            }
        }
        return properties;
    }

    /**
     * Processes the nodes.
     */
    private ObjectNode processChildren(
            final Iterable<DataSchemaNode> nodes, final String parentName, final ObjectNode models,
            final boolean isConfig, final SchemaContext schemaContext) throws IOException {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        for (final DataSchemaNode node : nodes) {
            if (node.isConfiguration() == isConfig) {
                final String name = resolveNodesName(node, topLevelModule, schemaContext);
                final ObjectNode property;
                if (node instanceof LeafSchemaNode) {
                    property = processLeafNode((LeafSchemaNode) node, schemaContext);

                } else if (node instanceof ListSchemaNode) {
                    property = processDataNodeContainer((ListSchemaNode) node, parentName, models, isConfig,
                            schemaContext);

                } else if (node instanceof LeafListSchemaNode) {
                    property = processLeafListNode((LeafListSchemaNode) node, schemaContext);

                } else if (node instanceof ChoiceSchemaNode) {
                    if (((ChoiceSchemaNode) node).getCases().values().iterator().hasNext()) {
                        processChoiceNode(((ChoiceSchemaNode) node).getCases().values().iterator().next()
                            .getChildNodes(), parentName, models, schemaContext, isConfig, properties);
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
                putIfNonNull(property, DESCRIPTION_KEY, node.getDescription().orElse(null));
                properties.put(topLevelModule.getName() + ":" + name, property);
            }
        }
        return properties;
    }

    private ObjectNode processLeafListNode(final LeafListSchemaNode listNode,
                                           final SchemaContext schemaContext) {
        final ObjectNode props = JsonNodeFactory.instance.objectNode();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        final int max;
        if (optConstraint.isPresent()) {
            final Integer constraintMax = optConstraint.get().getMaxElements();
            max = constraintMax == null ? 2 : constraintMax;
            processElementCount(optConstraint.get(), props);
        } else {
            max = 2;
        }

        if (max >= 2) {
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
        } else {
            processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext);
        }
        props.put(ITEMS_KEY, itemsVal);

        return props;
    }

    private void processChoiceNode(
            final Iterable<DataSchemaNode> nodes, final String moduleName, final ObjectNode models,
            final SchemaContext schemaContext, final boolean isConfig, final ObjectNode properties)
           throws IOException {
        for (final DataSchemaNode node : nodes) {
            final String name = resolveNodesName(node, topLevelModule, schemaContext);
            final ObjectNode property;

            if (node instanceof LeafSchemaNode) {
                property = processLeafNode((LeafSchemaNode) node, schemaContext);

            } else if (node instanceof ListSchemaNode) {
                property = processDataNodeContainer((ListSchemaNode) node, moduleName, models, isConfig,
                        schemaContext);

            } else if (node instanceof LeafListSchemaNode) {
                property = processLeafListNode((LeafListSchemaNode) node, schemaContext);

            } else if (node instanceof ChoiceSchemaNode) {
                if (((ChoiceSchemaNode) node).getCases().values().iterator().hasNext()) {
                    processChoiceNode(((ChoiceSchemaNode) node).getCases().values().iterator().next().getChildNodes(),
                            moduleName, models, schemaContext, isConfig, properties);
                }
                continue;

            } else if (node instanceof AnyXmlSchemaNode) {
                property = processAnyXMLNode((AnyXmlSchemaNode) node);

            } else if (node instanceof ContainerSchemaNode) {
                property = processDataNodeContainer((ContainerSchemaNode) node, moduleName, models, isConfig,
                        schemaContext);

            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }

            putIfNonNull(property, DESCRIPTION_KEY, node.getDescription().orElse(null));
            properties.put(name, property);
        }
    }

    private static void processElementCount(final ElementCountConstraint constraint, final ObjectNode props) {
        final Integer minElements = constraint.getMinElements();
        if (minElements != null) {
            props.put(MIN_ITEMS, minElements);
        }
        final Integer maxElements = constraint.getMaxElements();
        if (maxElements != null) {
            props.put(MAX_ITEMS, maxElements);
        }
    }

    private static void processMandatory(final MandatoryAware node, final ObjectNode props) {
        props.put(REQUIRED_KEY, node.isMandatory());
    }

    private ObjectNode processLeafNode(final LeafSchemaNode leafNode,
                                       final SchemaContext schemaContext) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse(null);
        putIfNonNull(property, DESCRIPTION_KEY, leafDescription);
        processMandatory(leafNode, property);
        processTypeDef(leafNode.getType(), leafNode, property, schemaContext);

        return property;
    }

    private static ObjectNode processAnyXMLNode(final AnyXmlSchemaNode leafNode) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse(null);
        putIfNonNull(property, DESCRIPTION_KEY, leafDescription);

        processMandatory(leafNode, property);
        final String localName = leafNode.getQName().getLocalName();
        property.put(TYPE_KEY, "example of anyxml " + localName);

        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final ObjectNode property, final SchemaContext schemaContext) {
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
                jsonType = name + ":" + ((IdentityrefTypeDefinition) leafTypeDef).getIdentities().iterator().next()
                        .getQName().getLocalName();

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

            } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                final Number maybeLower = ((RangeRestrictedTypeDefinition<?, ?>) leafTypeDef).getRangeConstraint()
                        .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint)
                        .orElse(null);
                jsonType = String.valueOf(maybeLower);

            } else {
                jsonType = OBJECT_TYPE;

            }
        } else {
            jsonType = String.valueOf(leafTypeDef.getDefaultValue());
        }
        putIfNonNull(property, TYPE_KEY, jsonType);
        return jsonType;
    }

    private String processLeafRef(final DataSchemaNode node, final ObjectNode property,
                                  final SchemaContext schemaContext, final TypeDefinition<?> leafTypeDef) {
        RevisionAwareXPath xpath = ((LeafrefTypeDefinition) leafTypeDef).getPathStatement();
        final SchemaNode schemaNode;

        final String xPathString = STRIP_PATTERN.matcher(xpath.toString()).replaceAll("");
        xpath = new RevisionAwareXPathImpl(xPathString, xpath.isAbsolute());

        final Module module;
        if (xpath.isAbsolute()) {
            module = findModule(schemaContext, leafTypeDef.getQName());
            schemaNode = SchemaContextUtil.findDataSchemaNode(schemaContext, module, xpath);
        } else {
            module = findModule(schemaContext, node.getQName());
            schemaNode = SchemaContextUtil.findDataSchemaNodeForRelativeXPath(schemaContext, module, node, xpath);
        }

        return processTypeDef(((TypedDataSchemaNode) schemaNode).getType(), (DataSchemaNode) schemaNode,
                property, schemaContext);
    }

    private static Module findModule(final SchemaContext schemaContext, final QName qualifiedName) {
        return schemaContext.findModule(qualifiedName.getNamespace(), qualifiedName.getRevision()).orElse(null);
    }

    private static String processBinaryType(final ObjectNode property) {
        final ObjectNode media = JsonNodeFactory.instance.objectNode();
        media.put(BINARY_ENCODING_KEY, BASE_64);
        property.put(MEDIA_KEY, media);
        return "bin1 bin2";
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType,
                                          final ObjectNode property) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        for (final EnumPair enumPair : enumPairs) {
            enumNames.add(new TextNode(enumPair.getName()));
        }

        property.put(ENUM, enumNames);
        return enumLeafType.getValues().iterator().next().getName();
    }

    private static String processBitsType(final BitsTypeDefinition bitsType,
                                          final ObjectNode property) {
        property.put(MIN_ITEMS, 0);
        property.put(UNIQUE_ITEMS_KEY, true);
        ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        final List<Bit> bits = bitsType.getBits();
        for (final Bit bit : bits) {
            enumNames.add(new TextNode(bit.getName()));
        }
        property.put(ENUM, enumNames);

        return enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1);
    }

    private static String processStringType(final TypeDefinition<?> stringType,
                                            final ObjectNode property, final String nodeName) {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        Optional<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraint();
        while (!lengthConstraints.isPresent() && type.getBaseType() != null) {
            type = type.getBaseType();
            lengthConstraints = type.getLengthConstraint();
        }

        if (lengthConstraints.isPresent()) {
            final Range<Integer> range = lengthConstraints.get().getAllowedRanges().span();
            putIfNonNull(property, MIN_LENGTH_KEY, range.lowerEndpoint());
            putIfNonNull(property, MAX_LENGTH_KEY, range.upperEndpoint());
        }

        if (type.getPatternConstraints().iterator().hasNext()) {
            final PatternConstraint pattern = type.getPatternConstraints().iterator().next();
            String regex = pattern.getJavaPatternString();
            regex = regex.substring(1, regex.length() - 1);
            final Generex generex = new Generex(regex);
            return generex.random();
        } else {
            return "Some " + nodeName;
        }
    }

    private String processUnionType(final UnionTypeDefinition unionType, final ObjectNode property,
                                    final SchemaContext schemaContext, final DataSchemaNode node) {
        final ArrayNode unionNames = new ArrayNode(JsonNodeFactory.instance);
        for (final TypeDefinition<?> typeDef : unionType.getTypes()) {
            unionNames.add(processTypeDef(typeDef, node, property, schemaContext));
        }
        property.put(ENUM, unionNames);
        return unionNames.iterator().next().asText();
    }

    /**
     * Helper method to generate a pre-filled JSON schema object.
     */
    private static ObjectNode getSchemaTemplate() {
        final ObjectNode schemaJSON = JsonNodeFactory.instance.objectNode();
        schemaJSON.put(SCHEMA_KEY, SCHEMA_URL);

        return schemaJSON;
    }

    private static void putIfNonNull(final ObjectNode property, final String key, final Number number) {
        if (key != null && number != null) {
            if (number instanceof Double) {
                property.put(key, (Double) number);
            } else if (number instanceof Float) {
                property.put(key, (Float) number);
            } else if (number instanceof Integer) {
                property.put(key, (Integer) number);
            } else if (number instanceof Short) {
                property.put(key, (Short) number);
            } else if (number instanceof Long) {
                property.put(key, (Long) number);
            }
        }
    }

    private static void putIfNonNull(final ObjectNode property, final String key, final String value) {
        if (key != null && value != null) {
            property.put(key, value);
        }
    }

}