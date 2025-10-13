/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.model.RestDocgenUtil.widthList;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.State;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.AbstractQName;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.DerivedString;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraint;
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraintAware;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MandatoryAware;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.meta.ModelStatement;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.DecimalTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EmptyTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
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

public class PropertyEntity {
    private static final Logger LOG = LoggerFactory.getLogger(PropertyEntity.class);
    private static final String COMPONENTS_PREFIX = "#/components/schemas/";
    private static final String ARRAY_TYPE = "array";
    private static final String DESCRIPTION = "description";
    private static final String DEFAULT = "default";
    private static final String ENUM = "enum";
    private static final String EXAMPLE = "example";
    private static final String FORMAT = "format";
    private static final String ITEMS = "items";
    private static final String STRING_TYPE = "string";
    private static final String OBJECT_TYPE = "object";
    private static final String NUMBER_TYPE = "number";
    private static final String TYPE = "type";
    private static final Pattern AUTOMATON_SPECIAL_CHARACTERS = Pattern.compile("[@&\"<>#~]");
    // Adaptation from YANG regex to Automaton regex
    // See https://github.com/mifmif/Generex/blob/master/src/main/java/com/mifmif/common/regex/Generex.java
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES = Map.of("\\\\d", "[0-9]",
        "\\\\D", "[^0-9]", "\\\\s", "[ \t\n\f\r]", "\\\\S", "[^ \t\n\f\r]",
        "\\\\w", "[a-zA-Z_0-9]", "\\\\W", "[^a-zA-Z_0-9]");
    // Maximum number of candidates tried at each step when a string example is generated with constraints
    private static final int MAX_CANDIDATES_ALLOWED = 100;

    private final @NonNull DataSchemaNode node;
    private final @NonNull JsonGenerator generator;
    private final @NonNull List<String> required;
    private final @NonNull String parentName;
    private final @NonNull DefinitionNames definitionNames;
    private final int width;
    private final int depth;

    public PropertyEntity(final @NonNull DataSchemaNode node, final @NonNull JsonGenerator generator,
            final @NonNull SchemaInferenceStack stack, final @NonNull List<String> required,
            final @NonNull String parentName, final boolean isParentConfig,
            final @NonNull DefinitionNames definitionNames, final int width,
            final int depth, final int nodeDepth) throws IOException {
        this.node = requireNonNull(node);
        this.generator = requireNonNull(generator);
        this.required = requireNonNull(required);
        this.parentName = requireNonNull(parentName);
        this.definitionNames = requireNonNull(definitionNames);
        this.width = width;
        this.depth = depth;
        generate(stack, isParentConfig, nodeDepth);
    }

    private void generate(final SchemaInferenceStack stack, final boolean isParentConfig, final int nodeDepth)
            throws IOException {
        if (node instanceof ChoiceSchemaNode choice) {
            stack.enterSchemaTree(node.getQName());
            final var isConfig = isParentConfig && node.isConfiguration();
            processChoiceNodeRecursively(isConfig, stack, choice, nodeDepth);
            stack.exit();
        } else {
            generator.writeObjectFieldStart(resolveNamespace(stack, node) + node.getQName().getLocalName());
            processChildNode(node, stack, isParentConfig, nodeDepth);
            generator.writeEndObject();
        }
    }

    private void processChoiceNodeRecursively(final boolean isConfig, final SchemaInferenceStack stack,
            final ChoiceSchemaNode choice, final int nodeDepth) throws IOException {
        if (depth > 0 && nodeDepth + 1 > depth) {
            return;
        }
        if (!choice.getCases().isEmpty()) {
            final var caseSchemaNode = choice.getDefaultCase().orElse(
                choice.getCases().stream().findFirst().orElseThrow());
            stack.enterSchemaTree(caseSchemaNode.getQName());
            final var childNodes = widthList(caseSchemaNode, width);
            for (final var childNode : childNodes) {
                if (childNode instanceof ChoiceSchemaNode childChoice) {
                    final var isChildConfig = isConfig && childNode.isConfiguration();
                    stack.enterSchemaTree(childNode.getQName());
                    processChoiceNodeRecursively(isChildConfig, stack, childChoice, nodeDepth + 1);
                    stack.exit();
                } else if (!isConfig || childNode.isConfiguration()) {
                    generator.writeObjectFieldStart(resolveNamespace(stack, childNode) + childNode.getQName()
                        .getLocalName());
                    processChildNode(childNode, stack, isConfig, nodeDepth + 1);
                    generator.writeEndObject();
                }
            }
            stack.exit();
        }
    }

