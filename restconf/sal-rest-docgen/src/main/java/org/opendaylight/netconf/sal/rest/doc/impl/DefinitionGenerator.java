/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator.MODULE_NAME_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.CONFIG;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.NAME_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TOP;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.XML_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.getAppropriateModelPrefix;
import static org.opendaylight.netconf.sal.rest.doc.util.RestDocgenUtil.resolveFullNameFromNode;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mifmif.common.regex.Generex;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraint;
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraintAware;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MandatoryAware;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
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
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates JSON Schema for data defined in YANG. This class is not thread-safe.
 */
public class DefinitionGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefinitionGenerator.class);

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
    private static final String ARRAY_TYPE = "array";
    private static final String ENUM_KEY = "enum";
    private static final String TITLE_KEY = "title";
    private static final String DEFAULT_KEY = "default";
    private static final String FORMAT_KEY = "format";
    private static final String NAMESPACE_KEY = "namespace";
    public static final String INPUT = "input";
    public static final String INPUT_SUFFIX = "_input";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_SUFFIX = "_output";
    private static final String STRING_TYPE = "string";
    private static final String OBJECT_TYPE = "object";
    private static final String NUMBER_TYPE = "number";
    private static final String INTEGER_TYPE = "integer";
    private static final String INT32_FORMAT = "int32";
    private static final String INT64_FORMAT = "int64";
    private static final String BOOLEAN_TYPE = "boolean";
    // Special characters used in automaton inside Generex.
    // See https://www.brics.dk/automaton/doc/dk/brics/automaton/RegExp.html
    private static final Pattern AUTOMATON_SPECIAL_CHARACTERS = Pattern.compile("[@&\"<>#~]");

    private Module topLevelModule;

    public DefinitionGenerator() {
    }

    /**
     * Creates Json definitions from provided module according to swagger spec.
     *
     * @param module          - Yang module to be converted
     * @param schemaContext   - SchemaContext of all Yang files used by Api Doc
     * @param definitionNames - Store for definition names
     * @return ObjectNode containing data used for creating examples and definitions in Api Doc
     * @throws IOException if I/O operation fails
     */


    public ObjectNode convertToJsonSchema(final Module module, final EffectiveModelContext schemaContext,
                                          final ObjectNode definitions, final DefinitionNames definitionNames,
                                          final OAversion oaversion)
            throws IOException {
        topLevelModule = module;

        processIdentities(module, definitions, definitionNames, schemaContext);
        processContainersAndLists(module, definitions, definitionNames, schemaContext, oaversion);
        processRPCs(module, definitions, definitionNames, schemaContext, oaversion);

        return definitions;
    }

    public ObjectNode convertToJsonSchema(final Module module, final EffectiveModelContext schemaContext,
                                          final DefinitionNames definitionNames, final OAversion oaversion,
                                          final boolean isForSingleModule)
            throws IOException {
        final ObjectNode definitions = JsonNodeFactory.instance.objectNode();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(module.getName() + CONFIG + MODULE_NAME_SUFFIX);
        }
        return convertToJsonSchema(module, schemaContext, definitions, definitionNames, oaversion);
    }

    private static boolean isSchemaNodeMandatory(final DataSchemaNode node) {
        //    https://www.rfc-editor.org/rfc/rfc7950#page-14
        //    mandatory node: A mandatory node is one of:
        if (node instanceof ContainerSchemaNode containerNode) {
            //  A container node without a "presence" statement and that has at least one mandatory node as a child.
            if (containerNode.isPresenceContainer()) {
                return false;
            }
            for (final DataSchemaNode childNode : containerNode.getChildNodes()) {
                if (childNode instanceof MandatoryAware mandatoryAware && mandatoryAware.isMandatory()) {
                    return true;
                }
            }
        }
        //  A list or leaf-list node with a "min-elements" statement with a value greater than zero.
        return node instanceof ElementCountConstraintAware constraintAware
                && constraintAware.getElementCountConstraint()
                .map(ElementCountConstraint::getMinElements)
                .orElse(0)
                > 0;
    }

    private void processContainersAndLists(final Module module, final ObjectNode definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext, final OAversion oaversion)
                throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                            true, stack, oaversion);
                }
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                        false, stack, oaversion);
                processActionNodeContainer(childNode, moduleName, definitions, definitionNames, stack, oaversion);
            }
            stack.exit();
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final ObjectNode definitions, final DefinitionNames definitionNames,
                                            final SchemaInferenceStack stack, final OAversion oaversion)
            throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, definitions, definitionNames, stack, oaversion);
            stack.exit();
        }
    }

    private void processRPCs(final Module module, final ObjectNode definitions, final DefinitionNames definitionNames,
                             final EffectiveModelContext schemaContext, final OAversion oaversion) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, definitions, definitionNames, stack, oaversion);
            stack.exit();
        }
    }

    private void processOperations(final OperationDefinition operationDef, final String parentName,
            final ObjectNode definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final OAversion oaversion)
                throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitions,
                definitionNames, stack, oaversion);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitions,
                definitionNames, stack, oaversion);
    }

    private void processOperationInputOutput(final ContainerLike container, final String operationName,
                                             final String parentName, final boolean isInput,
                                             final ObjectNode definitions, final DefinitionNames definitionNames,
                                             final SchemaInferenceStack stack, final OAversion oaversion)
            throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            processChildren(childSchema, container.getChildNodes(), parentName, definitions, definitionNames,
                    false, stack, oaversion);

            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            final ObjectNode xml = JsonNodeFactory.instance.objectNode();
            xml.put(NAME_KEY, isInput ? INPUT : OUTPUT);
            xml.put(NAMESPACE_KEY, container.getQName().getNamespace().toString());
            childSchema.set(XML_KEY, xml);
            childSchema.put(TITLE_KEY, filename);
            final String discriminator =
                    definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            definitions.set(filename + discriminator, childSchema);

            processTopData(filename, discriminator, definitions, container, oaversion,
                stack.getEffectiveModelContext());
        }
        stack.exit();
    }

    private static ObjectNode processTopData(final String filename, final String discriminator,
            final ObjectNode definitions, final SchemaNode schemaNode, final OAversion oaversion,
            final EffectiveModelContext context) {
        final ObjectNode dataNodeProperties = JsonNodeFactory.instance.objectNode();
        final String name = filename + discriminator;
        final String ref = getAppropriateModelPrefix(oaversion) + name;
        final String topName = filename + TOP;

        if (schemaNode instanceof ListSchemaNode) {
            dataNodeProperties.put(TYPE_KEY, ARRAY_TYPE);
            final ObjectNode items = JsonNodeFactory.instance.objectNode();
            items.put(REF_KEY, ref);
            dataNodeProperties.set(ITEMS_KEY, items);
            dataNodeProperties.put(DESCRIPTION_KEY, schemaNode.getDescription().orElse(""));
        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            dataNodeProperties.put(REF_KEY, ref);
        }

        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set(resolveFullNameFromNode(schemaNode.getQName(), context), dataNodeProperties);
        final ObjectNode finalChildSchema = JsonNodeFactory.instance.objectNode();
        finalChildSchema.put(TYPE_KEY, OBJECT_TYPE);
        finalChildSchema.set(PROPERTIES_KEY, properties);
        finalChildSchema.put(TITLE_KEY, topName);


        definitions.set(topName + discriminator, finalChildSchema);

        return dataNodeProperties;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     * @param module          The module from which the identity stmt will be processed
     * @param definitions     The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames Store for definition names
     */
    private static void processIdentities(final Module module, final ObjectNode definitions,
                                          final DefinitionNames definitionNames, final EffectiveModelContext context) {
        final String moduleName = module.getName();
        final Collection<? extends IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final ObjectNode identityObj = buildIdentityObject(idNode, context);
            final String idName = idNode.getQName().getLocalName();
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(idName));
            final String name = idName + discriminator;
            definitions.set(name, identityObj);
        }
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
                                                final ArrayNode enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private ObjectNode processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
                                                final ObjectNode definitions, final DefinitionNames definitionNames,
                                                final boolean isConfig, final SchemaInferenceStack stack,
                                                final OAversion oaversion) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = schemaNode.getQName().getLocalName();
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            final String nameAsParent = parentName + "_" + localName;
            final ObjectNode properties =
                    processChildren(childSchema, containerChildren, parentName + "_" + localName, definitions,
                            definitionNames, isConfig, stack, oaversion);

            final String nodeName = parentName + (isConfig ? CONFIG : "") + "_" + localName;
            final String parentNameConfigLocalName = parentName + CONFIG + "_" + localName;

            final String description = schemaNode.getDescription().orElse("");
            final String discriminator;

            if (!definitionNames.isListedNode(schemaNode)) {
                final List<String> names = List.of(parentNameConfigLocalName,
                        parentNameConfigLocalName + TOP,
                        nameAsParent,
                        nameAsParent + TOP);
                discriminator = definitionNames.pickDiscriminator(schemaNode, names);
            } else {
                discriminator = definitionNames.getDiscriminator(schemaNode);
            }

            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            childSchema.set(PROPERTIES_KEY, properties);
            childSchema.put(TITLE_KEY, nodeName);
            childSchema.put(DESCRIPTION_KEY, description);

            final String defName = nodeName + discriminator;
            childSchema.set(XML_KEY, buildXmlParameter(schemaNode));
            definitions.set(defName, childSchema);

            return processTopData(nodeName, discriminator, definitions, schemaNode, oaversion,
                stack.getEffectiveModelContext());
        }
        return null;
    }

    /**
     * Processes the nodes.
     */
    private ObjectNode processChildren(
            final ObjectNode parentNode, final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final ObjectNode definitions, final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion) throws IOException {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        for (final DataSchemaNode node : nodes) {
            if (!isConfig || node.isConfiguration()) {
                processChildNode(node, parentName, definitions, definitionNames, isConfig, stack, properties,
                        oaversion, required);
            }
        }
        parentNode.set(PROPERTIES_KEY, properties);
        setRequiredIfNotEmpty(parentNode, required);
        return properties;
    }

    private void processChildNode(
            final DataSchemaNode node, final String parentName, final ObjectNode definitions,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final ObjectNode properties, final OAversion oaversion,
            final ArrayNode required) throws IOException {

        stack.enterSchemaTree(node.getQName());

        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();

        if (node instanceof LeafSchemaNode leaf) {
            processLeafNode(leaf, name, properties, required, stack, definitions, definitionNames, oaversion);

        } else if (node instanceof AnyxmlSchemaNode anyxml) {
            processAnyXMLNode(anyxml, name, properties, required);

        } else if (node instanceof AnydataSchemaNode anydata) {
            processAnydataNode(anydata, name, properties, required);

        } else {

            final ObjectNode property;
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
                property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions,
                        definitionNames, isConfig, stack, oaversion);
                if (!isConfig) {
                    processActionNodeContainer(node, parentName, definitions, definitionNames, stack, oaversion);
                }
            } else if (node instanceof LeafListSchemaNode leafList) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
                property = processLeafListNode(leafList, stack, definitions, definitionNames, oaversion);

            } else if (node instanceof ChoiceSchemaNode choice) {
                if (!choice.getCases().isEmpty()) {
                    CaseSchemaNode caseSchemaNode = choice.getDefaultCase()
                            .orElse(choice.getCases().stream()
                                    .findFirst().get());
                    stack.enterSchemaTree(caseSchemaNode.getQName());
                    for (final DataSchemaNode childNode : caseSchemaNode.getChildNodes()) {
                        processChildNode(childNode, parentName, definitions, definitionNames, isConfig, stack,
                                properties, oaversion, required);
                    }
                    stack.exit();
                }
                property = null;

            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }
            if (property != null) {
                properties.set(name, property);
            }
        }

        stack.exit();
    }

    private ObjectNode processLeafListNode(final LeafListSchemaNode listNode, final SchemaInferenceStack stack,
                                           final ObjectNode definitions, final DefinitionNames definitionNames,
                                           final OAversion oaversion) {
        final ObjectNode props = JsonNodeFactory.instance.objectNode();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        processElementCount(optConstraint, props);

        processTypeDef(listNode.getType(), listNode, itemsVal, stack, definitions, definitionNames, oaversion);
        props.set(ITEMS_KEY, itemsVal);

        if (itemsVal.get(DEFAULT_KEY) != null && props.get(MIN_ITEMS) != null) {
            final ArrayNode listOfExamples = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < props.get(MIN_ITEMS).asInt(); i++) {
                listOfExamples.add(itemsVal.get(DEFAULT_KEY));
            }
            props.put("example", listOfExamples);
        }

        props.put(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return props;
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

    private static void processMandatory(final MandatoryAware node, final String nodeName, final ArrayNode required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private ObjectNode processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
                                       final ObjectNode properties, final ArrayNode required,
                                       final SchemaInferenceStack stack, final ObjectNode definitions,
                                       final DefinitionNames definitionNames, final OAversion oaversion) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            Description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            property.put(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, property, stack, definitions, definitionNames, oaversion);
        properties.set(jsonLeafName, property);
        property.set(XML_KEY, buildXmlParameter(leafNode));
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
        property.set(XML_KEY, buildXmlParameter(leafNode));
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
        property.set(XML_KEY, buildXmlParameter(leafNode));
        processMandatory(leafNode, name, required);
        properties.set(name, property);

        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final ObjectNode property, final SchemaInferenceStack stack,
                                  final ObjectNode definitions, final DefinitionNames definitionNames,
                                  final OAversion oaversion) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(property);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, property);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef, property, definitions,
                    definitionNames, oaversion, stack.getEffectiveModelContext());

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, property, node.getQName().getLocalName());

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processTypeDef(stack.resolveLeafref((LeafrefTypeDefinition) leafTypeDef), node, property,
                stack, definitions, definitionNames, oaversion);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            setDefaultValue(property, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
            jsonType = processNumberType((RangeRestrictedTypeDefinition<?, ?>) leafTypeDef, property);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition) {
            jsonType = processInstanceIdentifierType(node, property, stack.getEffectiveModelContext());
        } else {
            jsonType = STRING_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(property, TYPE_KEY, jsonType);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final Object defaultValue = leafTypeDef.getDefaultValue().get();
                if (defaultValue instanceof String stringDefaultValue) {
                    if (leafTypeDef instanceof BooleanTypeDefinition) {
                        setDefaultValue(property, Boolean.valueOf(stringDefaultValue));
                    } else if (leafTypeDef instanceof DecimalTypeDefinition
                            || leafTypeDef instanceof Uint64TypeDefinition) {
                        setDefaultValue(property, new BigDecimal(stringDefaultValue));
                    } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                        //uint8,16,32 int8,16,32,64
                        if (isHexadecimalOrOctal((RangeRestrictedTypeDefinition<?, ?>)leafTypeDef)) {
                            setDefaultValue(property, stringDefaultValue);
                        } else {
                            setDefaultValue(property, Long.valueOf(stringDefaultValue));
                        }
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

    private static String processBinaryType(final ObjectNode property) {
        property.put(FORMAT_KEY, "byte");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType,
                                          final ObjectNode property) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        final ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        for (final EnumPair enumPair : enumPairs) {
            enumNames.add(new TextNode(enumPair.getName()));
        }

        property.set(ENUM_KEY, enumNames);
        setDefaultValue(property, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef, final ObjectNode property,
                                          final ObjectNode definitions, final DefinitionNames definitionNames,
                                          final OAversion oaversion, final EffectiveModelContext schemaContext) {
        final String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, definitions, definitionNames, schemaContext);
        } else {
            final SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        property.put(REF_KEY, getAppropriateModelPrefix(oaversion) + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
                                              final ObjectNode definitions, final DefinitionNames definitionNames,
                                              final EffectiveModelContext context) {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        final String identityName = idNode.getQName().getLocalName();
        if (!definitionNames.isListedNode(idNode)) {
            final ObjectNode identityObj = buildIdentityObject(idNode, context);
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            final String name = identityName + discriminator;
            definitions.set(name, identityObj);
            return name;
        } else {
            return identityName + definitionNames.getDiscriminator(idNode);
        }
    }

    private static ObjectNode buildIdentityObject(final IdentitySchemaNode idNode,
            final EffectiveModelContext context) {
        final ObjectNode identityObj = JsonNodeFactory.instance.objectNode();
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);

        identityObj.put(TITLE_KEY, identityName);
        identityObj.put(DESCRIPTION_KEY, idNode.getDescription().orElse(""));

        final Collection<? extends IdentitySchemaNode> derivedIds = context.getDerivedIdentities(idNode);

        final ArrayNode enumPayload = JsonNodeFactory.instance.arrayNode();
        enumPayload.add(identityName);
        populateEnumWithDerived(derivedIds, enumPayload, context);
        identityObj.set(ENUM_KEY, enumPayload);
        identityObj.put(TYPE_KEY, STRING_TYPE);
        return identityObj;
    }

    private boolean isImported(final IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(topLevelModule.getQNameModule());
    }

    private static String processBitsType(final BitsTypeDefinition bitsType,
                                          final ObjectNode property) {
        property.put(MIN_ITEMS, 0);
        property.put(UNIQUE_ITEMS_KEY, true);
        final ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        final Collection<? extends Bit> bits = bitsType.getBits();
        for (final Bit bit : bits) {
            enumNames.add(new TextNode(bit.getName()));
        }
        property.set(ENUM_KEY, enumNames);
        property.put(DEFAULT_KEY, enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1));
        return STRING_TYPE;
    }

    private static String processStringType(final TypeDefinition<?> stringType, final ObjectNode property,
                                            final String nodeName) {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        Optional<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraint();
        while (lengthConstraints.isEmpty() && type.getBaseType() != null) {
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
            // Escape special characters to prevent issues inside Generex.
            regex = AUTOMATON_SPECIAL_CHARACTERS.matcher(regex).replaceAll("\\\\$0");
            String defaultValue = "";
            try {
                final Generex generex = new Generex(regex);
                defaultValue = generex.random();
            } catch (IllegalArgumentException ex) {
                LOG.warn("Cannot create example string for type: {} with regex: {}.", stringType.getQName(), regex);
            }
            setDefaultValue(property, defaultValue);
        } else {
            setDefaultValue(property, "Some " + nodeName);
        }
        return STRING_TYPE;
    }

    private static String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,
            final ObjectNode property) {
        final Optional<Number> maybeLower = leafTypeDef.getRangeConstraint()
                .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            maybeLower.ifPresent(number -> setDefaultValue(property, ((Decimal64) number).decimalValue()));
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

    private static boolean isHexadecimalOrOctal(final RangeRestrictedTypeDefinition<?, ?> typeDef) {
        final Optional<?> optDefaultValue = typeDef.getDefaultValue();
        if (optDefaultValue.isPresent()) {
            final String defaultValue = (String) optDefaultValue.get();
            return defaultValue.startsWith("0") || defaultValue.startsWith("-0");
        }
        return false;
    }

    private static String processInstanceIdentifierType(final DataSchemaNode node, final ObjectNode property,
                                                        final EffectiveModelContext schemaContext) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.get().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            container.ifPresent(c -> setDefaultValue(property, String.format("/%s:%s", module.get().getPrefix(),
                    c.getQName().getLocalName())));
        }

        return STRING_TYPE;
    }

    private static String processUnionType(final UnionTypeDefinition unionType) {
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

    private static ObjectNode buildXmlParameter(final SchemaNode node) {
        final ObjectNode xml = JsonNodeFactory.instance.objectNode();
        final QName qName = node.getQName();
        xml.put(NAME_KEY, qName.getLocalName());
        xml.put(NAMESPACE_KEY, qName.getNamespace().toString());
        return xml;
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

    private static void setDefaultValue(final ObjectNode property, final String value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final ObjectNode property, final Integer value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final ObjectNode property, final Long value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final ObjectNode property, final BigDecimal value) {
        property.put(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final ObjectNode property, final Boolean value) {
        property.put(DEFAULT_KEY, value);
    }

}
