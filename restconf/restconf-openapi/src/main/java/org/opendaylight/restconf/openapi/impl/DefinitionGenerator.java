<<<<<<< HEAD   (427c97 Remove synchronization locking)
=======
/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.MODULE_NAME_SUFFIX;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.COMPONENTS_PREFIX;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import dk.brics.automaton.RegExp;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.opendaylight.restconf.openapi.model.Property;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.restconf.openapi.model.Xml;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
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
public final class DefinitionGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefinitionGenerator.class);

    private static final String TYPE_KEY = "type";
    private static final String ARRAY_TYPE = "array";
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
    // Special characters used in Automaton.
    // See https://www.brics.dk/automaton/doc/dk/brics/automaton/RegExp.html
    private static final Pattern AUTOMATON_SPECIAL_CHARACTERS = Pattern.compile("[@&\"<>#~]");
    // Adaptation from YANG regex to Automaton regex
    // See https://github.com/mifmif/Generex/blob/master/src/main/java/com/mifmif/common/regex/Generex.java
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES = Map.of("\\\\d", "[0-9]",
            "\\\\D", "[^0-9]", "\\\\s", "[ \t\n\f\r]", "\\\\S", "[^ \t\n\f\r]",
            "\\\\w", "[a-zA-Z_0-9]", "\\\\W", "[^a-zA-Z_0-9]");

    private DefinitionGenerator() {
        // Hidden on purpose
    }

    /**
     * Creates Json definitions from provided module according to openapi spec.
     *
     * @param module          - Yang module to be converted
     * @param schemaContext   - SchemaContext of all Yang files used by Api Doc
     * @param definitionNames - Store for definition names
     * @return {@link Map} containing data used for creating examples and definitions in OpenAPI documentation
     * @throws IOException if I/O operation fails
     */
    private static Map<String, Schema> convertToSchemas(final Module module, final EffectiveModelContext schemaContext,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames) throws IOException {
        processContainersAndLists(module, definitions, definitionNames, schemaContext);
        processRPCs(module, definitions, definitionNames, schemaContext);

        return definitions;
    }

    public static Map<String, Schema> convertToSchemas(final Module module, final EffectiveModelContext schemaContext,
            final DefinitionNames definitionNames, final boolean isForSingleModule)
            throws IOException {
        final Map<String, Schema> definitions = new HashMap<>();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(module.getName() + MODULE_NAME_SUFFIX);
        }
        return convertToSchemas(module, schemaContext, definitions, definitionNames);
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

    private static void processContainersAndLists(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext)  throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                    stack, module, true);
                processActionNodeContainer(childNode, moduleName, definitions, definitionNames, stack, module);
            }
            stack.exit();
        }
    }

    private static void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final Module module) throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, definitions, definitionNames, stack, module);
            stack.exit();
        }
    }

    private static void processRPCs(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, definitions, definitionNames, stack, module);
            stack.exit();
        }
    }

    private static void processOperations(final OperationDefinition operationDef, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final Module module) throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitions,
                definitionNames, stack, module);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitions,
                definitionNames, stack, module);
    }

    private static void processOperationInputOutput(final ContainerLike container, final String operationName,
            final String parentName, final boolean isInput, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack,
            final Module module) throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final Schema.Builder childSchemaBuilder = new Schema.Builder()
                .title(filename)
                .type(OBJECT_TYPE)
                .xml(new Xml(isInput ? INPUT : OUTPUT, container.getQName().getNamespace().toString(), null));
            processChildren(childSchemaBuilder, container.getChildNodes(), parentName, definitions, definitionNames,
                stack, module, false);
            final String discriminator =
                definitionNames.pickDiscriminator(container, List.of(filename));
            definitions.put(filename + discriminator, childSchemaBuilder.build());
        }
        stack.exit();
    }

    private static Property processRef(final String filename, final String discriminator,
            final SchemaNode schemaNode) {
        final Property.Builder dataNodeProperties = new Property.Builder();
        final String name = filename + discriminator;
        final String ref = COMPONENTS_PREFIX + name;

        if (schemaNode instanceof ListSchemaNode) {
            dataNodeProperties.type(ARRAY_TYPE);
            final Property items = new Property.Builder().ref(ref).build();
            dataNodeProperties.items(items);
            dataNodeProperties.description(schemaNode.getDescription().orElse(""));
        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            dataNodeProperties.ref(ref);
        }

        return dataNodeProperties.build();
    }

    private static Property processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final Module module, final boolean isParentConfig) throws IOException {
        final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
        final SchemaNode schemaNode = (SchemaNode) dataNode;
        final String localName = schemaNode.getQName().getLocalName();
        final String nodeName = parentName + "_" + localName;
        final Schema.Builder childSchemaBuilder = new Schema.Builder()
            .type(OBJECT_TYPE)
            .title(nodeName)
            .description(schemaNode.getDescription().orElse(""));
        final boolean isConfig = ((DataSchemaNode) dataNode).isConfiguration() && isParentConfig;
        childSchemaBuilder.properties(processChildren(childSchemaBuilder, containerChildren,
            parentName + "_" + localName, definitions, definitionNames, stack, module, isConfig));

        final String discriminator;
        if (!definitionNames.isListedNode(schemaNode)) {
            final String parentNameConfigLocalName = parentName + "_" + localName;
            final List<String> names = List.of(parentNameConfigLocalName);
            discriminator = definitionNames.pickDiscriminator(schemaNode, names);
        } else {
            discriminator = definitionNames.getDiscriminator(schemaNode);
        }

        final String defName = nodeName + discriminator;
        childSchemaBuilder.xml(buildXmlParameter(schemaNode));
        definitions.put(defName, childSchemaBuilder.build());

        return processRef(nodeName, discriminator, schemaNode);
    }

    /**
     * Processes the nodes.
     */
    private static Map<String, Property> processChildren(final Schema.Builder parentNodeBuilder,
            final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final Module module, final boolean isParentConfig) throws IOException {
        final Map<String, Property> properties = new HashMap<>();
        final List<String> required = new ArrayList<>();
        for (final DataSchemaNode node : nodes) {
            if (node instanceof ChoiceSchemaNode choice) {
                stack.enterSchemaTree(node.getQName());
                final boolean isConfig = isParentConfig && node.isConfiguration();
                final Map<String, Property> choiceProperties = processChoiceNodeRecursively(parentName,
                    definitions, definitionNames, isConfig, stack, required, choice, module);
                properties.putAll(choiceProperties);
                stack.exit();
            } else {
                final Property property = processChildNode(node, parentName, definitions, definitionNames,
                    stack, required, module, isParentConfig);
                if (property != null) {
                    properties.put(node.getQName().getLocalName(), property);
                }
            }
        }
        parentNodeBuilder.properties(properties).required(required.size() > 0 ? required : null);
        return properties;
    }

    private static Map<String, Property> processChoiceNodeRecursively(final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final List<String> required, final ChoiceSchemaNode choice,
            final Module module) throws IOException {
        if (!choice.getCases().isEmpty()) {
            final var properties = new HashMap<String, Property>();
            final var caseSchemaNode = choice.getDefaultCase().orElse(choice.getCases().stream()
                .findFirst().orElseThrow());
            stack.enterSchemaTree(caseSchemaNode.getQName());
            for (final var childNode : caseSchemaNode.getChildNodes()) {
                if (childNode instanceof ChoiceSchemaNode childChoice) {
                    final var isChildConfig = isConfig && childNode.isConfiguration();
                    stack.enterSchemaTree(childNode.getQName());
                    final var childProperties = processChoiceNodeRecursively(parentName, definitions, definitionNames,
                        isChildConfig, stack, required, childChoice, module);
                    properties.putAll(childProperties);
                    stack.exit();
                } else {
                    final var property = processChildNode(childNode, parentName, definitions, definitionNames, stack,
                        required, module, isConfig);
                    if (property != null) {
                        properties.put(childNode.getQName().getLocalName(), property);
                    }
                }
            }
            stack.exit();
            return properties;
        }
        return Map.of();
    }

    private static Property processChildNode(final DataSchemaNode node, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final List<String> required, final Module module,
            final boolean isParentConfig) throws IOException {
        final XMLNamespace parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        stack.enterSchemaTree(node.getQName());
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();
        /*
            If the parent is operational, then current node is also operational and should be added as a child
            even if node.isConfiguration()==true.
            If the parent is configuration, then current node should be added as a child only if
            node.isConfiguration()==true.
        */
        final boolean shouldBeAddedAsChild = !isParentConfig || node.isConfiguration();
        Property property = null;
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            final Property dataNodeContainer = processDataNodeContainer((DataNodeContainer) node, parentName,
                definitions, definitionNames, stack, module, isParentConfig);
            if (shouldBeAddedAsChild) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
                property = dataNodeContainer;
            }
            processActionNodeContainer(node, parentName, definitions, definitionNames, stack, module);
        } else if (shouldBeAddedAsChild) {
            if (node instanceof LeafSchemaNode leaf) {
                property = processLeafNode(leaf, name, required, stack, parentNamespace);
            } else if (node instanceof AnyxmlSchemaNode || node instanceof AnydataSchemaNode) {
                property = processUnknownDataSchemaNode(node, name, required, parentNamespace);
            } else if (node instanceof LeafListSchemaNode leafList) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
                property = processLeafListNode(leafList, stack);
            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }
        }
        stack.exit();
        return property;
    }

    private static Property processLeafListNode(final LeafListSchemaNode listNode,
            final SchemaInferenceStack stack) {
        final Property.Builder props = new Property.Builder();
        props.type(ARRAY_TYPE);

        final Property.Builder itemsVal = new Property.Builder();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        optConstraint.ifPresent(elementCountConstraint -> processElementCount(elementCountConstraint, props));

        processTypeDef(listNode.getType(), listNode, itemsVal, stack);

        props.items(itemsVal.build());
        props.description(listNode.getDescription().orElse(""));

        return props.build();
    }

    private static void processElementCount(final ElementCountConstraint constraint, final Property.Builder props) {
        final Integer minElements = constraint.getMinElements();
        if (minElements != null) {
            props.minItems(minElements);
        }
        final Integer maxElements = constraint.getMaxElements();
        if (maxElements != null) {
            props.maxItems(maxElements);
        }
    }

    private static void processMandatory(final MandatoryAware node, final String nodeName,
            final List<String> required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private static Property processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
            final List<String> required, final SchemaInferenceStack stack, final XMLNamespace parentNamespace) {
        final Property.Builder property = new Property.Builder();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.description(leafDescription);

        processTypeDef(leafNode.getType(), leafNode, property, stack);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            property.xml(buildXmlParameter(leafNode));
        }
        processMandatory(leafNode, jsonLeafName, required);
        return property.build();
    }

    private static Property processUnknownDataSchemaNode(final DataSchemaNode leafNode, final String name,
            final List<String> required, final XMLNamespace parentNamespace) {
        assert (leafNode instanceof AnydataSchemaNode || leafNode instanceof AnyxmlSchemaNode);

        final Property.Builder property = new Property.Builder();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.description(leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        property.example(String.format("<%s> ... </%s>", localName, localName));
        property.type(STRING_TYPE);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            property.xml(buildXmlParameter(leafNode));
        }
        processMandatory((MandatoryAware) leafNode, name, required);
        return property.build();
    }

    private static String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
            final Property.Builder property, final SchemaInferenceStack stack) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition binaryType) {
            jsonType = processBinaryType(binaryType, property);
        } else if (leafTypeDef instanceof BitsTypeDefinition bitsType) {
            jsonType = processBitsType(bitsType, property);
        } else if (leafTypeDef instanceof EnumTypeDefinition enumType) {
            jsonType = processEnumType(enumType, property);
        } else if (leafTypeDef instanceof IdentityrefTypeDefinition identityrefType) {
            jsonType = processIdentityRefType(identityrefType, property, stack.getEffectiveModelContext());
        } else if (leafTypeDef instanceof StringTypeDefinition stringType) {
            jsonType = processStringType(stringType, property, node.getQName().getLocalName());
        } else if (leafTypeDef instanceof UnionTypeDefinition unionType) {
            jsonType = processTypeDef(unionType.getTypes().iterator().next(), node, property, stack);
        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition leafrefType) {
            return processTypeDef(stack.resolveLeafref(leafrefType), node, property, stack);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            leafTypeDef.getDefaultValue().ifPresent(v -> property.defaultValue(Boolean.valueOf((String) v)));
            property.example(true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
            jsonType = processNumberType(rangeRestrictedType, property);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition instanceIdentifierType) {
            jsonType = processInstanceIdentifierType(instanceIdentifierType, node, property,
                stack.getEffectiveModelContext());
        } else {
            jsonType = STRING_TYPE;
        }
        if (TYPE_KEY != null && jsonType != null) {
            property.type(jsonType);
        }

        if (leafTypeDef.getDefaultValue().isPresent()) {
            final Object defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
            if (defaultValue instanceof String stringDefaultValue) {
                if (leafTypeDef instanceof BooleanTypeDefinition) {
                    property.defaultValue(Boolean.valueOf(stringDefaultValue));
                } else if (leafTypeDef instanceof DecimalTypeDefinition
                        || leafTypeDef instanceof Uint64TypeDefinition) {
                    property.defaultValue(new BigDecimal(stringDefaultValue));
                } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
                    //uint8,16,32 int8,16,32,64
                    if (isHexadecimalOrOctal(rangeRestrictedType)) {
                        property.defaultValue(stringDefaultValue);
                    } else {
                        property.defaultValue(Long.valueOf(stringDefaultValue));
                    }
                } else {
                    property.defaultValue(stringDefaultValue);
                }
            } else {
                //we should never get here. getDefaultValue always gives us string
                property.defaultValue(defaultValue.toString());
            }
        }
        return jsonType;
    }

    private static String processBinaryType(final BinaryTypeDefinition definition, final Property.Builder property) {
        definition.getDefaultValue().ifPresent(property::defaultValue);
        property.format("byte");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType, final Property.Builder property) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        final List<String> enumNames = enumPairs.stream()
            .map(EnumPair::getName)
            .toList();

        property.enums(enumNames);
        enumLeafType.getDefaultValue().ifPresent(property::defaultValue);
        property.example(enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private static String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
            final Property.Builder property, final EffectiveModelContext schemaContext) {
        final IdentitySchemaNode node = leafTypeDef.getIdentities().iterator().next();
        property.example(node.getQName().getLocalName());
        final Collection<? extends IdentitySchemaNode> derivedIds = schemaContext.getDerivedIdentities(node);
        final List<String> enumPayload = new ArrayList<>();
        enumPayload.add(node.getQName().getLocalName());
        populateEnumWithDerived(derivedIds, enumPayload, schemaContext);
        final List<String> schemaEnum = new ArrayList<>(enumPayload);
        property.enums(schemaEnum);
        return STRING_TYPE;
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final List<String> enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private static String processBitsType(final BitsTypeDefinition bitsType, final Property.Builder property) {
        property.minItems(0);
        property.uniqueItems(true);
        final Collection<? extends Bit> bits = bitsType.getBits();
        final List<String> enumNames = bits.stream()
            .map(Bit::getName)
            .toList();
        property.enums(enumNames);
        property.defaultValue(enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1));
        bitsType.getDefaultValue().ifPresent(property::defaultValue);
        return STRING_TYPE;
    }

    private static String processStringType(final StringTypeDefinition stringType, final Property.Builder property,
            final String nodeName) {
        var type = stringType;
        while (type.getLengthConstraint().isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
        }

        type.getLengthConstraint().ifPresent(constraint -> {
            final Range<Integer> range = constraint.getAllowedRanges().span();
            property.minLength(range.lowerEndpoint());
            property.maxLength(range.upperEndpoint());
        });

        if (type.getPatternConstraints().iterator().hasNext()) {
            final PatternConstraint pattern = type.getPatternConstraints().iterator().next();
            String regex = pattern.getRegularExpressionString();
            // Escape special characters to prevent issues inside Automaton.
            regex = AUTOMATON_SPECIAL_CHARACTERS.matcher(regex).replaceAll("\\\\$0");
            for (final var charClass : PREDEFINED_CHARACTER_CLASSES.entrySet()) {
                regex = regex.replaceAll(charClass.getKey(), charClass.getValue());
            }
            String defaultValue = "";
            try {
                final RegExp regExp = new RegExp(regex);
                defaultValue = regExp.toAutomaton().getShortestExample(true);
            } catch (IllegalArgumentException ex) {
                LOG.warn("Cannot create example string for type: {} with regex: {}.", stringType.getQName(), regex);
            }
            property.example(defaultValue);
        } else {
            property.example("Some " + nodeName);
        }

        stringType.getDefaultValue().ifPresent(property::defaultValue);
        return STRING_TYPE;
    }

    private static String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,
            final Property.Builder property) {
        final Optional<Number> maybeLower = leafTypeDef.getRangeConstraint()
                .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            leafTypeDef.getDefaultValue().ifPresent(number -> property.defaultValue(Decimal64.valueOf((String) number)
                .decimalValue()));
            maybeLower.ifPresent(number -> property.example(((Decimal64) number).decimalValue()));
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
                || leafTypeDef instanceof Uint16TypeDefinition
                || leafTypeDef instanceof Int8TypeDefinition
                || leafTypeDef instanceof Int16TypeDefinition
                || leafTypeDef instanceof Int32TypeDefinition) {

            property.format(INT32_FORMAT);
            leafTypeDef.getDefaultValue().ifPresent(number -> property.defaultValue(Integer.valueOf((String) number)));
            maybeLower.ifPresent(number -> property.example(Integer.valueOf(number.toString())));
        } else if (leafTypeDef instanceof Uint32TypeDefinition
                || leafTypeDef instanceof Int64TypeDefinition) {

            property.format(INT64_FORMAT);
            leafTypeDef.getDefaultValue().ifPresent(number -> property.defaultValue(Long.valueOf((String) number)));
            maybeLower.ifPresent(number -> property.example(Long.valueOf(number.toString())));
        } else {
            //uint64
            leafTypeDef.getDefaultValue().ifPresent(number -> property.defaultValue(new BigInteger((String) number)));
            property.example(0);
        }
        return INTEGER_TYPE;
    }

    private static boolean isHexadecimalOrOctal(final RangeRestrictedTypeDefinition<?, ?> typeDef) {
        final Optional<?> optDefaultValue = typeDef.getDefaultValue();
        if (optDefaultValue.isPresent()) {
            final String defaultValue = (String) optDefaultValue.orElseThrow();
            return defaultValue.startsWith("0") || defaultValue.startsWith("-0");
        }
        return false;
    }

    private static String processInstanceIdentifierType(final InstanceIdentifierTypeDefinition iidType,
            final DataSchemaNode node, final Property.Builder property, final EffectiveModelContext schemaContext) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            container.ifPresent(c -> property.example(String.format("/%s:%s", module.orElseThrow().getPrefix(),
                c.getQName().getLocalName())));
        }
        // set default value
        iidType.getDefaultValue().ifPresent(property::defaultValue);
        return STRING_TYPE;
    }

    private static Xml buildXmlParameter(final SchemaNode node) {
        final QName qName = node.getQName();
        return new Xml(qName.getLocalName(), qName.getNamespace().toString(), null);
    }
}
>>>>>>> CHANGE (1f6754 Fix module's root POST request payload)