    /**
     * Resolve parent namespace
     *
     * <p>This method used to solve duplicity issues when node, and it's augmentation have the same name by adding name
     * of the module before name of the node to specify that this exact node if coming from augmentation.
     *
     * @param stack currently processed stack
     * @param childNode child node to be checked
     * @return name of the augmentation module with ':' or empty line if parent and child came from the same model
     */
    private static String resolveNamespace(final SchemaInferenceStack stack, final DataSchemaNode childNode) {
        final var parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        if (!childNode.getQName().getNamespace().equals(parentNamespace)) {
            // Getting module name from augmentation
            return stack.modelContext().getModuleStatement(childNode.getQName().getModule()).getDeclared()
                .argument().getLocalName() + ":";
        }
        return "";
    }

    private void processChildNode(final DataSchemaNode schemaNode, final SchemaInferenceStack stack,
            final boolean isParentConfig, final int nodeDepth) throws IOException {
        final var parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        stack.enterSchemaTree(schemaNode.getQName());
        final var name = schemaNode.getQName().getLocalName();
        final var shouldBeAddedAsChild = !isParentConfig || schemaNode.isConfiguration();
        if (schemaNode instanceof ListSchemaNode || schemaNode instanceof ContainerSchemaNode) {
            processDataNodeContainer(schemaNode, stack, nodeDepth + 1);
            if (shouldBeAddedAsChild && isSchemaNodeMandatory(schemaNode)) {
                required.add(name);
            }
        } else if (shouldBeAddedAsChild) {
            switch (schemaNode) {
                case AnyxmlSchemaNode anyxml -> processOpaqueDataSchemaNode(anyxml, anyxml, name, parentNamespace);
                case AnydataSchemaNode anydata -> processOpaqueDataSchemaNode(anydata, anydata, name, parentNamespace);
                case LeafSchemaNode leaf -> processLeafNode(leaf, name, stack, parentNamespace);
                case LeafListSchemaNode leafList -> {
                    if (isSchemaNodeMandatory(schemaNode)) {
                        required.add(name);
                    }
                    processLeafListNode(leafList, stack);
                }
                default -> throw new IOException("Unknown DataSchemaNode type: " + schemaNode.getClass());
            }
        }
        stack.exit();
    }

    private void processDataNodeContainer(final DataSchemaNode dataNode, final SchemaInferenceStack stack,
            final int nodeDepth) throws IOException {
        final var localName = dataNode.getQName().getLocalName();
        final var nodeName = parentName + "_" + localName;

        final String discriminator;
        if (!definitionNames.isListedNode(dataNode, nodeName)) {
            discriminator = definitionNames.pickDiscriminator(dataNode, List.of(nodeName));
        } else {
            discriminator = definitionNames.getDiscriminator(dataNode);
        }

        processRef(nodeName, dataNode, discriminator, stack, nodeDepth);
    }

    private void processRef(final String name, final SchemaNode schemaNode, final String discriminator,
            final SchemaInferenceStack stack, final int nodeDepth) throws IOException {
        final var ref = COMPONENTS_PREFIX + name + discriminator;
        if (schemaNode instanceof ListSchemaNode listNode) {
            if (depth > 0 && nodeDepth + 1 > depth) {
                return;
            }
            generator.writeStringField(TYPE, ARRAY_TYPE);
            generator.writeObjectFieldStart(ITEMS);
            generator.writeStringField("$ref", ref);
            generator.writeEndObject();
            generator.writeStringField(DESCRIPTION, schemaNode.getDescription().orElse(""));

            if (listNode.getElementCountConstraint().isPresent()) {
                final var minElements = listNode.getElementCountConstraint().orElseThrow().getMinElements();
                final var maxElements = listNode.getElementCountConstraint().orElseThrow().getMaxElements();
                if (minElements != null) {
                    createExamples(listNode, minElements, stack);
                    generator.writeNumberField("minItems", minElements);
                }
                if (maxElements != null) {
                    generator.writeNumberField("maxItems", maxElements);
                }
            }
        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            generator.writeStringField("$ref", ref);
        }
    }

