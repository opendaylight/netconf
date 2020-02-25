/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.model.builder.NewOperationBuilder.DEFINITIONS_PREFIX;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder;
import org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.Post;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
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
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.PathExpression;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition.Bit;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.DecimalTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EmptyTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition.EnumPair;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int8TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LengthConstraint;
import org.opendaylight.yangtools.yang.model.api.type.PatternConstraint;
import org.opendaylight.yangtools.yang.model.api.type.RangeConstraint;
import org.opendaylight.yangtools.yang.model.api.type.RangeRestrictedTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.StringTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint8TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.PathExpressionImpl;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates JSON Schema for data defined in YANG. This class is not thread-safe.
 */
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
    private static final String ENUM_KEY = "enum";
    private static final String TITLE_KEY = "title";
    private static final String SUB_TYPES_KEY = "subTypes";
    private static final String UNIQUE_EMPTY_IDENTIFIER = "unique_empty_identifier";
    private static final String DEFAULT_KEY = "default";
    private static final String FORMAT_KEY = "format";
    private static final String STRING_TYPE = "string";
    private static final String NUMBER_TYPE = "number";
    private static final String INTEGER_TYPE = "integer";
    private static final String DOUBLE_FORMAT = "double";
    private static final String INT32_FORMAT = "int32";
    private static final String INT64_FORMAT = "int64";
    private static final String BOOLEAN_TYPE = "boolean";

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
        models.set(UNIQUE_EMPTY_IDENTIFIER, emptyIdentifier);
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
                processActionNodeContainer(childNode, moduleName, models, schemaContext);
            }
        }
    }

    /**
     * Process the Actions Swagger UI.
     *
     * @param DataSchemaNode childNode
     * @param String         moduleName
     * @param ObjectNode     models
     * @param SchemaContext  schemaContext
     * @throws IOException if I/O operation fails
     */
    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final ObjectNode models, final SchemaContext schemaContext) throws IOException {
        for (ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            processOperations(actionDef, moduleName, models, schemaContext);
        }
    }

    /**
     * Process the RPCs for Swagger UI.
     *
     * @param Module        module
     * @param ObjectNode    models
     * @param SchemaContext schemaContext
     * @throws IOException if I/O operation fails
     */
    private void processRPCs(final Module module, final ObjectNode models, final SchemaContext schemaContext)
            throws IOException {
        final String moduleName = module.getName();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            processOperations(rpcDefinition, moduleName, models, schemaContext);
        }
    }

    /**
     * Process the Operations for a Module Spits out a file each of the name {@code <operationName>-input.json and
     * <oprationName>-output.json} for each Operation that contains input & output elements.
     *
     * @param OperationDefinition operationDef
     * @param String              moduleName
     * @param ObjectNode          models
     * @param SchemaContext       schemaContext
     * @throws IOException if I/O operation fails
     */
    private void processOperations(final OperationDefinition operationDef, final String moduleName,
                                   final ObjectNode models, final SchemaContext schemaContext) throws IOException {
        final ContainerSchemaNode input = operationDef.getInput();
        if (!input.getChildNodes().isEmpty()) {
            final ObjectNode properties =
                    processChildren(input.getChildNodes(), moduleName, models, true, schemaContext);

            final String filename = "(" + operationDef.getQName().getLocalName() + ")input";
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.set(PROPERTIES_KEY, properties);
            childSchema.put(TITLE_KEY, filename);
            models.set(filename, childSchema);

            processTopData(filename, models, input);
        }

        final ContainerSchemaNode output = operationDef.getOutput();
        if (!output.getChildNodes().isEmpty()) {
            final ObjectNode properties =
                    processChildren(output.getChildNodes(), moduleName, models, true, schemaContext);
            final String filename = "(" + operationDef.getQName().getLocalName() + ")output";
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.set(PROPERTIES_KEY, properties);
            childSchema.put(TITLE_KEY, filename);
            models.set(filename, childSchema);

            processTopData(filename, models, output);
        }
    }

    private ObjectNode processTopData(final String filename, final ObjectNode models, final SchemaNode schemaNode) {

        final ObjectNode dataNodeProperties = JsonNodeFactory.instance.objectNode();
        if (schemaNode instanceof ListSchemaNode) {
            dataNodeProperties.put(TYPE_KEY, ARRAY_TYPE);
            final ObjectNode items = JsonNodeFactory.instance.objectNode();
            items.put(REF_KEY, filename);
            dataNodeProperties.set(ITEMS_KEY, items);
            dataNodeProperties.put(DESCRIPTION_KEY, schemaNode.getDescription().orElse(""));
        } else {
             /*
                description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            dataNodeProperties.put(REF_KEY, filename);
        }

        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set(topLevelModule.getName() + ":" + schemaNode.getQName().getLocalName(), dataNodeProperties);
        final ObjectNode finalChildSchema = JsonNodeFactory.instance.objectNode();
        finalChildSchema.put(TYPE_KEY, OBJECT_TYPE);
        finalChildSchema.set(PROPERTIES_KEY, properties);
        finalChildSchema.put(TITLE_KEY, filename + OperationBuilder.TOP);
        models.set(filename + OperationBuilder.TOP, finalChildSchema);

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

            identityObj.put(TITLE_KEY, identityName);
            putIfNonNull(identityObj, DESCRIPTION_KEY, idNode.getDescription().orElse(""));

            final Set<IdentitySchemaNode> derivedIds = idNode.getDerivedIdentities();

            final ArrayNode enumPayload = JsonNodeFactory.instance.arrayNode();
            enumPayload.add(identityName);
            populateEnumWithDerived(derivedIds, enumPayload);
            identityObj.set(ENUM_KEY, enumPayload);

            identityObj.put(TYPE_KEY, STRING_TYPE);

            // Add the properties. For a base type, this will be an empty object as required by the Swagger spec.
            models.set(identityName, identityObj);
        }

    }

    private static void populateEnumWithDerived(Set<IdentitySchemaNode> derivedIds, ArrayNode enumPayload) {
        for (IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(derivedId.getDerivedIdentities(), enumPayload);
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

            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.set(PROPERTIES_KEY, properties);

            childSchema.put(TITLE_KEY, nodeName);
            models.set(nodeName, childSchema);

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
        final ObjectNode postSchema = JsonNodeFactory.instance.objectNode();
        postSchema.put(TYPE_KEY, OBJECT_TYPE);
        postSchema.put(TITLE_KEY, nodePostName);
        postSchema.set(PROPERTIES_KEY, properties);
        models.set(nodePostName, postSchema);
    }

    private JsonNode createPropertiesForPost(final DataNodeContainer dataNodeContainer,
                                             final SchemaContext schemaContext, final String parentName) {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        for (final DataSchemaNode childNode : dataNodeContainer.getChildNodes()) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final String ref = parentName + "(config)" + childNode.getQName().getLocalName();
                final ObjectNode property = JsonNodeFactory.instance.objectNode();
                if (childNode instanceof ListSchemaNode) {
                    final ObjectNode items = JsonNodeFactory.instance.objectNode();
                    items.put(REF_KEY, ref);
                    property.put(TYPE_KEY, ARRAY_TYPE);
                    property.set(ITEMS_KEY, items);
                } else {
                    property.put(REF_KEY, ref);
                }

                properties.set(childNode.getQName().getLocalName(), property);
            } else if (childNode instanceof LeafSchemaNode) {
                final ObjectNode property = processLeafNode((LeafSchemaNode) childNode, schemaContext);
                properties.set(childNode.getQName().getLocalName(), property);
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
                    for (CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases().values()) {
                        processChoiceNode(variant.getChildNodes(), parentName, models, schemaContext, isConfig, properties);
                    }
                    continue;

                } else if (node instanceof AnyxmlSchemaNode) {
                    property = processAnyXMLNode((AnyxmlSchemaNode) node);

                } else if (node instanceof AnydataSchemaNode) {
                    property = processAnydataNode((AnydataSchemaNode) node);

                } else if (node instanceof ContainerSchemaNode) {
                    property = processDataNodeContainer((ContainerSchemaNode) node, parentName, models, isConfig,
                            schemaContext);

                } else {
                    throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
                }
                /*
                    description can't be added, because nothing allowed alongside $ref.
                    allOf is not an option, because ServiceNow can't parse it.
                */
                if (!(node instanceof LeafSchemaNode
                        && ((LeafSchemaNode) node).getType() instanceof IdentityrefTypeDefinition)) {
                    putIfNonNull(property, DESCRIPTION_KEY, node.getDescription().orElse(""));
                }
                properties.set(topLevelModule.getName() + ":" + name, property);
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
        props.set(ITEMS_KEY, itemsVal);

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
                for (CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases().values()) {
                    processChoiceNode(variant.getChildNodes(), moduleName, models, schemaContext, isConfig, properties);
                }
                continue;

            } else if (node instanceof AnyxmlSchemaNode) {
                property = processAnyXMLNode((AnyxmlSchemaNode) node);

            } else if (node instanceof AnydataSchemaNode) {
                property = processAnydataNode((AnydataSchemaNode) node);

            } else if (node instanceof ContainerSchemaNode) {
                property = processDataNodeContainer((ContainerSchemaNode) node, moduleName, models, isConfig,
                        schemaContext);

            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }

            /*
                    description can't be added, because nothing allowed alongside $ref.
                    allOf is not an option, because ServiceNow can't parse it.
            */
            if (!(node instanceof LeafSchemaNode
                    && ((LeafSchemaNode) node).getType() instanceof IdentityrefTypeDefinition)) {
                putIfNonNull(property, DESCRIPTION_KEY, node.getDescription().orElse(""));
            }
            properties.set(name, property);
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

        final String leafDescription = leafNode.getDescription().orElse("");
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(property, DESCRIPTION_KEY, leafDescription);
        }
