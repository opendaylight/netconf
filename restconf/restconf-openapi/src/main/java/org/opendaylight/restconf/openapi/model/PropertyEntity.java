/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonGenerator;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.yangtools.yang.common.AbstractQName;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.AnydataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
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

    private final @NonNull DataSchemaNode node;
    private final @NonNull JsonGenerator generator;
    private final @NonNull List<String> required;
    private final @NonNull String parentName;
    private final @NonNull DefinitionNames definitionNames;

    public PropertyEntity(final @NonNull DataSchemaNode node, final @NonNull JsonGenerator generator,
            final @NonNull SchemaInferenceStack stack, final @NonNull List<String> required,
            final @NonNull String parentName, final boolean isParentConfig,
            final @NonNull DefinitionNames definitionNames) throws IOException {
        this.node = requireNonNull(node);
        this.generator = requireNonNull(generator);
        this.required = requireNonNull(required);
        this.parentName = requireNonNull(parentName);
        this.definitionNames = requireNonNull(definitionNames);
        generate(stack, isParentConfig);

    }

    private void generate(final SchemaInferenceStack stack, final boolean isParentConfig) throws IOException {
        if (node instanceof ChoiceSchemaNode choice) {
            stack.enterSchemaTree(node.getQName());
            final var isConfig = isParentConfig && node.isConfiguration();
            processChoiceNodeRecursively(isConfig, stack, choice);
            stack.exit();
        } else {
            generator.writeObjectFieldStart(node.getQName().getLocalName());
            processChildNode(node, stack, isParentConfig);
            generator.writeEndObject();
        }
    }

    private void processChoiceNodeRecursively(final boolean isConfig, final SchemaInferenceStack stack,
            final ChoiceSchemaNode choice) throws IOException {
        if (!choice.getCases().isEmpty()) {
            final var caseSchemaNode = choice.getDefaultCase().orElse(
                choice.getCases().stream().findFirst().orElseThrow());
            stack.enterSchemaTree(caseSchemaNode.getQName());
            for (final var childNode : caseSchemaNode.getChildNodes()) {
                if (childNode instanceof ChoiceSchemaNode childChoice) {
                    final var isChildConfig = isConfig && childNode.isConfiguration();
                    stack.enterSchemaTree(childNode.getQName());
                    processChoiceNodeRecursively(isChildConfig, stack, childChoice);
                    stack.exit();
                } else if (!isConfig || childNode.isConfiguration()) {
                    generator.writeObjectFieldStart(childNode.getQName().getLocalName());
                    processChildNode(childNode, stack, isConfig);
                    generator.writeEndObject();
                }
            }
            stack.exit();
        }
    }

    private void processChildNode(final DataSchemaNode schemaNode, final SchemaInferenceStack stack,
            final boolean isParentConfig) throws IOException {
        final var parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        stack.enterSchemaTree(schemaNode.getQName());
        final var name = schemaNode.getQName().getLocalName();
        final var shouldBeAddedAsChild = !isParentConfig || schemaNode.isConfiguration();
        if (schemaNode instanceof ListSchemaNode || schemaNode instanceof ContainerSchemaNode) {
            processDataNodeContainer((DataNodeContainer) schemaNode);
            if (shouldBeAddedAsChild && isSchemaNodeMandatory(schemaNode)) {
                required.add(name);
            }
        } else if (shouldBeAddedAsChild) {
            if (schemaNode instanceof LeafSchemaNode leaf) {
                processLeafNode(leaf, name, stack, parentNamespace);
            } else if (schemaNode instanceof AnyxmlSchemaNode || schemaNode instanceof AnydataSchemaNode) {
                processUnknownDataSchemaNode(schemaNode, name, parentNamespace);
            } else if (schemaNode instanceof LeafListSchemaNode leafList) {
                if (isSchemaNodeMandatory(schemaNode)) {
                    required.add(name);
                }
                processLeafListNode(leafList, stack);
            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + schemaNode.getClass());
            }
        }
        stack.exit();
    }

    private void processDataNodeContainer(final DataNodeContainer dataNode) throws IOException {
        final var schemaNode = (SchemaNode) dataNode;
        final var localName = schemaNode.getQName().getLocalName();
        final var nodeName = parentName + "_" + localName;


        final String discriminator;
        if (!definitionNames.isListedNode(schemaNode)) {
            final var parentNameConfigLocalName = parentName + "_" + localName;
            final var names = List.of(parentNameConfigLocalName);
            discriminator = definitionNames.pickDiscriminator(schemaNode, names);
        } else {
            discriminator = definitionNames.getDiscriminator(schemaNode);
        }

        processRef(nodeName, schemaNode, discriminator);
    }

    private void processRef(final String name, final SchemaNode schemaNode, String discriminator) throws IOException {
        final var ref = COMPONENTS_PREFIX + name + discriminator;
        if (schemaNode instanceof ListSchemaNode listNode) {
            generator.writeStringField(TYPE, ARRAY_TYPE);
            generator.writeObjectFieldStart(ITEMS);
            generator.writeStringField("$ref", ref);
            generator.writeEndObject();
            generator.writeStringField(DESCRIPTION, schemaNode.getDescription().orElse(""));

            if (listNode.getElementCountConstraint().isPresent()) {
                final var minElements = listNode.getElementCountConstraint().orElseThrow().getMinElements();
                final var maxElements = listNode.getElementCountConstraint().orElseThrow().getMaxElements();
                if (minElements != null) {
                    createExamples(listNode, minElements);
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
        @NonNull final Integer minElements) throws IOException {
        final var firstExampleMap = prepareFirstListExample(schemaNode);
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

    private HashMap<String, Object> prepareFirstListExample(final ListSchemaNode schemaNode) {
        final var childNodes = schemaNode.getChildNodes();
        final var firstExampleMap = new HashMap<String, Object>();
        // Cycle for each child node
        for (final var childNode : childNodes) {
            if (childNode instanceof TypedDataSchemaNode leafSchemaNode) {
                final var def = new TypeDef();
                processTypeDef(leafSchemaNode.getType(), leafSchemaNode, null, def);
                if (def.hasExample()) {
                    firstExampleMap.put(leafSchemaNode.getQName().getLocalName(), def.getExample());
                }
            }
        }
        return firstExampleMap;
    }

    private Object editExample(final Object exampleValue, final int edit) {
        if (exampleValue instanceof String string) {
            return string + "_" + edit;
        } else if (exampleValue instanceof Integer number) {
            return number + edit;
        } else if (exampleValue instanceof Long number) {
            return number + edit;
        } else if (exampleValue instanceof Decimal64 number) {
            return Decimal64.valueOf(BigDecimal.valueOf(number.intValue() + edit));
        }
        return exampleValue;
    }

    private void processUnknownDataSchemaNode(final DataSchemaNode leafNode, final String name,
            final XMLNamespace parentNamespace) throws IOException {
        assert (leafNode instanceof AnydataSchemaNode || leafNode instanceof AnyxmlSchemaNode);

        final var leafDescription = leafNode.getDescription().orElse("");
        generator.writeStringField(DESCRIPTION, leafDescription);

        final var localName = leafNode.getQName().getLocalName();
        generator.writeStringField(EXAMPLE, String.format("<%s> ... </%s>", localName, localName));
        generator.writeStringField(TYPE, STRING_TYPE);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            buildXmlParameter(leafNode);
        }
        processMandatory((MandatoryAware) leafNode, name, required);
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
            jsonType = processIdentityRefType(identityrefType, stack.getEffectiveModelContext(), def);
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
                stack.getEffectiveModelContext(), def);
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
        if (value instanceof Number) {
            if (value instanceof Integer intValue) {
                generator.writeNumberField(field, intValue);
            } else if (value instanceof Long longValue) {
                generator.writeNumberField(field, longValue);
            } else if (value instanceof BigDecimal decimalValue) {
                generator.writeNumberField(field, decimalValue);
            } else if (value instanceof BigInteger bigIntValue) {
                generator.writeNumberField(field, bigIntValue);
            }
        } else if (value instanceof Boolean bool) {
            generator.writeBooleanField(field, bool);
        } else {
            generator.writeStringField(field, (String) value);
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

    private String processBinaryType(final BinaryTypeDefinition definition, final TypeDef def) {
        if (definition.getDefaultValue().isPresent()) {
            def.setDefaultValue(definition.getDefaultValue().toString());
        }
        def.setFormat("byte");
        return STRING_TYPE;
    }

    private String processBitsType(final BitsTypeDefinition bitsType, final TypeDef def) {
        def.setMinItems(0);
        def.setUniqueItems(true);
        final var bits = bitsType.getBits();
        final var enumNames = bits.stream()
            .map(BitsTypeDefinition.Bit::getName)
            .toList();

        def.setEnums(enumNames);

        def.setDefaultValue(bitsType.getDefaultValue().isPresent()
            ? bitsType.getDefaultValue().orElseThrow().toString() :
            (enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1)));
        return STRING_TYPE;
    }

    private String processEnumType(final EnumTypeDefinition enumLeafType, final TypeDef def) {
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
            final EffectiveModelContext schemaContext, final TypeDef def) {
        final var schemaNode = leafTypeDef.getIdentities().iterator().next();
        def.setExample(schemaNode.getQName().getLocalName());

        final var derivedIds = schemaContext.getDerivedIdentities(schemaNode);
        final var enumPayload = new ArrayList<String>();
        enumPayload.add(schemaNode.getQName().getLocalName());
        populateEnumWithDerived(derivedIds, enumPayload, schemaContext);
        final var schemaEnum = new ArrayList<>(enumPayload);

        def.setEnums(schemaEnum);

        return STRING_TYPE;
    }

    private void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final List<String> enumPayload, final EffectiveModelContext context) {
        for (final var derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private String processStringType(final StringTypeDefinition stringType, final String nodeName, final TypeDef def) {
        var type = stringType;
        while (type.getLengthConstraint().isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
        }

        if (type.getLengthConstraint().isPresent()) {
            final var range = type.getLengthConstraint().orElseThrow().getAllowedRanges().span();
            def.setMinLength(range.lowerEndpoint());
            def.setMaxLength(range.upperEndpoint());
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
                final var regExp = new RegExp(regex);
                defaultValue = regExp.toAutomaton().getShortestExample(true);
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

    private String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,final TypeDef def) {
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

    private String processInstanceIdentifierType(final InstanceIdentifierTypeDefinition iidType,
            final DataSchemaNode schemaNode, final EffectiveModelContext schemaContext,final TypeDef def) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(schemaNode.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                .filter(n -> n instanceof ContainerSchemaNode)
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
}