    private void createExamples(final ListSchemaNode schemaNode,
            @NonNull final Integer minElements, final SchemaInferenceStack stack) throws IOException {
        final var firstExampleMap = prepareFirstListExample(schemaNode, stack);
        final var examples = new ArrayList<Map<String, Object>>();
        examples.add(firstExampleMap);

        final var unqiueContraintsNameSet = schemaNode.getUniqueConstraints().stream()
            .map(ModelStatement::argument)
            .flatMap(uniqueSt -> uniqueSt.stream()
                .map(schemaNI -> schemaNI.lastNodeIdentifier().getLocalName()))
            .collect(Collectors.toSet());
        final var keysNameSet = schemaNode.getKeyDefinition().stream()
            .map(AbstractQName::getLocalName)
            .collect(Collectors.toSet());
        for (int i = 1; i < minElements; i++) {
            final var exampleMap = new HashMap<String, Object>();
            for (final var example : firstExampleMap.entrySet()) {
                final Object exampleValue;
                if (keysNameSet.contains(example.getKey()) || unqiueContraintsNameSet.contains(example.getKey())) {
                    exampleValue = editExample(example.getValue(), i);
                } else {
                    exampleValue = example.getValue();
                }
                exampleMap.put(example.getKey(), exampleValue);
            }
            examples.add(exampleMap);
        }
        generator.writeArrayFieldStart(EXAMPLE);
        for (final var example : examples) {
            generator.writeStartObject();
            for (final var elem : example.entrySet()) {
                writeValue(elem.getKey(), elem.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private HashMap<String, Object> prepareFirstListExample(final ListSchemaNode schemaNode,
            final SchemaInferenceStack stack) {
        final var childNodes = schemaNode.getChildNodes();
        final var firstExampleMap = new HashMap<String, Object>();
        // Cycle for each child node
        for (final var childNode : childNodes) {
            if (childNode instanceof TypedDataSchemaNode leafSchemaNode) {
                final var def = new TypeDef();
                stack.enterDataTree(childNode.getQName());
                processTypeDef(leafSchemaNode.getType(), leafSchemaNode, stack, def);
                stack.exit();
                if (def.hasExample()) {
                    firstExampleMap.put(leafSchemaNode.getQName().getLocalName(), def.getExample());
                }
            }
        }
        return firstExampleMap;
    }

    private static Object editExample(final Object exampleValue, final int edit) {
        return switch (exampleValue) {
            case String val -> val + "_" + edit;
            case Integer val -> val + edit;
            case Long val -> val + edit;
            case Decimal64 val -> Decimal64.valueOf(BigDecimal.valueOf(val.longValue() + edit));
            case null, default -> exampleValue;
        };
    }

    private void processOpaqueDataSchemaNode(final DataSchemaNode dataSchema, final MandatoryAware mandatoryAware,
            final String name, final XMLNamespace parentNamespace) throws IOException {
        final var leafDescription = dataSchema.getDescription().orElse("");
        generator.writeStringField(DESCRIPTION,
            leafDescription + " (This is unknown data, need to be filled by user.)");

        generator.writeObjectFieldStart(EXAMPLE);
        generator.writeEndObject();
        generator.writeStringField(TYPE, OBJECT_TYPE);
        if (!dataSchema.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            buildXmlParameter(dataSchema);
        }
        processMandatory(mandatoryAware, name, required);
    }

    private void processLeafListNode(final LeafListSchemaNode listNode, final SchemaInferenceStack stack)
            throws IOException {
        generator.writeStringField(TYPE, ARRAY_TYPE);

        Integer minElements = null;
        final var optConstraint = listNode.getElementCountConstraint();
        if (optConstraint.isPresent()) {
            minElements = optConstraint.orElseThrow().getMinElements();
            if (minElements != null) {
                generator.writeNumberField("minItems", minElements);
            }
            final var maxElements = optConstraint.orElseThrow().getMaxElements();
            if (maxElements != null) {
                generator.writeNumberField("maxItems", maxElements);
            }
        }
        final var def = new TypeDef();
        processTypeDef(listNode.getType(), listNode, stack, def);

        generator.writeObjectFieldStart(ITEMS);
        processTypeDef(listNode.getType(), listNode, stack);
        generator.writeEndObject();
        generator.writeStringField(DESCRIPTION, listNode.getDescription().orElse(""));

        if (def.hasExample() && minElements != null) {
            final var listOfExamples = new ArrayList<>();
            for (int i = 0; i < minElements; i++) {
                listOfExamples.add(def.getExample());
            }
            generator.writeArrayFieldStart(EXAMPLE);
            for (final var example : listOfExamples) {
                generator.writeString(example.toString());
            }
            generator.writeEndArray();
        }
    }

    private void processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
            final SchemaInferenceStack stack, final XMLNamespace parentNamespace) throws IOException {
        final var leafDescription = leafNode.getDescription().orElse("");
        generator.writeStringField(DESCRIPTION, leafDescription);
        processTypeDef(leafNode.getType(), leafNode, stack);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            buildXmlParameter(leafNode);
        }
        processMandatory(leafNode, jsonLeafName, required);
    }

    private static void processMandatory(final MandatoryAware node, final String nodeName,
            final List<String> required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private void buildXmlParameter(final SchemaNode schemaNode) throws IOException {
        generator.writeObjectFieldStart("xml");
        generator.writeStringField("name", schemaNode.getQName().getLocalName());
        generator.writeStringField("namespace", schemaNode.getQName().getNamespace().toString());
        generator.writeEndObject();
    }

    protected static boolean isSchemaNodeMandatory(final DataSchemaNode schemaNode) {
        if (schemaNode instanceof ContainerSchemaNode containerNode) {
            if (containerNode.isPresenceContainer()) {
                return false;
            }
            for (final var childNode : containerNode.getChildNodes()) {
                if (childNode instanceof MandatoryAware mandatoryAware && mandatoryAware.isMandatory()) {
                    return true;
                }
            }
        }
        //  A list or leaf-list node with a "min-elements" statement with a value greater than zero.
        return schemaNode instanceof ElementCountConstraintAware constraintAware
            && constraintAware.getElementCountConstraint()
            .map(ElementCountConstraint::getMinElements)
            .orElse(0) > 0;
    }

    private void processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode schemaNode,
        final SchemaInferenceStack stack) throws IOException {
        final var def = new TypeDef();

        final var jsonType = processTypeDef(leafTypeDef, schemaNode, stack, def);

        generator.writeStringField(TYPE, jsonType);
        generateTypeDef(def);
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode schemaNode,
            final SchemaInferenceStack stack, final TypeDef def) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition binaryType) {
            jsonType = processBinaryType(binaryType, def);
        } else if (leafTypeDef instanceof BitsTypeDefinition bitsType) {
            jsonType = processBitsType(bitsType, def);
        } else if (leafTypeDef instanceof EnumTypeDefinition enumType) {
            jsonType = processEnumType(enumType, def);
        } else if (leafTypeDef instanceof IdentityrefTypeDefinition identityrefType) {
            jsonType = processIdentityRefType(identityrefType, stack.modelContext(), def);
        } else if (leafTypeDef instanceof StringTypeDefinition stringType) {
            jsonType = processStringType(stringType, schemaNode.getQName().getLocalName(), def);
        } else if (leafTypeDef instanceof UnionTypeDefinition unionType) {
            jsonType = processTypeDef(unionType.getTypes().iterator().next(), schemaNode, stack, def);
        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition leafrefType) {
            jsonType = processTypeDef(stack.resolveLeafref(leafrefType), schemaNode, stack, def);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = "boolean";
            if (leafTypeDef.getDefaultValue().isPresent()) {
                def.setDefaultValue(Boolean.parseBoolean((String) leafTypeDef.getDefaultValue().orElseThrow()));
            }
            def.setExample(true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
            jsonType = processNumberType(rangeRestrictedType, def);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition instanceIdentifierType) {
            jsonType = processInstanceIdentifierType(instanceIdentifierType, schemaNode,
                stack.modelContext(), def);
        } else {
            jsonType = STRING_TYPE;
        }

        if (leafTypeDef.getDefaultValue().isPresent()) {
            final var defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
            if (defaultValue instanceof String stringDefaultValue) {
                if (leafTypeDef instanceof BooleanTypeDefinition) {
                    def.setDefaultValue(Boolean.valueOf(stringDefaultValue));
                } else if (leafTypeDef instanceof DecimalTypeDefinition
                    || leafTypeDef instanceof Uint64TypeDefinition) {
                    def.setDefaultValue(new BigDecimal(stringDefaultValue));
                } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
                    //uint8,16,32 int8,16,32,64
                    if (isHexadecimalOrOctal(rangeRestrictedType)) {
                        def.setDefaultValue(stringDefaultValue);
                    } else {
                        def.setDefaultValue(Long.valueOf(stringDefaultValue));
                    }
                } else {
                    def.setDefaultValue(stringDefaultValue);
                }
            }
        }
        return jsonType;
    }

