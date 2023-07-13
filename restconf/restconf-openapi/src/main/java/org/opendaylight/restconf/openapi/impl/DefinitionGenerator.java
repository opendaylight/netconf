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
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.NAME_KEY;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.TOP;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.XML_KEY;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import dk.brics.automaton.RegExp;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.opendaylight.restconf.openapi.model.Schema;
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
public class DefinitionGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefinitionGenerator.class);

    private static final String UNIQUE_ITEMS_KEY = "uniqueItems";
    private static final String MAX_ITEMS = "maxItems";
    private static final String MIN_ITEMS = "minItems";
    private static final String MAX_LENGTH_KEY = "maxLength";
    private static final String MIN_LENGTH_KEY = "minLength";
    private static final String REF_KEY = "$ref";
    private static final String ITEMS_KEY = "items";
    private static final String TYPE_KEY = "type";
    private static final String DESCRIPTION_KEY = "description";
    private static final String ARRAY_TYPE = "array";
    private static final String ENUM_KEY = "enum";
    private static final String TITLE_KEY = "title";
    private static final String DEFAULT_KEY = "default";
    private static final String EXAMPLE_KEY = "example";
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
    // Special characters used in Automaton.
    // See https://www.brics.dk/automaton/doc/dk/brics/automaton/RegExp.html
    private static final Pattern AUTOMATON_SPECIAL_CHARACTERS = Pattern.compile("[@&\"<>#~]");
    // Adaptation from YANG regex to Automaton regex
    // See https://github.com/mifmif/Generex/blob/master/src/main/java/com/mifmif/common/regex/Generex.java
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES = Map.of("\\\\d", "[0-9]",
            "\\\\D", "[^0-9]", "\\\\s", "[ \t\n\f\r]", "\\\\S", "[^ \t\n\f\r]",
            "\\\\w", "[a-zA-Z_0-9]", "\\\\W", "[^a-zA-Z_0-9]");

    private Module topLevelModule;

    public DefinitionGenerator() {
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
    public Map<String, Schema> convertToSchemas(final Module module, final EffectiveModelContext schemaContext,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final boolean isForSingleModule) throws IOException {
        topLevelModule = module;

        processIdentities(module, definitions, definitionNames, schemaContext);
        processContainersAndLists(module, definitions, definitionNames, schemaContext);
        processRPCs(module, definitions, definitionNames, schemaContext);

        if (isForSingleModule) {
            processModule(module, definitions, definitionNames, schemaContext);
        }

        return definitions;
    }

    public Map<String, Schema> convertToSchemas(final Module module, final EffectiveModelContext schemaContext,
            final DefinitionNames definitionNames, final boolean isForSingleModule)
            throws IOException {
        final Map<String, Schema> definitions = new HashMap<>();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(module.getName() + MODULE_NAME_SUFFIX);
        }
        return convertToSchemas(module, schemaContext, definitions, definitionNames, isForSingleModule);
    }

    private void processModule(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext) {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        final String moduleName = module.getName();
        final String definitionName = moduleName + MODULE_NAME_SUFFIX;
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode node : module.getChildNodes()) {
            stack.enterSchemaTree(node.getQName());
            final String localName = node.getQName().getLocalName();
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    if (isSchemaNodeMandatory(node)) {
                        required.add(localName);
                    }
                    for (final DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        final ObjectNode childNodeProperties = JsonNodeFactory.instance.objectNode();

                        final String ref = COMPONENTS_PREFIX
                                + moduleName
                                + "_" + localName
                                + definitionNames.getDiscriminator(node);

                        if (node instanceof ListSchemaNode) {
                            childNodeProperties.put(TYPE_KEY, ARRAY_TYPE);
                            final ObjectNode items = JsonNodeFactory.instance.objectNode();
                            items.put(REF_KEY, ref);
                            childNodeProperties.set(ITEMS_KEY, items);
                            childNodeProperties.put(DESCRIPTION_KEY, childNode.getDescription().orElse(""));
                            childNodeProperties.put(TITLE_KEY, localName);
                        } else {
                         /*
                            Description can't be added, because nothing allowed alongside $ref.
                            allOf is not an option, because ServiceNow can't parse it.
                          */
                            childNodeProperties.put(REF_KEY, ref);
                        }
                        //add module name prefix to property name, when ServiceNow can process colons
                        properties.set(localName, childNodeProperties);
                    }
                } else if (node instanceof LeafSchemaNode) {
                    /*
                        Add module name prefix to property name, when ServiceNow can process colons(second parameter
                        of processLeafNode).
                     */
                    final ObjectNode leafNode = processLeafNode((LeafSchemaNode) node, localName, required, stack,
                            definitions, definitionNames, module.getNamespace());
                    properties.set(localName, leafNode);
                }
            }
            stack.exit();
        }
        final Schema.Builder definitionBuilder = new Schema.Builder()
            .title(definitionName)
            .type(OBJECT_TYPE)
            .properties(properties)
            .description(module.getDescription().orElse(""))
            .required(required.size() > 0 ? required : null);

        definitions.put(definitionName, definitionBuilder.build());
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

    private void processContainersAndLists(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext)  throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                        stack);
                }
                processActionNodeContainer(childNode, moduleName, definitions, definitionNames, stack);
            }
            stack.exit();
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack) throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, definitions, definitionNames, stack);
            stack.exit();
        }
    }

    private void processRPCs(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, definitions, definitionNames, stack);
            stack.exit();
        }
    }

    private void processOperations(final OperationDefinition operationDef, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack) throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitions,
                definitionNames, stack);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitions,
                definitionNames, stack);
    }

    private void processOperationInputOutput(final ContainerLike container, final String operationName,
            final String parentName, final boolean isInput, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack)
            throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final Schema.Builder childSchemaBuilder = new Schema.Builder()
                .title(filename)
                .type(OBJECT_TYPE)
                .xml(JsonNodeFactory.instance.objectNode().put(NAME_KEY, isInput ? INPUT : OUTPUT));
            processChildren(childSchemaBuilder, container.getChildNodes(), parentName, definitions, definitionNames,
                stack);
            final String discriminator =
                definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            definitions.put(filename + discriminator, childSchemaBuilder.build());
            processTopData(filename, discriminator, definitions, container);
        }
        stack.exit();
    }

    private static ObjectNode processTopData(final String filename, final String discriminator,
            final Map<String, Schema> definitions, final SchemaNode schemaNode) {
        final ObjectNode dataNodeProperties = JsonNodeFactory.instance.objectNode();
        final String name = filename + discriminator;
        final String ref = COMPONENTS_PREFIX + name;
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
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        properties.set(schemaNode.getQName().getLocalName(), dataNodeProperties);
        final var schema = new Schema.Builder()
            .type(OBJECT_TYPE)
            .properties(properties)
            .title(topName)
            .build();

        definitions.put(topName + discriminator, schema);

        return dataNodeProperties;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     * @param module          The module from which the identity stmt will be processed
     * @param definitions     The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames Store for definition names
     */
    private static void processIdentities(final Module module, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final EffectiveModelContext context) {
        final String moduleName = module.getName();
        final Collection<? extends IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final Schema identityObj = buildIdentityObject(idNode, context);
            final String idName = idNode.getQName().getLocalName();
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(idName));
            final String name = idName + discriminator;
            definitions.put(name, identityObj);
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
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack) throws IOException {
        final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
        final SchemaNode schemaNode = (SchemaNode) dataNode;
        final String localName = schemaNode.getQName().getLocalName();
        final String nodeName = parentName + "_" + localName;
        final Schema.Builder childSchemaBuilder = new Schema.Builder()
            .type(OBJECT_TYPE)
            .title(nodeName)
            .description(schemaNode.getDescription().orElse(""));

        childSchemaBuilder.properties(processChildren(childSchemaBuilder, containerChildren,
            parentName + "_" + localName, definitions, definitionNames, stack));

        final String discriminator;
        if (!definitionNames.isListedNode(schemaNode)) {
            final String parentNameConfigLocalName = parentName + "_" + localName;
            final String nameAsParent = parentName + "_" + localName;
            final List<String> names = List.of(parentNameConfigLocalName, parentNameConfigLocalName + TOP,
                nameAsParent, nameAsParent + TOP);
            discriminator = definitionNames.pickDiscriminator(schemaNode, names);
        } else {
            discriminator = definitionNames.getDiscriminator(schemaNode);
        }

        final String defName = nodeName + discriminator;
        childSchemaBuilder.xml(buildXmlParameter(schemaNode));
        definitions.put(defName, childSchemaBuilder.build());

        return processTopData(nodeName, discriminator, definitions, schemaNode);
    }

    /**
     * Processes the nodes.
     */
    private ObjectNode processChildren(final Schema.Builder parentNodeBuilder,
            final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack) throws IOException {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        for (final DataSchemaNode node : nodes) {
            if (node.isConfiguration()) {
                if (node instanceof ChoiceSchemaNode choice) {
                    stack.enterSchemaTree(node.getQName());
                    final Map<String, ObjectNode> choiceProperties = processChoiceNodeRecursively(parentName,
                        definitions, definitionNames, stack, required, choice);
                    choiceProperties.forEach(properties::set);
                    stack.exit();
                } else {
                    final ObjectNode property = processChildNode(node, parentName, definitions, definitionNames,
                        stack, required);
                    properties.set(node.getQName().getLocalName(), property);
                }
            }
        }
        parentNodeBuilder.properties(properties).required(required.size() > 0 ? required : null);
        return properties;
    }

    private Map<String, ObjectNode> processChoiceNodeRecursively(final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final ArrayNode required, final ChoiceSchemaNode choice)
            throws IOException {
        if (!choice.getCases().isEmpty()) {
            final var properties = new HashMap<String, ObjectNode>();
            final var caseSchemaNode = choice.getDefaultCase().orElse(choice.getCases().stream()
                .findFirst().orElseThrow());
            stack.enterSchemaTree(caseSchemaNode.getQName());
            for (final var childNode : caseSchemaNode.getChildNodes()) {
                if (childNode instanceof ChoiceSchemaNode childChoice) {
                    stack.enterSchemaTree(childNode.getQName());
                    final var childProperties = processChoiceNodeRecursively(parentName, definitions, definitionNames,
                        stack, required, childChoice);
                    properties.putAll(childProperties);
                    stack.exit();
                } else {
                    final var property = processChildNode(childNode, parentName, definitions, definitionNames, stack,
                        required);
                    properties.put(childNode.getQName().getLocalName(), property);
                }
            }
            stack.exit();
            return properties;
        }
        return Map.of();
    }

    private ObjectNode processChildNode(final DataSchemaNode node, final String parentName,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final ArrayNode required) throws IOException {
        final XMLNamespace parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        stack.enterSchemaTree(node.getQName());
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();
        final ObjectNode property;
        if (node instanceof LeafSchemaNode leaf) {
            property = processLeafNode(leaf, name, required, stack, definitions, definitionNames, parentNamespace);
        } else if (node instanceof AnyxmlSchemaNode anyxml) {
            property = processAnyXMLNode(anyxml, name, required, parentNamespace);
        } else if (node instanceof AnydataSchemaNode anydata) {
            property = processAnydataNode(anydata, name, required, parentNamespace);
        } else if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            if (isSchemaNodeMandatory(node)) {
                required.add(name);
            }
            property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions, definitionNames,
                stack);
            processActionNodeContainer(node, parentName, definitions, definitionNames, stack);
        } else if (node instanceof LeafListSchemaNode leafList) {
            if (isSchemaNodeMandatory(node)) {
                required.add(name);
            }
            property = processLeafListNode(leafList, stack, definitions, definitionNames);
        } else {
            throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
        }
        stack.exit();
        return property;
    }

    private ObjectNode processLeafListNode(final LeafListSchemaNode listNode, final SchemaInferenceStack stack,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames) {
        final ObjectNode props = JsonNodeFactory.instance.objectNode();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        optConstraint.ifPresent(elementCountConstraint -> processElementCount(elementCountConstraint, props));

        processTypeDef(listNode.getType(), listNode, itemsVal, stack, definitions, definitionNames);
        props.set(ITEMS_KEY, itemsVal);

        props.put(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return props;
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

    private static void processMandatory(final MandatoryAware node, final String nodeName, final ArrayNode required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private ObjectNode processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
            final ArrayNode required, final SchemaInferenceStack stack, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames, final XMLNamespace parentNamespace) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            Description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            property.put(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, property, stack, definitions, definitionNames);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            property.set(XML_KEY, buildXmlParameter(leafNode));
        }
        processMandatory(leafNode, jsonLeafName, required);
        return property;
    }

    private static ObjectNode processAnydataNode(final AnydataSchemaNode leafNode, final String name,
            final ArrayNode required, final XMLNamespace parentNamespace) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.put(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setExampleValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            property.set(XML_KEY, buildXmlParameter(leafNode));
        }
        processMandatory(leafNode, name, required);
        return property;
    }

    private static ObjectNode processAnyXMLNode(final AnyxmlSchemaNode leafNode, final String name,
            final ArrayNode required, final XMLNamespace parentNamespace) {
        final ObjectNode property = JsonNodeFactory.instance.objectNode();

        final String leafDescription = leafNode.getDescription().orElse("");
        property.put(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setExampleValue(property, String.format("<%s> ... </%s>", localName, localName));
        property.put(TYPE_KEY, STRING_TYPE);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            property.set(XML_KEY, buildXmlParameter(leafNode));
        }
        processMandatory(leafNode, name, required);
        return property;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
            final ObjectNode property, final SchemaInferenceStack stack, final Map<String, Schema> definitions,
            final DefinitionNames definitionNames) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition binaryType) {
            jsonType = processBinaryType(binaryType, property);
        } else if (leafTypeDef instanceof BitsTypeDefinition bitsType) {
            jsonType = processBitsType(bitsType, property);
        } else if (leafTypeDef instanceof EnumTypeDefinition enumType) {
            jsonType = processEnumType(enumType, property);
        } else if (leafTypeDef instanceof IdentityrefTypeDefinition identityrefType) {
            jsonType = processIdentityRefType(identityrefType, property, definitions,
                    definitionNames, stack.getEffectiveModelContext());
        } else if (leafTypeDef instanceof StringTypeDefinition stringType) {
            jsonType = processStringType(stringType, property, node.getQName().getLocalName());
        } else if (leafTypeDef instanceof UnionTypeDefinition unionType) {
            jsonType = processUnionType(unionType, property, node.getQName().getLocalName());
        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition leafrefType) {
            return processTypeDef(stack.resolveLeafref(leafrefType), node, property,
                stack, definitions, definitionNames);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            leafTypeDef.getDefaultValue().ifPresent(v -> setDefaultValue(property, Boolean.valueOf((String) v)));
            setExampleValue(property, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
            jsonType = processNumberType(rangeRestrictedType, property);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition) {
            jsonType = processInstanceIdentifierType(node, property, stack.getEffectiveModelContext());
        } else {
            jsonType = STRING_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            if (TYPE_KEY != null && jsonType != null) {
                property.put(TYPE_KEY, jsonType);
            }
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final Object defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
                if (defaultValue instanceof String stringDefaultValue) {
                    if (leafTypeDef instanceof BooleanTypeDefinition) {
                        setDefaultValue(property, Boolean.valueOf(stringDefaultValue));
                    } else if (leafTypeDef instanceof DecimalTypeDefinition
                            || leafTypeDef instanceof Uint64TypeDefinition) {
                        setDefaultValue(property, new BigDecimal(stringDefaultValue));
                    } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
                        //uint8,16,32 int8,16,32,64
                        if (isHexadecimalOrOctal(rangeRestrictedType)) {
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

    private static String processBinaryType(final BinaryTypeDefinition definition, final ObjectNode property) {
        definition.getDefaultValue().ifPresent(v -> setDefaultValue(property, ((String) v)));
        property.put(FORMAT_KEY, "byte");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType, final ObjectNode property) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        final ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        for (final EnumPair enumPair : enumPairs) {
            enumNames.add(new TextNode(enumPair.getName()));
        }

        property.set(ENUM_KEY, enumNames);
        enumLeafType.getDefaultValue().ifPresent(v -> setDefaultValue(property, ((String) v)));
        setExampleValue(property, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef, final ObjectNode property,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final EffectiveModelContext schemaContext) {
        final String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, definitions, definitionNames, schemaContext);
        } else {
            final SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        property.put(REF_KEY, COMPONENTS_PREFIX + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
            final Map<String, Schema> definitions, final DefinitionNames definitionNames,
            final EffectiveModelContext context) {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        final String identityName = idNode.getQName().getLocalName();
        if (!definitionNames.isListedNode(idNode)) {
            final Schema identityObj = buildIdentityObject(idNode, context);
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            final String name = identityName + discriminator;
            definitions.put(name, identityObj);
            return name;
        } else {
            return identityName + definitionNames.getDiscriminator(idNode);
        }
    }

    private static Schema buildIdentityObject(final IdentitySchemaNode idNode, final EffectiveModelContext context) {
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);

        final Collection<? extends IdentitySchemaNode> derivedIds = context.getDerivedIdentities(idNode);
        final ArrayNode enumPayload = JsonNodeFactory.instance.arrayNode();
        enumPayload.add(identityName);
        populateEnumWithDerived(derivedIds, enumPayload, context);

        return new Schema.Builder()
            .title(identityName)
            .description(idNode.getDescription().orElse(""))
            .schemaEnum(enumPayload)
            .type(STRING_TYPE)
            .build();
    }

    private boolean isImported(final IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(topLevelModule.getQNameModule());
    }

    private static String processBitsType(final BitsTypeDefinition bitsType, final ObjectNode property) {
        property.put(MIN_ITEMS, 0);
        property.put(UNIQUE_ITEMS_KEY, true);
        final ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        final Collection<? extends Bit> bits = bitsType.getBits();
        for (final Bit bit : bits) {
            enumNames.add(new TextNode(bit.getName()));
        }
        property.set(ENUM_KEY, enumNames);
        property.put(DEFAULT_KEY, enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1));
        bitsType.getDefaultValue().ifPresent(v -> setDefaultValue(property, (String) v));
        return STRING_TYPE;
    }

    private static String processStringType(final StringTypeDefinition stringType, final ObjectNode property,
            final String nodeName) {
        var type = stringType;
        while (type.getLengthConstraint().isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
        }

        type.getLengthConstraint().ifPresent(constraint -> {
            final Range<Integer> range = constraint.getAllowedRanges().span();
            property.put(MIN_LENGTH_KEY, range.lowerEndpoint());
            property.put(MAX_LENGTH_KEY, range.upperEndpoint());
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
            setExampleValue(property, defaultValue);
        } else {
            setExampleValue(property, "Some " + nodeName);
        }

        stringType.getDefaultValue().ifPresent(v -> setDefaultValue(property, (String) v));
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
            maybeLower.ifPresent(number -> setExampleValue(property, ((Decimal64) number).decimalValue()));
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
                || leafTypeDef instanceof Uint16TypeDefinition
                || leafTypeDef instanceof Int8TypeDefinition
                || leafTypeDef instanceof Int16TypeDefinition
                || leafTypeDef instanceof Int32TypeDefinition) {

            property.put(FORMAT_KEY, INT32_FORMAT);
            maybeLower.ifPresent(number -> setExampleValue(property, Integer.valueOf(number.toString())));
        } else if (leafTypeDef instanceof Uint32TypeDefinition
                || leafTypeDef instanceof Int64TypeDefinition) {

            property.put(FORMAT_KEY, INT64_FORMAT);
            maybeLower.ifPresent(number -> setExampleValue(property, Long.valueOf(number.toString())));
        } else {
            //uint64
            setExampleValue(property, 0);
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

    private static String processInstanceIdentifierType(final DataSchemaNode node, final ObjectNode property,
            final EffectiveModelContext schemaContext) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            container.ifPresent(c -> setExampleValue(property, String.format("/%s:%s", module.orElseThrow().getPrefix(),
                    c.getQName().getLocalName())));
        }

        return STRING_TYPE;
    }

    private static String processUnionType(final UnionTypeDefinition unionType, final ObjectNode property,
            final String nodeName) {
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
            unionType.getDefaultValue().ifPresent(v -> setDefaultValue(property, (String) v));
            setExampleValue(property, "Some " + nodeName);
            return STRING_TYPE;
        }
        if (isBooleanTakePlace) {
            if (isNumberTakePlace) {
                // FIXME deal with other number formats
                unionType.getDefaultValue().ifPresent(v -> setDefaultValue(property, Long.valueOf((String) v)));
                setExampleValue(property, 0);
                return NUMBER_TYPE;
            }
            unionType.getDefaultValue().ifPresent(v -> setDefaultValue(property, Boolean.valueOf((String) v)));
            setExampleValue(property, true);
            return BOOLEAN_TYPE;
        }
        // FIXME deal with other number formats
        unionType.getDefaultValue().ifPresent(v -> setDefaultValue(property, Long.valueOf((String) v)));
        setExampleValue(property, 0);
        return NUMBER_TYPE;
    }

    private static ObjectNode buildXmlParameter(final SchemaNode node) {
        final ObjectNode xml = JsonNodeFactory.instance.objectNode();
        final QName qName = node.getQName();
        xml.put(NAME_KEY, qName.getLocalName());
        xml.put(NAMESPACE_KEY, qName.getNamespace().toString());
        return xml;
    }

    private static void setExampleValue(final ObjectNode property, final String value) {
        property.put(EXAMPLE_KEY, value);
    }

    private static void setExampleValue(final ObjectNode property, final Integer value) {
        property.put(EXAMPLE_KEY, value);
    }

    private static void setExampleValue(final ObjectNode property, final Long value) {
        property.put(EXAMPLE_KEY, value);
    }

    private static void setExampleValue(final ObjectNode property, final BigDecimal value) {
        property.put(EXAMPLE_KEY, value);
    }

    private static void setExampleValue(final ObjectNode property, final Boolean value) {
        property.put(EXAMPLE_KEY, value);
    }

    private static void setDefaultValue(final ObjectNode property, final String value) {
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