//        processMandatory(leafNode, property);
        processTypeDef(leafNode.getType(), leafNode, property, schemaContext);

        return property;
    }

    private static ObjectNode processAnydataNode(final AnydataSchemaNode leafNode) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        putIfNonNull(property, DESCRIPTION_KEY, leafDescription);

        processMandatory(leafNode, property);
        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);

        return property;
    }

    private static ObjectNode processAnyXMLNode(final AnyxmlSchemaNode leafNode) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        putIfNonNull(property, DESCRIPTION_KEY, leafDescription);

        processMandatory(leafNode, property);
        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);

        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final ObjectNode property, final SchemaContext schemaContext) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(property);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType =  processStringType(leafTypeDef, property, node.getQName().getLocalName());

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef, property, schemaContext, node);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = STRING_TYPE;
            setDefaultValue(property, UNIQUE_EMPTY_IDENTIFIER);
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processLeafRef(node, property, schemaContext, leafTypeDef);

        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            setDefaultValue(property, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
            jsonType = processNumberType((RangeRestrictedTypeDefinition) leafTypeDef, property);
        } else {
            jsonType = OBJECT_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(property, TYPE_KEY, jsonType);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final String stringDefaultValue = String.valueOf(leafTypeDef.getDefaultValue().get());
                if (leafTypeDef instanceof BooleanTypeDefinition) {
                    setDefaultValue(property, Boolean.valueOf(stringDefaultValue));
                } else if (leafTypeDef instanceof DecimalTypeDefinition) {
                    setDefaultValue(property, new BigDecimal(stringDefaultValue));
                } else if (leafTypeDef instanceof Uint64TypeDefinition) {
                    setDefaultValue(property, new BigDecimal(stringDefaultValue));
                } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                    //uint8,16,32 int8,16,32,64
                    setDefaultValue(property, Long.valueOf(stringDefaultValue));
                } else {
                    setDefaultValue(property, stringDefaultValue);
                }
            }
        }
        return jsonType;
    }

    private String processLeafRef(final DataSchemaNode node, final ObjectNode property,
                                  final SchemaContext schemaContext, final TypeDefinition<?> leafTypeDef) {
        PathExpression xpath = ((LeafrefTypeDefinition) leafTypeDef).getPathStatement();
        final SchemaNode schemaNode;

        final String xPathString = STRIP_PATTERN.matcher(xpath.getOriginalString()).replaceAll("");
        xpath = new PathExpressionImpl(xPathString, xpath.isAbsolute());

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
        property.put(FORMAT_KEY, "byte");
        setDefaultValue(property, "bin1 bin2");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType,
                                          final ObjectNode property) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        for (final EnumPair enumPair : enumPairs) {
            enumNames.add(new TextNode(enumPair.getName()));
        }

        property.set(ENUM_KEY, enumNames);
        setDefaultValue(property, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(IdentityrefTypeDefinition leafTypeDef, ObjectNode property) {
        property.put(REF_KEY, DEFINITIONS_PREFIX + leafTypeDef.getIdentities().iterator().next().getQName().getLocalName());
        return STRING_TYPE;
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
        property.set(ENUM_KEY, enumNames);
        property.put(DEFAULT_KEY, enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1));
        return STRING_TYPE;
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
            setDefaultValue(property, generex.random());
        } else {
            setDefaultValue(property, "Some " + nodeName);
        }
        return STRING_TYPE;
    }

    private String processNumberType(RangeRestrictedTypeDefinition leafTypeDef, ObjectNode property) {
        final Optional<Number> maybeLower = ((RangeRestrictedTypeDefinition<?, ?>) leafTypeDef).getRangeConstraint()
                .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            maybeLower.ifPresent(number -> setDefaultValue(property, (BigDecimal)number));
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
                || leafTypeDef instanceof Uint16TypeDefinition
                || leafTypeDef instanceof Int8TypeDefinition
                || leafTypeDef instanceof Int16TypeDefinition
                || leafTypeDef instanceof Int32TypeDefinition) {

            property.put(FORMAT_KEY, INT32_FORMAT);
            maybeLower.ifPresent(number -> setDefaultValue(property, Integer.valueOf(number.toString())));
        } else if (leafTypeDef instanceof Uint32TypeDefinition
                || leafTypeDef instanceof Int64TypeDefinition) {

            property.put(FORMAT_KEY, INT64_FORMAT);
            maybeLower.ifPresent(number -> setDefaultValue(property, Long.valueOf(number.toString())));
        } else {
            //uint64
            setDefaultValue(property, 0);
        }
        return INTEGER_TYPE;
    }

    private String processUnionType(final UnionTypeDefinition unionType, final ObjectNode property,
                                    final SchemaContext schemaContext, final DataSchemaNode node) {
        final ArrayNode unionNames = new ArrayNode(JsonNodeFactory.instance);
        for (final TypeDefinition<?> typeDef : unionType.getTypes()) {
            unionNames.add(processTypeDef(typeDef, node, property, schemaContext));
        }
        property.set(ENUM_KEY, unionNames);
        setDefaultValue(property, unionNames.iterator().next().asText());
        return STRING_TYPE;
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

    private static void setDefaultValue(ObjectNode property, String value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, Integer value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, Long value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, Double value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, BigDecimal value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, Boolean value) {
        property.put(DEFAULT_KEY, value);
    }
}