    private void writeValue(final String field, final Object value) throws IOException {
        switch (value) {
            case null -> generator.writeNullField(field);
            case Byte val -> generator.writeNumberField(field, val.shortValue());
            case Short val -> generator.writeNumberField(field, val);
            case Integer val -> generator.writeNumberField(field, val);
            case Long val -> generator.writeNumberField(field, val);
            case Float val -> generator.writeNumberField(field, val);
            case Double val -> generator.writeNumberField(field, val);
            case BigDecimal val -> generator.writeNumberField(field, val);
            case BigInteger val -> generator.writeNumberField(field, val);
            case Uint8 val -> generator.writeNumberField(field, val.toJava());
            case Uint16 val -> generator.writeNumberField(field, val.toJava());
            case Uint32 val -> generator.writeNumberField(field, val.toJava());
            case Uint64 val -> {
                generator.writeFieldName(field);
                generator.writeNumber(val.toCanonicalString());
            }
            case Decimal64 val -> {
                generator.writeFieldName(field);
                generator.writeNumber(val.toCanonicalString());
            }
            case Boolean val -> generator.writeBooleanField(field, val);
            case String val -> generator.writeStringField(field, val);
            case DerivedString<?> val -> generator.writeStringField(field, val.toCanonicalString());
            default -> throw new IOException("Unhandled value " + value.getClass().getName());
        }
    }

