/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.COMPONENTS_PREFIX;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import dk.brics.automaton.RegExp;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNull;
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
import org.opendaylight.yangtools.yang.model.api.type.Uint8TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.UnionTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyEntity {
    private final Logger LOG = LoggerFactory.getLogger(PropertyEntity.class);
    private static final String ARRAY_TYPE = "array";
    private static final String DESCRIPTION = "description";
    private static final String DEFAULT = "default";
    private static final String ENUM = "enum";
    private static final String EXAMPLE = "example";
    private static final String STRING_TYPE = "string";
    private static final String OBJECT_TYPE = "object";
    private static final String NUMBER_TYPE = "number";
    private static final String TYPE = "type";
    private static final String INTEGER_TYPE = "integer";
    private static final String INT32_FORMAT = "int32";
    private static final String INT64_FORMAT = "int64";
    private static final String BOOLEAN_TYPE = "boolean";

    private static final Pattern AUTOMATON_SPECIAL_CHARACTERS = Pattern.compile("[@&\"<>#~]");
    // Adaptation from YANG regex to Automaton regex
    // See https://github.com/mifmif/Generex/blob/master/src/main/java/com/mifmif/common/regex/Generex.java
    private static final Map<String, String> PREDEFINED_CHARACTER_CLASSES = Map.of("\\\\d", "[0-9]",
        "\\\\D", "[^0-9]", "\\\\s", "[ \t\n\f\r]", "\\\\S", "[^ \t\n\f\r]",
        "\\\\w", "[a-zA-Z_0-9]", "\\\\W", "[^a-zA-Z_0-9]");

    private final DataSchemaNode node;
    private final JsonGenerator generator;
    private final List<String> required;
    private final String parentName;


    public PropertyEntity(final @NonNull DataSchemaNode childNode, final @NonNull JsonGenerator generator,
        final @NonNull SchemaInferenceStack stack, final List<String> required, final String parentName,
        final boolean isParentConfig) throws IOException {
        node = childNode;
        this.generator = generator;
        this.required = required;
        this.parentName = parentName;
        generate(stack, isParentConfig);

    }

    private void generate(SchemaInferenceStack stack, final boolean isParentConfig) throws IOException {
        if (node instanceof ChoiceSchemaNode choice) {
            stack.enterSchemaTree(node.getQName());
            final boolean isConfig = isParentConfig && node.isConfiguration();
            processChoiceNodeRecursively(isConfig, stack, choice);
            stack.exit();
        } else {
            generator.writeObjectFieldStart(node.getQName().getLocalName());
            processChildNode(node, stack, false);
            generator.writeEndObject();
        }
    }

    private void processChoiceNodeRecursively(final boolean isConfig, final SchemaInferenceStack stack,
        final ChoiceSchemaNode choice) throws IOException {
        if (!choice.getCases().isEmpty()) {
            final var caseSchemaNode = choice.getDefaultCase().orElse(choice.getCases().stream()
                .findFirst().orElseThrow());
            stack.enterSchemaTree(caseSchemaNode.getQName());
            for (final var childNode : caseSchemaNode.getChildNodes()) {
                if (childNode instanceof ChoiceSchemaNode childChoice) {
                    final var isChildConfig = isConfig && childNode.isConfiguration();
                    stack.enterSchemaTree(childNode.getQName());
                    processChoiceNodeRecursively(isChildConfig, stack, childChoice);
                    stack.exit();
                } else {
                    generator.writeObjectFieldStart(childNode.getQName().getLocalName());
                    processChildNode(childNode, stack, isConfig);
                    generator.writeEndObject();
                }
            }
            stack.exit();
        }
    }

    private void processChildNode(final DataSchemaNode node, final SchemaInferenceStack stack,
        final boolean isParentConfig) throws IOException {
        final XMLNamespace parentNamespace = stack.toSchemaNodeIdentifier().lastNodeIdentifier().getNamespace();
        stack.enterSchemaTree(node.getQName());

        final String name = node.getQName().getLocalName();

        final boolean shouldBeAddedAsChild = !isParentConfig || node.isConfiguration();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            processDataNodeContainer((DataNodeContainer) node, parentName);
            if (shouldBeAddedAsChild) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
            }
        } else if (shouldBeAddedAsChild) {
            if (node instanceof LeafSchemaNode leaf) {
                processLeafNode(leaf, name, required, stack, parentNamespace);
            } else if (node instanceof AnyxmlSchemaNode || node instanceof AnydataSchemaNode) {
                processUnknownDataSchemaNode(node, name, required, parentNamespace);
            } else if (node instanceof LeafListSchemaNode leafList) {
                if (isSchemaNodeMandatory(node)) {
                    required.add(name);
                }
                processLeafListNode(leafList, stack);
            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }
        }
        stack.exit();
    }


    private void processDataNodeContainer(final DataNodeContainer dataNode, final String parentName) throws IOException {
        final SchemaNode schemaNode = (SchemaNode) dataNode;
        final String localName = schemaNode.getQName().getLocalName();
        final String nodeName = parentName + "_" + localName;

        buildXmlParameter(schemaNode);

        processRef(nodeName, schemaNode);
    }

    private void processRef(final String name,
        final SchemaNode schemaNode) throws IOException {
        final String ref = COMPONENTS_PREFIX + name;

        if (schemaNode instanceof ListSchemaNode) {
            generator.writeStringField(TYPE, ARRAY_TYPE);
            generator.writeObjectFieldStart("items");
            generator.writeStringField("$ref", ref);
            generator.writeEndObject();
            generator.writeStringField(DESCRIPTION, schemaNode.getDescription().orElse(""));

        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            generator.writeStringField("$ref", ref);
        }

    }

    private void processUnknownDataSchemaNode(final DataSchemaNode leafNode, final String name,
        final List<String> required, final XMLNamespace parentNamespace) throws IOException {
        assert (leafNode instanceof AnydataSchemaNode || leafNode instanceof AnyxmlSchemaNode);

        final String leafDescription = leafNode.getDescription().orElse("");
        generator.writeStringField("description", leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        generator.writeStringField(EXAMPLE, String.format("<%s> ... </%s>", localName, localName));
        generator.writeStringField(TYPE, STRING_TYPE);
        if (!leafNode.getQName().getNamespace().equals(parentNamespace)) {
            // If the parent is not from the same model, define the child XML namespace.
            buildXmlParameter(leafNode);

        }
        processMandatory((MandatoryAware) leafNode, name, required);
    }

    private void processLeafListNode(final LeafListSchemaNode listNode,
        final SchemaInferenceStack stack) throws IOException {
        generator.writeStringField(TYPE, ARRAY_TYPE);

        final Property.Builder itemsVal = new Property.Builder();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        optConstraint.ifPresent(elementCountConstraint -> {
            try {
                processElementCount(elementCountConstraint);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        generator.writeObjectFieldStart("items2");
        processTypeDef(listNode.getType(), listNode, stack);
        generator.writeEndObject();

        generator.writeStringField(DESCRIPTION, listNode.getDescription().orElse(""));

    }

    private void processElementCount(final ElementCountConstraint constraint) throws IOException {
        final Integer minElements = constraint.getMinElements();
        if (minElements != null) {
            generator.writeStringField("minItems", minElements.toString());
        }
        final Integer maxElements = constraint.getMaxElements();
        if (maxElements != null) {
            generator.writeStringField("maxItems", maxElements.toString());
        }
    }

    private void processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
        final List<String> required, final SchemaInferenceStack stack, final XMLNamespace parentNamespace) throws IOException {

        final String leafDescription = leafNode.getDescription().orElse("");
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

    private boolean isSchemaNodeMandatory(final DataSchemaNode node) {

        if (node instanceof ContainerSchemaNode containerNode) {
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


    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
        final SchemaInferenceStack stack) throws IOException {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition binaryType) {
            jsonType = processBinaryType(binaryType);
        } else if (leafTypeDef instanceof BitsTypeDefinition bitsType) {
            jsonType = processBitsType(bitsType);
        } else if (leafTypeDef instanceof EnumTypeDefinition enumType) {
            jsonType = processEnumType(enumType);
        } else if (leafTypeDef instanceof IdentityrefTypeDefinition identityrefType) {
            jsonType = processIdentityRefType(identityrefType, stack.getEffectiveModelContext());
        } else if (leafTypeDef instanceof StringTypeDefinition stringType) {
            jsonType = processStringType(stringType, node.getQName().getLocalName());
        } else if (leafTypeDef instanceof UnionTypeDefinition unionType) {
            return processTypeDef(unionType.getTypes().iterator().next(), node, stack);
        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition leafrefType) {
            return processTypeDef(stack.resolveLeafref(leafrefType), node, stack);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            if (leafTypeDef.getDefaultValue().isPresent()) {
                generator.writeBooleanField(DEFAULT, Boolean.parseBoolean((String) leafTypeDef.getDefaultValue().get()));
            }
            generator.writeBooleanField(EXAMPLE, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
            jsonType = processNumberType(rangeRestrictedType);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition instanceIdentifierType) {
            jsonType = processInstanceIdentifierType(instanceIdentifierType, node,
                stack.getEffectiveModelContext());
        } else {
            jsonType = STRING_TYPE;
        }
        if (jsonType != null) {
            generator.writeStringField(TYPE, jsonType);
        }

        if (leafTypeDef.getDefaultValue().isPresent()) {
            final Object defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
            if (defaultValue instanceof String stringDefaultValue) {
                if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
                    //uint8,16,32 int8,16,32,64
                    if (isHexadecimalOrOctal(rangeRestrictedType)) {
                        generator.writeStringField(DEFAULT, stringDefaultValue);
                    }
                }
            } else {
                //we should never get here. getDefaultValue always gives us string
                generator.writeStringField(DEFAULT, defaultValue.toString());
            }
        }
//        if (leafTypeDef.getDefaultValue().isPresent()) {
//            final Object defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
//            if (defaultValue instanceof String stringDefaultValue) {
//                if (leafTypeDef instanceof BooleanTypeDefinition) {
//                    generator.writeBooleanField("default2", Boolean.parseBoolean(stringDefaultValue));
//                } else if (leafTypeDef instanceof DecimalTypeDefinition
//                    || leafTypeDef instanceof Uint64TypeDefinition) {
//                    generator.writeNumberField("default3", new BigDecimal(stringDefaultValue));
//                } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition<?, ?> rangeRestrictedType) {
//                    //uint8,16,32 int8,16,32,64
//                    if (isHexadecimalOrOctal(rangeRestrictedType)) {
//                        generator.writeStringField("default4", stringDefaultValue);
//                    } else {
//                        generator.writeNumberField("default5", Long.parseLong(stringDefaultValue));
//                    }
//                } else {
//                    generator.writeStringField("default6", stringDefaultValue);
//                }
//            } else {
//                //we should never get here. getDefaultValue always gives us string
//                generator.writeStringField("default7", defaultValue.toString());
//            }
//        }
        return jsonType;
    }

    private String processBinaryType(final BinaryTypeDefinition definition) throws IOException {
        definition.getDefaultValue().ifPresent(value -> {
            try {
                generator.writeStringField(DEFAULT, value.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        generator.writeStringField("format", "byte");
        return STRING_TYPE;
    }

    private String processBitsType(final BitsTypeDefinition bitsType) throws IOException {
        generator.writeNumberField("minElements", 0);
        generator.writeBooleanField("uniqueItems", true);
        final Collection<? extends BitsTypeDefinition.Bit> bits = bitsType.getBits();
        final List<String> enumNames = bits.stream()
            .map(BitsTypeDefinition.Bit::getName)
            .toList();

        generator.writeArrayFieldStart(ENUM);
        for (String e : enumNames) generator.writeString(e);
        generator.writeEndArray();

        generator.writeStringField(DEFAULT,
            bitsType.getDefaultValue().isPresent() ? bitsType.getDefaultValue().get().toString() : (enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1)));
        return STRING_TYPE;
    }

    private String processEnumType(final EnumTypeDefinition enumLeafType) throws IOException {
        final List<EnumTypeDefinition.EnumPair> enumPairs = enumLeafType.getValues();
        final List<String> enumNames = enumPairs.stream()
            .map(EnumTypeDefinition.EnumPair::getName)
            .toList();

        generator.writeArrayFieldStart(ENUM);
        for (String e : enumNames) generator.writeString(e);
        generator.writeEndArray();

        generator.writeStringField(DEFAULT,
            enumLeafType.getDefaultValue().isPresent() ? enumLeafType.getDefaultValue().get().toString() :
                enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
        final EffectiveModelContext schemaContext) throws IOException {
        final IdentitySchemaNode node = leafTypeDef.getIdentities().iterator().next();
        generator.writeStringField(EXAMPLE, node.getQName().getLocalName());

        final Collection<? extends IdentitySchemaNode> derivedIds = schemaContext.getDerivedIdentities(node);
        final List<String> enumPayload = new ArrayList<>();
        enumPayload.add(node.getQName().getLocalName());
        populateEnumWithDerived(derivedIds, enumPayload, schemaContext);
        final List<String> schemaEnum = new ArrayList<>(enumPayload);

        generator.writeArrayFieldStart(ENUM);
        for (String e : schemaEnum) generator.writeString(e);
        generator.writeEndArray();

        return STRING_TYPE;
    }

    private void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
        final List<String> enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private String processStringType(final StringTypeDefinition stringType, final String nodeName) throws IOException {
        var type = stringType;
        while (type.getLengthConstraint().isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
        }

        type.getLengthConstraint().ifPresent(constraint -> {
            final Range<Integer> range = constraint.getAllowedRanges().span();
            try {
                generator.writeNumberField("minLenght", range.lowerEndpoint());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                generator.writeNumberField("maxLength", range.upperEndpoint());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
            generator.writeStringField(DEFAULT, defaultValue);
        } else {
            generator.writeStringField(EXAMPLE, "Some " + nodeName);
        }

        if (stringType.getDefaultValue().isPresent()) {
            generator.writeStringField(DEFAULT, stringType.getDefaultValue().get().toString());
        }
        return STRING_TYPE;
    }

    private String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef) throws IOException {
        final Optional<Number> maybeLower = leafTypeDef.getRangeConstraint()
            .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            if (leafTypeDef.getDefaultValue().isPresent()) {
                generator.writeNumberField(DEFAULT, Decimal64.valueOf(leafTypeDef.getDefaultValue().get().toString()).decimalValue());
            }
            if (maybeLower.isPresent()) {
                generator.writeNumberField(EXAMPLE, ((Decimal64) maybeLower.get()).decimalValue());
            }
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
            || leafTypeDef instanceof Uint16TypeDefinition
            || leafTypeDef instanceof Int8TypeDefinition
            || leafTypeDef instanceof Int16TypeDefinition
            || leafTypeDef instanceof Int32TypeDefinition) {

            generator.writeStringField("format", INT32_FORMAT);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                generator.writeNumberField(DEFAULT, Integer.parseInt(leafTypeDef.getDefaultValue().get().toString()));
            }
            if (maybeLower.isPresent()) {
                generator.writeNumberField(EXAMPLE, Integer.parseInt(maybeLower.get().toString()));
            }
        } else if (leafTypeDef instanceof Uint32TypeDefinition
            || leafTypeDef instanceof Int64TypeDefinition) {

            generator.writeStringField("format", INT64_FORMAT);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                generator.writeNumberField(DEFAULT, Long.parseLong(leafTypeDef.getDefaultValue().get().toString()));
            }
            if (maybeLower.isPresent()) {
                generator.writeNumberField(EXAMPLE, Long.parseLong(maybeLower.get().toString()));
            }
        } else {
            //uint64
            if (leafTypeDef.getDefaultValue().isPresent()) {
                generator.writeNumberField(DEFAULT, new BigInteger(leafTypeDef.getDefaultValue().get().toString()));
            }
            generator.writeNumberField(EXAMPLE, 0);
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

    private String processInstanceIdentifierType(final InstanceIdentifierTypeDefinition iidType,
        final DataSchemaNode node, final EffectiveModelContext schemaContext) throws IOException {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                .filter(n -> n instanceof ContainerSchemaNode)
                .findFirst();
            if (container.isPresent()) {
                generator.writeStringField(EXAMPLE, String.format("/%s:%s", module.orElseThrow().getPrefix(),
                    container.get().getQName().getLocalName()));
            }
        }
        // set default value
        if (iidType.getDefaultValue().isPresent()) {
            generator.writeStringField(DEFAULT, iidType.getDefaultValue().get().toString());
        }
        return STRING_TYPE;
    }
}
