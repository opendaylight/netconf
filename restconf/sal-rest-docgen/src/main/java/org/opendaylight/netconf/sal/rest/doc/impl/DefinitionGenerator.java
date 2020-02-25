/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.DEFINITIONS_PREFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TOP;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.CONFIG;
import static org.opendaylight.netconf.sal.rest.doc.util.RestDocgenUtil.resolveNodesName;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mifmif.common.regex.Generex;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

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
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
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
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
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
public class DefinitionGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefinitionGenerator.class);

    private static final Pattern STRIP_PATTERN = Pattern.compile("\\[[^\\[\\]]*\\]");
    private static final String UNIQUE_ITEMS_KEY = "uniqueItems";
    private static final String MAX_ITEMS = "maxItems";
    private static final String MIN_ITEMS = "minItems";
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
    private static final String UNIQUE_EMPTY_IDENTIFIER = "unique_empty_identifier";
    private static final String DEFAULT_KEY = "default";
    public static final String INPUT_SUFFIX = "_input";
    public static final String OUTPUT_SUFFIX = "_output";
    private static final String FORMAT_KEY = "format";
    private static final String STRING_TYPE = "string";
    private static final String NUMBER_TYPE = "number";
    private static final String INTEGER_TYPE = "integer";
    private static final String INT32_FORMAT = "int32";
    private static final String INT64_FORMAT = "int64";
    private static final String BOOLEAN_TYPE = "boolean";

    private Module topLevelModule;

    public DefinitionGenerator() {
    }

    /**
     * Creates Json definitions from provided module according to swagger spec.
     *
     * @param module          - Yang module to be converted
     * @param schemaContext   - SchemaContext of all Yang files used by Api Doc
     * @param definitionNames
     * @return ObjectNode containing data used for creating examples and definitions in Api Doc
     * @throws IOException if I/O operation fails
     */
    public ObjectNode convertToJsonSchema(final Module module,
                                          final SchemaContext schemaContext,
                                          final DefinitionNames definitionNames) throws IOException {
        topLevelModule = module;
        final ObjectNode definitions = JsonNodeFactory.instance.objectNode();
        final ObjectNode emptyIdentifier = JsonNodeFactory.instance.objectNode();
        definitionNames.addUnlinkedName(topLevelModule.getName() + "_module");
        definitionNames.addUnlinkedName(UNIQUE_EMPTY_IDENTIFIER);
        definitions.set(UNIQUE_EMPTY_IDENTIFIER, emptyIdentifier);
        processIdentities(module, definitions, definitionNames);
        processContainersAndLists(module, definitions, definitionNames, schemaContext);
        processRPCs(module, definitions, definitionNames, schemaContext);
        processModules(module, definitions, definitionNames, schemaContext);
        return definitions;
    }

    private void processModules(Module module, final ObjectNode definitions, final DefinitionNames definitionNames, final SchemaContext schemaContext) {
        final ObjectNode definition = JsonNodeFactory.instance.objectNode();
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        final String moduleName = module.getName();
        final String definitionName = moduleName + "_module";
        for (DataSchemaNode node : module.getChildNodes()) {
            String localName = node.getQName().getLocalName();
            String jsonNodeName = localName;
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    for (DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        ObjectNode childNodeProperties = JsonNodeFactory.instance.objectNode();

                        final String ref = DEFINITIONS_PREFIX
                                + moduleName + CONFIG
                                + "_" + localName
                                + definitionNames.getDiscriminator(node);

                        if (node instanceof ListSchemaNode) {
                            childNodeProperties.put(TYPE_KEY, ARRAY_TYPE);
                            final ObjectNode items = JsonNodeFactory.instance.objectNode();
                            items.put(REF_KEY, ref);
                            childNodeProperties.set(ITEMS_KEY, items);
                            childNodeProperties.put(DESCRIPTION_KEY, childNode.getDescription().orElse(""));
                            childNodeProperties.put(TITLE_KEY, localName + CONFIG);
                        } else {
                         /*
                            description can't be added, because nothing allowed alongside $ref.
                            allOf is not an option, because ServiceNow can't parse it.
                          */
                            childNodeProperties.put(REF_KEY, ref);
                        }
                        properties.set(jsonNodeName, childNodeProperties);
                    }
                } else {
                    if (node instanceof LeafSchemaNode) {
                        processLeafNode((LeafSchemaNode) node, jsonNodeName, properties, required, schemaContext, definitions, definitionNames);
                    }
                }
            }
        }
        definition.put(TITLE_KEY, definitionName);
        definition.put(TYPE_KEY, OBJECT_TYPE);
        definition.set(PROPERTIES_KEY, properties);
        definition.put(DESCRIPTION_KEY, module.getDescription().orElse(""));
        setRequiredIfNotEmpty(definition, required);

        definitions.set(definitionName, definition);
    }

    private void processContainersAndLists(final Module module, final ObjectNode definitions,
                                           DefinitionNames definitionNames, final SchemaContext schemaContext) throws IOException {
        final String moduleName = module.getName();

        for (final DataSchemaNode childNode : module.getChildNodes()) {
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames, true, schemaContext);
                }
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames, false, schemaContext);
                processActionNodeContainer(childNode, moduleName, definitions, definitionNames, schemaContext);
            }
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final ObjectNode definitions, DefinitionNames definitionNames, final SchemaContext schemaContext) throws IOException {
        for (ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            processOperations(actionDef, moduleName, definitions, definitionNames, schemaContext);
        }
    }

    private void processRPCs(final Module module, final ObjectNode definitions, DefinitionNames definitionNames, final SchemaContext schemaContext)
            throws IOException {
        final String moduleName = module.getName();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            processOperations(rpcDefinition, moduleName, definitions, definitionNames, schemaContext);
        }
    }


    private void processOperations(final OperationDefinition operationDef, final String parentName,
                                   final ObjectNode definitions, DefinitionNames definitionNames, final SchemaContext schemaContext) throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitions, definitionNames, schemaContext);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitions, definitionNames, schemaContext);
    }

    private void processOperationInputOutput(ContainerSchemaNode container,
                                             String operationName, String parentName,
                                             boolean isInput, final ObjectNode definitions,
                                             DefinitionNames definitionNames, SchemaContext schemaContext) throws IOException {
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            processChildren(childSchema, container.getChildNodes(), parentName, definitions, definitionNames, false, schemaContext);

            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.put(TITLE_KEY, filename);
            String discriminator = definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            definitions.set(filename + discriminator, childSchema);

            processTopData(filename, discriminator, definitions, container);
        }
    }

    private ObjectNode processTopData(final String filename, final String discriminator,
                                      final ObjectNode definitions, final SchemaNode schemaNode) {
        final ObjectNode dataNodeProperties = JsonNodeFactory.instance.objectNode();
        final String name = filename + discriminator;
        final String ref = DEFINITIONS_PREFIX + name;
        final String topName = filename + TOP;

        if (schemaNode instanceof ListSchemaNode) {
            dataNodeProperties.put(TYPE_KEY, ARRAY_TYPE);
            final ObjectNode items = JsonNodeFactory.instance.objectNode();
            items.put(REF_KEY, ref);
            dataNodeProperties.set(ITEMS_KEY, items);
            dataNodeProperties.put(DESCRIPTION_KEY, schemaNode.getDescription().orElse(""));
        } else {
             /*
                description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            dataNodeProperties.put(REF_KEY, ref);
        }

        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set(schemaNode.getQName().getLocalName(), dataNodeProperties);
        final ObjectNode finalChildSchema = JsonNodeFactory.instance.objectNode();
        finalChildSchema.put(TYPE_KEY, OBJECT_TYPE);
        finalChildSchema.set(PROPERTIES_KEY, properties);
        finalChildSchema.put(TITLE_KEY, topName);


        definitions.set(topName + discriminator, finalChildSchema);

        return dataNodeProperties;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     *
     * @param module          The module from which the identity stmt will be processed
     * @param definitions          The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames
     */
    private static void processIdentities(final Module module, final ObjectNode definitions, DefinitionNames definitionNames) {

        final String moduleName = module.getName();
        final Set<IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final ObjectNode identityObj = buildIdentityObject(idNode);
            final String idName = idNode.getQName().getLocalName();
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(idName));
            final String name = idName + discriminator;
            definitions.set(name, identityObj);
        }
    }

    private static void populateEnumWithDerived(Set<IdentitySchemaNode> derivedIds, ArrayNode enumPayload) {
        for (IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(derivedId.getDerivedIdentities(), enumPayload);
        }
    }

    private ObjectNode processDataNodeContainer(
            final DataNodeContainer dataNode, final String parentName,
            final ObjectNode definitions, DefinitionNames definitionNames,
            final boolean isConfig, final SchemaContext schemaContext) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Iterable<DataSchemaNode> containerChildren = dataNode.getChildNodes();
            SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = (schemaNode).getQName().getLocalName();
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            final ObjectNode properties =
                    processChildren(childSchema, containerChildren, parentName + "_" + localName, definitions, definitionNames, isConfig, schemaContext);

            final String nodeName = parentName + (isConfig ? CONFIG : "") + "_" + localName;

            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.set(PROPERTIES_KEY, properties);
            childSchema.put(TITLE_KEY, nodeName);
            childSchema.put(DESCRIPTION_KEY, ((DataSchemaNode) dataNode).getDescription().orElse(""));

            String discriminator;

            if (!definitionNames.isListedNode(schemaNode)) {
                List<String> names = List.of(parentName + CONFIG + "_" + localName,
                        parentName + CONFIG + "_" + localName + TOP,
                        parentName + "_" + localName,
                        parentName + "_" + localName + TOP);
                discriminator = definitionNames.pickDiscriminator(schemaNode, names);
            } else {
                discriminator = definitionNames.getDiscriminator(schemaNode);
            }

            definitions.set(nodeName + discriminator, childSchema);

            return processTopData(nodeName, discriminator, definitions, schemaNode);
        }
        return null;
    }

    /**
     * Processes the nodes.
     */
    private ObjectNode processChildren(
            final ObjectNode parentNode,
            final Iterable<DataSchemaNode> nodes, final String parentName, final ObjectNode definitions, DefinitionNames definitionNames,
            final boolean isConfig, final SchemaContext schemaContext) throws IOException {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        for (final DataSchemaNode node : nodes) {
            if (!isConfig || node.isConfiguration()) {
                final String name = resolveNodesName(node, topLevelModule, schemaContext);
                final String propertyName = name;
                final ObjectNode property;
                if (node instanceof LeafSchemaNode) {
                    processLeafNode((LeafSchemaNode) node, propertyName, properties,
                            required, schemaContext, definitions, definitionNames);
                } else if (node instanceof AnyxmlSchemaNode) {
                    processAnyXMLNode((AnyxmlSchemaNode) node, propertyName, properties,
                            required);
                } else if (node instanceof AnydataSchemaNode) {
                    processAnydataNode((AnydataSchemaNode) node, propertyName, properties,
                            required);
                } else {
                    if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                        property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions,
                                definitionNames, isConfig, schemaContext);
                        if (!isConfig) {
                            processActionNodeContainer(node, parentName, definitions, definitionNames, schemaContext);
                        }
                    } else if (node instanceof LeafListSchemaNode) {
                        property = processLeafListNode((LeafListSchemaNode) node, schemaContext, definitions, definitionNames);

                    } else if (node instanceof ChoiceSchemaNode) {
                        for (CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases().values()) {
                            processChoiceNode(variant.getChildNodes(), parentName, definitions, definitionNames, isConfig,
                                    schemaContext, properties);
                        }
                        continue;

                    } else {
                        throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
                    }
                    properties.set(propertyName, property);
                }
            }
        }
        parentNode.set(PROPERTIES_KEY, properties);
        setRequiredIfNotEmpty(parentNode, required);
        return properties;
    }

    private ObjectNode processLeafListNode(final LeafListSchemaNode listNode,
                                           final SchemaContext schemaContext,
                                           final ObjectNode definitions, DefinitionNames definitionNames) {
        final ObjectNode props = JsonNodeFactory.instance.objectNode();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        processElementCount(optConstraint, props);

        processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext, definitions, definitionNames);
        props.set(ITEMS_KEY, itemsVal);

        props.put(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return props;
    }

    private void processChoiceNode(
            final Iterable<DataSchemaNode> nodes, final String parentName, final ObjectNode definitions,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaContext schemaContext, final ObjectNode properties)
            throws IOException {
        for (final DataSchemaNode node : nodes) {
            final String name = resolveNodesName(node, topLevelModule, schemaContext);
            final ObjectNode property;

            /*
                Ignore mandatoriness(passing unreferenced arrayNode to process...Node), because choice produces multiple
                properties
             */
            if (node instanceof LeafSchemaNode) {
                processLeafNode((LeafSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode(), schemaContext, definitions, definitionNames);
            } else if (node instanceof AnyxmlSchemaNode) {
                processAnyXMLNode((AnyxmlSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode());
            } else if (node instanceof AnydataSchemaNode) {
                processAnydataNode((AnydataSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode());
            } else {
                if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                    property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions,
                            definitionNames, isConfig, schemaContext);
                    if (!isConfig) {
                        processActionNodeContainer(node, parentName, definitions, definitionNames, schemaContext);
                    }
                } else if (node instanceof LeafListSchemaNode) {
                    property = processLeafListNode((LeafListSchemaNode) node, schemaContext, definitions, definitionNames);

                } else if (node instanceof ChoiceSchemaNode) {
                    for (CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases().values()) {
                        processChoiceNode(variant.getChildNodes(), parentName, definitions, definitionNames, isConfig,
                                schemaContext, properties);
                    }
                    continue;
                } else {
                    throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
                }
                properties.set(name, property);
            }
        }
    }

    private static void processElementCount(final Optional<ElementCountConstraint> constraint, final ObjectNode props) {
        if (constraint.isPresent()) {
            final ElementCountConstraint constr = constraint.get();
            final Integer minElements = constr.getMinElements();
            if (minElements != null) {
                props.put(MIN_ITEMS, minElements);
            }
            final Integer maxElements = constr.getMaxElements();
            if (maxElements != null) {
                props.put(MAX_ITEMS, maxElements);
            }
        }
    }

    private static void processMandatory(final MandatoryAware node, String nodeName, final ArrayNode required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private ObjectNode processLeafNode(final LeafSchemaNode leafNode,
                                       final String jsonLeafName,
                                       final ObjectNode properties,
                                       final ArrayNode required,
                                       final SchemaContext schemaContext,
                                       final ObjectNode definitions, DefinitionNames definitionNames) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            property.put(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, property, schemaContext, definitions, definitionNames);
        properties.set(jsonLeafName, property);
        processMandatory(leafNode, jsonLeafName, required);

        return property;
    }

    private static ObjectNode processAnydataNode(final AnydataSchemaNode leafNode, final String name,
                                                 final ObjectNode properties, final ArrayNode required) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.put(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);
        processMandatory(leafNode, name, required);
        properties.set(name, property);

        return property;
    }

    private static ObjectNode processAnyXMLNode(final AnyxmlSchemaNode leafNode, final String name,
                                                final ObjectNode properties, final ArrayNode required) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.put(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);
        processMandatory(leafNode, name, required);
        properties.set(name, property);

        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final ObjectNode property, final SchemaContext schemaContext,
                                  final ObjectNode definitions, DefinitionNames definitionNames) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(property);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef, property, definitions, definitionNames);

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, property, node.getQName().getLocalName());

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = STRING_TYPE;
            setDefaultValue(property, UNIQUE_EMPTY_IDENTIFIER);
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processLeafRef(node, property, schemaContext, leafTypeDef, definitions, definitionNames);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            setDefaultValue(property, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
            jsonType = processNumberType((RangeRestrictedTypeDefinition) leafTypeDef, property);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition) {
            jsonType = processInstanceIdentifierType(node, property, schemaContext);
        } else {
            jsonType = STRING_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(property, TYPE_KEY, jsonType);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final Object defaultValue = leafTypeDef.getDefaultValue().get();
                if (defaultValue instanceof String) {
                    final String stringDefaultValue = (String) defaultValue;
                    if (leafTypeDef instanceof BooleanTypeDefinition) {
                        setDefaultValue(property, Boolean.valueOf(stringDefaultValue));
                    } else if (leafTypeDef instanceof DecimalTypeDefinition
                            || leafTypeDef instanceof Uint64TypeDefinition) {
                        setDefaultValue(property, new BigDecimal(stringDefaultValue));
                    } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                        //uint8,16,32 int8,16,32,64
                        setDefaultValue(property, Long.valueOf(stringDefaultValue));
                    } else {
                        setDefaultValue(property, stringDefaultValue);
                    }
                } else {
                    //we should never get here. getDefaultValue always gives us string
                    setDefaultValue(property, defaultValue.toString());
                }
            }
        }
        return jsonType;
    }

    private String processLeafRef(final DataSchemaNode node, final ObjectNode property,
                                  final SchemaContext schemaContext, final TypeDefinition<?> leafTypeDef,
                                  final ObjectNode definitions, final DefinitionNames definitionNames) {
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
                property, schemaContext, definitions, definitionNames);
    }

    private static Module findModule(final SchemaContext schemaContext, final QName qualifiedName) {
        return schemaContext.findModule(qualifiedName.getNamespace(), qualifiedName.getRevision()).orElse(null);
    }

    private static String processBinaryType(final ObjectNode property) {
        property.put(FORMAT_KEY, "byte");
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

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
                                          final ObjectNode property,
                                          final ObjectNode definitions,
                                          final DefinitionNames definitionNames) {
        String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, definitions, definitionNames);
        } else {
            SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        property.put(REF_KEY, DEFINITIONS_PREFIX + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
                                              final ObjectNode definitions, final DefinitionNames definitionNames) {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        if (!definitionNames.isListedNode(idNode)) {
            final String identityName = idNode.getQName().getLocalName();
            final ObjectNode identityObj = buildIdentityObject(idNode);
            String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            String name = identityName + discriminator;
            definitions.set(name, identityObj);
            return name;
        } else {
            return definitionNames.getDiscriminator(idNode);
        }
    }

    private static ObjectNode buildIdentityObject(final IdentitySchemaNode idNode) {
        final ObjectNode identityObj = JsonNodeFactory.instance.objectNode();
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);

        identityObj.put(TITLE_KEY, identityName);
        identityObj.put(DESCRIPTION_KEY, idNode.getDescription().orElse(""));

        final Set<IdentitySchemaNode> derivedIds = idNode.getDerivedIdentities();

        final ArrayNode enumPayload = JsonNodeFactory.instance.arrayNode();
        enumPayload.add(identityName);
        populateEnumWithDerived(derivedIds, enumPayload);
        identityObj.set(ENUM_KEY, enumPayload);
        identityObj.put(TYPE_KEY, STRING_TYPE);
        return identityObj;
    }

    private boolean isImported(IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(topLevelModule.getQNameModule());
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
            maybeLower.ifPresent(number -> setDefaultValue(property, (BigDecimal) number));
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

    private String processInstanceIdentifierType(final DataSchemaNode node, final ObjectNode property,
                                                 final SchemaContext schemaContext) {
        SchemaPath path = node.getPath();

        while (path.getParent() != null && path.getParent().getPathFromRoot().iterator().hasNext()) {
            path = path.getParent();
        }

        QName rootContainer = path.getLastComponent();
        String rootContainerName = rootContainer.getLocalName();
        String prefix = schemaContext.findModule(rootContainer.getModule()).get().getPrefix();
        setDefaultValue(property, String.format("/%s:%s", prefix, rootContainerName));
        return STRING_TYPE;
    }

    private String processUnionType(final UnionTypeDefinition unionType) {
        boolean isStringTakePlace = false;
        boolean isNumberTakePlace = false;
        boolean isBooleanTakePlace = false;
        for (final TypeDefinition<?> typeDef : unionType.getTypes()) {
            if (!isStringTakePlace) {
                if (typeDef instanceof StringTypeDefinition
                        || typeDef instanceof BitsTypeDefinition
                        || typeDef instanceof BinaryTypeDefinition
                        || typeDef instanceof IdentityrefTypeDefinition
                        || typeDef instanceof EnumTypeDefinition
                        || typeDef instanceof LeafrefTypeDefinition
                        || typeDef instanceof UnionTypeDefinition) {
                    isStringTakePlace = true;
                } else if (!isNumberTakePlace && typeDef instanceof RangeRestrictedTypeDefinition) {
                    isNumberTakePlace = true;
                } else if (!isBooleanTakePlace && typeDef instanceof BooleanTypeDefinition) {
                    isBooleanTakePlace = true;
                }
            }
        }
        if (isStringTakePlace) {
            return STRING_TYPE;
        }
        if (isBooleanTakePlace) {
            if (isNumberTakePlace) {
                return STRING_TYPE;
            }
            return BOOLEAN_TYPE;
        }
        return NUMBER_TYPE;
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

    private static void setRequiredIfNotEmpty(final ObjectNode node, final ArrayNode required) {
        if (required.size() > 0) {
            node.set(REQUIRED_KEY, required);
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

    private static void setDefaultValue(ObjectNode property, BigDecimal value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(ObjectNode property, Boolean value) {
        property.put(DEFAULT_KEY, value);
    }
}