    private void generateTypeDef(final TypeDef def) throws IOException {
        if (def.hasEnums()) {
            generator.writeArrayFieldStart(ENUM);
            for (final var enumElem : def.getEnums()) {
                generator.writeString(enumElem);
            }
            generator.writeEndArray();
        }
        if (def.hasFormat()) {
            generator.writeStringField(FORMAT, def.getFormat());
        }
        if (def.hasMinItems()) {
            generator.writeNumberField("minItems", def.getMinItems());
        }
        if (def.hasDefaultValue()) {
            writeValue(DEFAULT, def.getDefaultValue());
        }
        if (def.hasExample()) {
            writeValue(EXAMPLE, def.getExample());
        }
        if (def.hasUniqueItems()) {
            generator.writeBooleanField("uniqueItems", def.getUniqueItems());
        }
        if (def.hasMinLength()) {
            generator.writeNumberField("minLength", def.getMinLength());
        }
        if (def.hasMaxLength()) {
            generator.writeNumberField("maxLength", def.getMaxLength());
        }
    }

    private static String processBinaryType(final BinaryTypeDefinition definition, final TypeDef def) {
        if (definition.getDefaultValue().isPresent()) {
            def.setDefaultValue(definition.getDefaultValue().toString());
        }
        def.setFormat("byte");
        return STRING_TYPE;
    }

    private static String processBitsType(final BitsTypeDefinition bitsType, final TypeDef def) {
        def.setMinItems(0);
        def.setUniqueItems(true);
        final var bits = bitsType.getBits();
        final var enumNames = bits.stream()
            .map(BitsTypeDefinition.Bit::getName)
            .toList();

        def.setEnums(enumNames);

        def.setDefaultValue(bitsType.getDefaultValue()
            .map(Object::toString)
            .orElseGet(() -> enumNames.getFirst() + " " + enumNames.getLast()));
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType, final TypeDef def) {
        final var enumPairs = enumLeafType.getValues();
        final var enumNames = enumPairs.stream()
            .map(EnumTypeDefinition.EnumPair::getName)
            .toList();

        def.setEnums(enumNames);

        if (enumLeafType.getDefaultValue().isPresent()) {
            final var defaultValue = enumLeafType.getDefaultValue().orElseThrow().toString();
            def.setDefaultValue(defaultValue);
        }
        def.setExample(enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
            final EffectiveModelContext modelContext, final TypeDef def) {
        final var schemaNode = leafTypeDef.getIdentities().iterator().next();
        def.setExample(schemaNode.getQName().getLocalName());

        final var derivedIds = modelContext.getDerivedIdentities(schemaNode);
        final var enumPayload = new ArrayList<String>();
        enumPayload.add(schemaNode.getQName().getLocalName());
        populateEnumWithDerived(derivedIds, enumPayload, modelContext);
        final var schemaEnum = new ArrayList<>(enumPayload);

        def.setEnums(schemaEnum);

        return STRING_TYPE;
    }

    private void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final List<String> enumPayload, final EffectiveModelContext modelContext) {
        for (final var derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(modelContext.getDerivedIdentities(derivedId), enumPayload, modelContext);
        }
    }

    private static String processStringType(final StringTypeDefinition stringType, final String nodeName,
            final TypeDef def) {
        var type = stringType;
        while (type.getLengthConstraint().isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
        }

        var minLength = 0;
        var maxLength = Integer.MAX_VALUE;
        if (type.getLengthConstraint().isPresent()) {
            final var range = type.getLengthConstraint().orElseThrow().getAllowedRanges().span();
            minLength = range.lowerEndpoint();
            maxLength = range.upperEndpoint();
            def.setMaxLength(maxLength);
            def.setMinLength(minLength);
        }

        if (type.getPatternConstraints().iterator().hasNext()) {
            final PatternConstraint pattern = type.getPatternConstraints().iterator().next();
            var regex = pattern.getRegularExpressionString();
            // Escape special characters to prevent issues inside Automaton.
            regex = AUTOMATON_SPECIAL_CHARACTERS.matcher(regex).replaceAll("\\\\$0");
            for (final var charClass : PREDEFINED_CHARACTER_CLASSES.entrySet()) {
                regex = regex.replaceAll(charClass.getKey(), charClass.getValue());
            }
            var defaultValue = "";
            try {
                final var automaton = new RegExp(regex).toAutomaton();
                if (minLength > 0) {
                    defaultValue = prepareExample(List.of(new ExampleCandidate(
                        defaultValue, automaton.getInitialState())), minLength, maxLength);
                } else {
                    defaultValue = automaton.getShortestExample(true);
                }
            } catch (IllegalArgumentException ex) {
                LOG.warn("Cannot create example string for type: {} with regex: {}.", stringType.getQName(), regex);
            }
            def.setExample(defaultValue);
        } else {
            def.setExample("Some " + nodeName);
        }

        if (stringType.getDefaultValue().isPresent()) {
            def.setDefaultValue(stringType.getDefaultValue().orElseThrow().toString());
        }
        return STRING_TYPE;
    }

    private static String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,final TypeDef def) {
        final var maybeLower = leafTypeDef.getRangeConstraint()
            .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            if (leafTypeDef.getDefaultValue().isPresent()) {
                def.setDefaultValue(Decimal64.valueOf(
                    leafTypeDef.getDefaultValue().orElseThrow().toString()).decimalValue());
            }
            if (maybeLower.isPresent()) {
                def.setExample(((Decimal64) maybeLower.orElseThrow()).decimalValue());
            }
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
            || leafTypeDef instanceof Uint16TypeDefinition
            || leafTypeDef instanceof Int8TypeDefinition
            || leafTypeDef instanceof Int16TypeDefinition
            || leafTypeDef instanceof Int32TypeDefinition) {

            def.setFormat("int32");
            if (leafTypeDef.getDefaultValue().isPresent()) {
                def.setDefaultValue(Integer.parseInt(leafTypeDef.getDefaultValue().orElseThrow().toString()));
            }
            if (maybeLower.isPresent()) {
                def.setExample(Integer.parseInt(maybeLower.orElseThrow().toString()));
            }
        } else if (leafTypeDef instanceof Uint32TypeDefinition
            || leafTypeDef instanceof Int64TypeDefinition) {
            def.setFormat("int64");
            if (leafTypeDef.getDefaultValue().isPresent()) {
                def.setDefaultValue(Long.parseLong(leafTypeDef.getDefaultValue().orElseThrow().toString()));
            }
            if (maybeLower.isPresent()) {
                def.setExample(Long.parseLong(maybeLower.orElseThrow().toString()));
            }
        } else {
            //uint64
            if (leafTypeDef.getDefaultValue().isPresent()) {
                def.setDefaultValue(new BigInteger(leafTypeDef.getDefaultValue().orElseThrow().toString()));
            }
            def.setExample(0);
        }
        return "integer";
    }

    private static boolean isHexadecimalOrOctal(final RangeRestrictedTypeDefinition<?, ?> typeDef) {
        final var optDefaultValue = typeDef.getDefaultValue();
        if (optDefaultValue.isPresent()) {
            final var defaultValue = (String) optDefaultValue.orElseThrow();
            return defaultValue.startsWith("0") || defaultValue.startsWith("-0");
        }
        return false;
    }

    private static String processInstanceIdentifierType(final InstanceIdentifierTypeDefinition iidType,
            final DataSchemaNode schemaNode, final EffectiveModelContext modelContext,final TypeDef def) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = modelContext.findModule(schemaNode.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                .filter(ContainerSchemaNode.class::isInstance)
                .findFirst();
            if (container.isPresent()) {
                def.setExample(String.format("/%s:%s", module.orElseThrow().getPrefix(),
                    container.orElseThrow().getQName().getLocalName()));
            }
        }
        // set default value
        if (iidType.getDefaultValue().isPresent()) {
            def.setDefaultValue(iidType.getDefaultValue().orElseThrow().toString());
        }
        return STRING_TYPE;
    }

    private static String prepareExample(final List<ExampleCandidate> candidates, final int minLength,
            final int maxLength) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No candidates found");
        }

        final var nextCandidates = new ArrayList<ExampleCandidate>();
        for (var candidate : candidates) {
            final var string = candidate.string();
            final var state = candidate.state();

            if (string.length() >= minLength && state.isAccept()) {
                return string;
            }

            if (string.length() < maxLength) {
                final var transitions = state.getSortedTransitions(false);
                final var toAdd = Math.min(MAX_CANDIDATES_ALLOWED - nextCandidates.size(), transitions.size());
                transitions.subList(0, toAdd).forEach(t -> nextCandidates.add(
                    new ExampleCandidate(string + t.getMin(), t.getDest())));
            }

            if (nextCandidates.size() >= MAX_CANDIDATES_ALLOWED) {
                break;
            }
        }

        if (nextCandidates.isEmpty()) {
            // If no string satisfies the length & regex constraints, return the first
            return candidates.getFirst().string();
        }

        return prepareExample(nextCandidates, minLength, maxLength);
    }

    private record ExampleCandidate(String string, State state) {
    }
}
