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
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.POST_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TOP;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.XML_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.XML_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.getAppropriateModelPrefix;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mifmif.common.regex.Generex;
import java.io.IOException;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator.MapperGeneratorRecord;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
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
public class DefinitionGenerator extends StdSerializer<MapperGeneratorRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(DefinitionGenerator.class);

    @Serial
    private static final long serialVersionUID = 1L;

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

    private QNameModule qnameModule;


    public DefinitionGenerator() {
        this(null);
    }

    public DefinitionGenerator(final Class<MapperGeneratorRecord> recordClass) {
        super(recordClass);
    }

    @Override
    public void serialize(final MapperGeneratorRecord value, final JsonGenerator gen, final SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        if (value.isForSingleModule()) {
            value.definitionNames().addUnlinkedName(value.module().getName() + MODULE_NAME_SUFFIX);
        }
        convertToJsonSchema(value.module(), value.schemaContext(), gen, value.definitionNames(), value.oaversion(),
                value.isForSingleModule());
        gen.writeEndObject();
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
    public JsonGenerator convertToJsonSchema(final Module module, final EffectiveModelContext schemaContext,
                                          final JsonGenerator generator, final DefinitionNames definitionNames,
                                          final OAversion oaversion, final boolean isForSingleModule)
            throws IOException {
        qnameModule = module.getQNameModule();

        processIdentities(module, generator, definitionNames, schemaContext);
        processContainersAndLists(module, generator, definitionNames, schemaContext, oaversion);
        processRPCs(module, generator, definitionNames, schemaContext, oaversion);

        if (isForSingleModule) {
            processModule(module, generator, definitionNames, schemaContext, oaversion);
        }

        return generator;
    }

    private void processModule(final Module module, final JsonGenerator generator,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext, final OAversion oaversion)
            throws IOException {
        final String moduleName = module.getName();
        final String definitionName = moduleName + MODULE_NAME_SUFFIX;
        generator.writeObjectFieldStart(definitionName); // definition
        generator.writeStringField(TITLE_KEY, definitionName);
        generator.writeStringField(TYPE_KEY, OBJECT_TYPE);
        generator.writeObjectFieldStart(PROPERTIES_KEY); // properties
        HashSet<String> required = new HashSet<>();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode node : module.getChildNodes()) {
            stack.enterSchemaTree(node.getQName());
            final String localName = node.getQName().getLocalName();
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    for (final DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        generator.writeObjectFieldStart(localName);  // childNodeProperties
                        final String ref = getAppropriateModelPrefix(oaversion)
                                + moduleName + CONFIG
                                + "_" + localName
                                + definitionNames.getDiscriminator(node);

                        if (node instanceof ListSchemaNode) {
                            generator.writeStringField(TYPE_KEY, ARRAY_TYPE);
                            generator.writeObjectFieldStart(ITEMS_KEY);
                            generator.writeStringField(REF_KEY, ref);
                            generator.writeEndObject();
                            generator.writeStringField(DESCRIPTION_KEY, childNode.getDescription().orElse(""));
                            generator.writeStringField(TITLE_KEY, localName + CONFIG);
                        } else {
                         /*
                            Description can't be added, because nothing allowed alongside $ref.
                            allOf is not an option, because ServiceNow can't parse it.
                          */
                            generator.writeStringField(REF_KEY, ref);
                        }
                        //add module name prefix to property name, when ServiceNow can process colons
                        generator.writeEndObject();
                    }
                } else if (node instanceof LeafSchemaNode) {
                    /*
                        Add module name prefix to property name, when ServiceNow can process colons(second parameter
                        of processLeafNode).
                     */
                    processLeafNode((LeafSchemaNode) node, localName, generator, required, stack,
                            definitionNames, oaversion);
                }
            }
            stack.exit();
        }
        generator.writeEndObject();
        generator.writeStringField(DESCRIPTION_KEY, module.getDescription().orElse(""));
        setRequiredIfNotEmpty(generator, required);
        generator.writeEndObject();
    }

    private void processContainersAndLists(final Module module, final JsonGenerator generator,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext, final OAversion oaversion)
                throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, generator, definitionNames,
                            true, stack, oaversion);
                }
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, generator, definitionNames,
                        false, stack, oaversion);
                processActionNodeContainer(childNode, moduleName, generator, definitionNames, stack, oaversion);
            }
            stack.exit();
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final JsonGenerator generator, final DefinitionNames definitionNames,
                                            final SchemaInferenceStack stack, final OAversion oaversion)
            throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, generator, definitionNames, stack, oaversion);
            stack.exit();
        }
    }

    private void processRPCs(final Module module, final JsonGenerator generator, final DefinitionNames definitionNames,
                             final EffectiveModelContext schemaContext, final OAversion oaversion) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, generator, definitionNames, stack, oaversion);
            stack.exit();
        }
    }

    private void processOperations(final OperationDefinition operationDef, final String parentName,
            final JsonGenerator generator, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final OAversion oaversion)
                throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, generator,
                definitionNames, stack, oaversion);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, generator,
                definitionNames, stack, oaversion);
    }

    private void processOperationInputOutput(final ContainerLike container, final String operationName,
                                             final String parentName, final boolean isInput,
                                             final JsonGenerator generator, final DefinitionNames definitionNames,
                                             final SchemaInferenceStack stack, final OAversion oaversion)
            throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final String discriminator =
                    definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            generator.writeObjectFieldStart(filename + discriminator);
            processChildren(generator, container.getChildNodes(), parentName, definitionNames,
                    false, stack, oaversion);

            generator.writeStringField(TYPE_KEY, OBJECT_TYPE);

            generator.writeObjectFieldStart(XML_KEY);
            generator.writeStringField(NAME_KEY, isInput ? INPUT : OUTPUT);
            generator.writeEndObject();

            generator.writeStringField(TITLE_KEY, filename);

            generator.writeEndObject();

            processTopData(filename, discriminator, generator, container, oaversion);
        }
        stack.exit();
    }

    private static JsonGenerator processTopData(final String filename, final String discriminator,
            final JsonGenerator generator, final SchemaNode schemaNode, final OAversion oaversion) throws IOException {
        final String topName = filename + TOP;
        generator.writeObjectFieldStart(topName + discriminator);

        generator.writeStringField(TYPE_KEY, OBJECT_TYPE);
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        generator.writeObjectFieldStart(PROPERTIES_KEY);

        generator.writeObjectFieldStart(schemaNode.getQName().getLocalName());
        final String name = filename + discriminator;
        final String ref = getAppropriateModelPrefix(oaversion) + name;

        if (schemaNode instanceof ListSchemaNode) {
            generator.writeStringField(TYPE_KEY, ARRAY_TYPE);
            generator.writeObjectFieldStart(ITEMS_KEY);
            generator.writeStringField(REF_KEY, ref);
            generator.writeEndObject();

            generator.writeStringField(DESCRIPTION_KEY, schemaNode.getDescription().orElse(""));
        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            generator.writeStringField(REF_KEY, ref);
        }
        generator.writeEndObject();
        generator.writeEndObject();

        generator.writeStringField(TYPE_KEY, topName);
        generator.writeEndObject();

        return generator;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     * @param module          The module from which the identity stmt will be processed
     * @param definitions     The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames Store for definition names
     */
    private static void processIdentities(final Module module, final JsonGenerator generator,
                                          final DefinitionNames definitionNames, final EffectiveModelContext context)
            throws IOException {
        final String moduleName = module.getName();
        final Collection<? extends IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());

        for (final IdentitySchemaNode idNode : idNodes) {
            final String idName = idNode.getQName().getLocalName();
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(idName));
            final String name = idName + discriminator;
            generator.writeObjectFieldStart(name);
            buildIdentityObject(generator, idNode, context);
            generator.writeEndObject();
        }
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final JsonGenerator generator, final EffectiveModelContext context) throws IOException {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            generator.writeString(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), generator, context);
        }
    }

    private JsonGenerator processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
                                                final JsonGenerator generator, final DefinitionNames definitionNames,
                                                final boolean isConfig, final SchemaInferenceStack stack,
                                                final OAversion oaversion) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = schemaNode.getQName().getLocalName();
            final String postNodeName = parentName + CONFIG + "_" + localName + POST_SUFFIX;
            final String postXmlNodeName = postNodeName + XML_SUFFIX;
            final String parentNameConfigLocalName = parentName + CONFIG + "_" + localName;
            final String nameAsParent = parentName + "_" + localName;
            final String discriminator;
            if (!definitionNames.isListedNode(schemaNode)) {
                final List<String> names = List.of(parentNameConfigLocalName,
                        parentNameConfigLocalName + TOP,
                        nameAsParent,
                        nameAsParent + TOP,
                        postNodeName,
                        postXmlNodeName);
                discriminator = definitionNames.pickDiscriminator(schemaNode, names);
            } else {
                discriminator = definitionNames.getDiscriminator(schemaNode);
            }

            final String nodeName = parentName + (isConfig ? CONFIG : "") + "_" + localName;
            generator.writeObjectFieldStart(nodeName + discriminator);

            final String description = schemaNode.getDescription().orElse("");

            generator.writeStringField(TYPE_KEY, OBJECT_TYPE);

            generator.writeObjectFieldStart(PROPERTIES_KEY);
            processChildren(generator, containerChildren, parentName + "_" + localName, definitionNames,
                    isConfig, stack, oaversion);
            generator.writeEndObject();
            generator.writeStringField(TITLE_KEY, nodeName);
            generator.writeStringField(DESCRIPTION_KEY, description);
            buildXmlParameter(XML_KEY, generator, schemaNode);
            generator.writeEndObject();

            if (isConfig) {
                String truePostNodeName = postNodeName + discriminator;
                generator.writeObjectFieldStart(truePostNodeName);
                generator.writeStringField(TYPE_KEY, OBJECT_TYPE);
                generator.writeObjectFieldStart(PROPERTIES_KEY);
                if (dataNode instanceof ListSchemaNode listNode) {
                    final List<QName> keyDefinition = listNode.getKeyDefinition();
                    final Set<String> keys = listNode.getChildNodes().stream()
                            .filter(node -> keyDefinition.contains(node.getQName()))
                            .map(node -> node.getQName().getLocalName())
                            .collect(Collectors.toSet());
                    processChildren(generator, containerChildren, parentName + "_" + localName, definitionNames,
                            isConfig, stack, oaversion, keys);
                }
                else {
                    processChildren(generator, containerChildren, parentName + "_" + localName, definitionNames,
                            isConfig, stack, oaversion, Set.of());
                }
                generator.writeEndObject();

                generator.writeStringField(TITLE_KEY, postNodeName);
                generator.writeStringField(DESCRIPTION_KEY, description);
                buildXmlParameter(XML_KEY, generator, schemaNode);
                generator.writeEndObject();

                generator.writeObjectFieldStart(postXmlNodeName + discriminator);
                generator.writeStringField(REF_KEY, getAppropriateModelPrefix(oaversion) + truePostNodeName);
                generator.writeEndObject();
            }
            return processTopData(nodeName, discriminator, generator, schemaNode, oaversion);
        }
        return generator;
    }

    private static JsonGenerator createPostJsonSchema(final SchemaNode dataNode, final JsonGenerator generator,
            final String postNodeName, final String description) throws IOException {
        generator.writeStringField(TYPE_KEY, OBJECT_TYPE);

        generator.writeObjectFieldStart(PROPERTIES_KEY);
        if (dataNode instanceof ListSchemaNode) {
            createListItemProperties(generator, (ListSchemaNode) dataNode);
        }
//        else {
            //TODO: Find out how to copy/re-create some previous element.
//            postItemProperties = properties.deepCopy();
//        }
        generator.writeEndObject();

        generator.writeStringField(TITLE_KEY, postNodeName);
        generator.writeStringField(DESCRIPTION_KEY, description);
        buildXmlParameter(XML_KEY, generator, dataNode);
        return generator;
    }

    private static JsonGenerator createListItemProperties(final JsonGenerator generator, final ListSchemaNode listNode)
            throws IOException {
        final List<QName> keyDefinition = listNode.getKeyDefinition();
        final Set<String> keys = listNode.getChildNodes().stream()
                .filter(node -> keyDefinition.contains(node.getQName()))
                .map(node -> node.getQName().getLocalName())
                .collect(Collectors.toSet());

        //TODO: Should be moved to processChildren() method since this should add json object which was not added
        //      in this method.
        if (!keys.contains(TYPE_KEY)) {
            generator.writeStringField(TYPE_KEY, OBJECT_TYPE);
        }

        return generator;
    }

    /**
     * Processes the nodes.
     */
    private JsonGenerator processChildren(
            final JsonGenerator generator, final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion) throws IOException {
        generator.writeObjectFieldStart(PROPERTIES_KEY);
        for (final DataSchemaNode node : nodes) {
            if (!isConfig || node.isConfiguration()) {
                processChildNode(node, parentName, generator, definitionNames, isConfig, stack, oaversion);
            }
        }
        generator.writeEndObject();
        return generator;
    }

    private JsonGenerator processChildren(
            final JsonGenerator generator, final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion, final Set<String> skip) throws IOException {
        for (final DataSchemaNode node : nodes) {
            if (!skip.contains(node.getQName().getLocalName())
                    && !isConfig || node.isConfiguration()) {
                processChildNode(node, parentName, generator, definitionNames, isConfig, stack, oaversion);
            }
        }
        return generator;
    }

    private void processChildNode(
            final DataSchemaNode node, final String parentName, final JsonGenerator generator,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion)
            throws IOException {

        stack.enterSchemaTree(node.getQName());

        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();

        // TODO: Was this required Set<String> array somehow used?
        if (node instanceof LeafSchemaNode leaf) {
            processLeafNode(leaf, name, generator, new HashSet<>(), stack, definitionNames,
                oaversion);

        } else if (node instanceof AnyxmlSchemaNode anyxml) {
            processAnyXMLNode(anyxml, name, generator, new HashSet<>());

        } else if (node instanceof AnydataSchemaNode anydata) {
            processAnydataNode(anydata, name, generator, new HashSet<>());

        } else {
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                processDataNodeContainer((DataNodeContainer) node, parentName, generator,
                        definitionNames, isConfig, stack, oaversion);
                if (!isConfig) {
                    processActionNodeContainer(node, parentName, generator, definitionNames, stack, oaversion);
                }
            } else if (node instanceof LeafListSchemaNode leafList) {
                processLeafListNode(leafList, stack, generator, definitionNames, oaversion);

            } else if (node instanceof ChoiceSchemaNode choice) {
                for (final CaseSchemaNode variant : choice.getCases()) {
                    stack.enterSchemaTree(variant.getQName());
                    for (final DataSchemaNode childNode : variant.getChildNodes()) {
                        processChildNode(childNode, parentName, generator, definitionNames, isConfig, stack, oaversion);
                    }
                    stack.exit();
                }
            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }
        }

        stack.exit();
    }

    private JsonGenerator processLeafListNode(final LeafListSchemaNode listNode, final SchemaInferenceStack stack,
                                           final JsonGenerator generator, final DefinitionNames definitionNames,
                                           final OAversion oaversion) throws IOException {
        // TODO: Does we use somehow props object? Why this is never saved to parent container?
        //       Caller never use return value of this method
//        final ObjectNode props = JsonNodeFactory.instance.objectNode();
//        props.put(TYPE_KEY, ARRAY_TYPE);

        // TODO itemsVal is created but not stored in parent container...
//        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
//        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
//        processElementCount(optConstraint, props);

        processTypeDef(listNode.getType(), listNode, generator, stack, definitionNames, oaversion);
//        props.set(ITEMS_KEY, itemsVal);
//
//        props.put(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return generator;
    }

//    TODO: Util method looks like never applied to result JSON
//    private static void processElementCount(final Optional<ElementCountConstraint> constraint,
//          final ObjectNode props) {
//        if (constraint.isPresent()) {
//            final ElementCountConstraint constr = constraint.get();
//            final Integer minElements = constr.getMinElements();
//            if (minElements != null) {
//                props.put(MIN_ITEMS, minElements);
//            }
//            final Integer maxElements = constr.getMaxElements();
//            if (maxElements != null) {
//                props.put(MAX_ITEMS, maxElements);
//            }
//        }
//    }

    private static void processMandatory(final MandatoryAware node, final String nodeName, final Set<String> required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private JsonGenerator processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
                                       final JsonGenerator generator, final Set<String> required,
                                       final SchemaInferenceStack stack,
                                       final DefinitionNames definitionNames, final OAversion oaversion)
            throws IOException {
        generator.writeObjectFieldStart(jsonLeafName);

        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            Description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            generator.writeStringField(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, generator, stack, definitionNames, oaversion);
        buildXmlParameter(XML_KEY, generator, leafNode);

        generator.writeEndObject();
        processMandatory(leafNode, jsonLeafName, required);

        return generator;
    }

    private static JsonGenerator processAnydataNode(final AnydataSchemaNode leafNode, final String name,
                                                 final JsonGenerator generator, final Set<String> required)
            throws IOException {
        generator.writeObjectFieldStart(name);

        final String leafDescription = leafNode.getDescription().orElse("");
        generator.writeStringField(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(generator, String.format("<%s> ... </%s>", localName, localName));
        generator.writeStringField(TYPE_KEY, STRING_TYPE);
        buildXmlParameter(XML_KEY, generator, leafNode);
        processMandatory(leafNode, name, required);

        return generator;
    }

    private static JsonGenerator processAnyXMLNode(final AnyxmlSchemaNode leafNode, final String name,
                                                final JsonGenerator generator, final Set<String> required)
            throws IOException {
        generator.writeObjectFieldStart(name);

        final String leafDescription = leafNode.getDescription().orElse("");
        generator.writeStringField(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(generator, String.format("<%s> ... </%s>", localName, localName));
        generator.writeStringField(TYPE_KEY, STRING_TYPE);
        buildXmlParameter(XML_KEY, generator, leafNode);
        processMandatory(leafNode, name, required);
        generator.writeEndObject();
        return generator;
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final JsonGenerator generator, final SchemaInferenceStack stack,
                                  final DefinitionNames definitionNames,
                                  final OAversion oaversion) throws IOException {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(generator);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, generator);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, generator);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef, generator,
                    definitionNames, oaversion, stack.getEffectiveModelContext());

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, generator, node.getQName().getLocalName());

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processTypeDef(stack.resolveLeafref((LeafrefTypeDefinition) leafTypeDef), node, generator,
                stack, definitionNames, oaversion);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            generator.writeBooleanField(DEFAULT_KEY, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
            jsonType = processNumberType((RangeRestrictedTypeDefinition<?, ?>) leafTypeDef, generator);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition) {
            jsonType = processInstanceIdentifierType(node, generator, stack.getEffectiveModelContext());
        } else {
            jsonType = STRING_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(generator, TYPE_KEY, jsonType);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final Object defaultValue = leafTypeDef.getDefaultValue().get();
                if (defaultValue instanceof String stringDefaultValue) {
                    if (leafTypeDef instanceof BooleanTypeDefinition) {
                        setDefaultValue(generator, Boolean.valueOf(stringDefaultValue));
                    } else if (leafTypeDef instanceof DecimalTypeDefinition
                            || leafTypeDef instanceof Uint64TypeDefinition) {
                        setDefaultValue(generator, new BigDecimal(stringDefaultValue));
                    } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                        //uint8,16,32 int8,16,32,64
                        if (isHexadecimalOrOctal((RangeRestrictedTypeDefinition<?, ?>)leafTypeDef)) {
                            setDefaultValue(generator, stringDefaultValue);
                        } else {
                            setDefaultValue(generator, Long.valueOf(stringDefaultValue));
                        }
                    } else {
                        setDefaultValue(generator, stringDefaultValue);
                    }
                } else {
                    //we should never get here. getDefaultValue always gives us string
                    setDefaultValue(generator, defaultValue.toString());
                }
            }
        }
        return jsonType;
    }

    private static String processBinaryType(final JsonGenerator generator) throws IOException {
        generator.writeStringField(FORMAT_KEY, "byte");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType,
                                          final JsonGenerator generator) throws IOException {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        generator.writeArrayFieldStart(ENUM_KEY);
        for (final EnumPair enumPair : enumPairs) {
            generator.writeString(enumPair.getName());
        }
        generator.writeEndArray();
        generator.writeStringField(DEFAULT_KEY, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef, final JsonGenerator generator,
                                          final DefinitionNames definitionNames,
                                          final OAversion oaversion, final EffectiveModelContext schemaContext)
            throws IOException {
        final String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, generator, definitionNames, schemaContext);
        } else {
            final SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        generator.writeStringField(REF_KEY, getAppropriateModelPrefix(oaversion) + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
                                              final JsonGenerator generator, final DefinitionNames definitionNames,
                                              final EffectiveModelContext context) throws IOException {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        final String identityName = idNode.getQName().getLocalName();
        if (!definitionNames.isListedNode(idNode)) {
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            final String name = identityName + discriminator;
            generator.writeObjectFieldStart(name);
            buildIdentityObject(generator, idNode, context);
            generator.writeEndObject();
            return name;
        } else {
            return identityName + definitionNames.getDiscriminator(idNode);
        }
    }

    private static JsonGenerator buildIdentityObject(final JsonGenerator generator, final IdentitySchemaNode idNode,
            final EffectiveModelContext context) throws IOException {
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);

        generator.writeStringField(TITLE_KEY, identityName);
        generator.writeStringField(DESCRIPTION_KEY,idNode.getDescription().orElse(""));

        final Collection<? extends IdentitySchemaNode> derivedIds = context.getDerivedIdentities(idNode);

        generator.writeArrayFieldStart(ENUM_KEY);
        populateEnumWithDerived(derivedIds, generator, context);
        generator.writeString(identityName);
        generator.writeEndArray();

        generator.writeStringField(TYPE_KEY, STRING_TYPE);

        return generator;
    }

    private boolean isImported(final IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(qnameModule);
    }

    private static String processBitsType(final BitsTypeDefinition bitsType,
                                          final JsonGenerator generator) throws IOException {
        generator.writeNumberField(MIN_ITEMS, 0);
        generator.writeBooleanField(UNIQUE_ITEMS_KEY, true);

        generator.writeArrayFieldStart(ENUM_KEY);
        final Collection<? extends Bit> bits = bitsType.getBits();
        String lastBit = "";
        for (final Bit bit : bits) {
            final var bitName = bit.getName();
            generator.writeString(bitName);
            lastBit = bitName;
        }
        generator.writeEndArray();
        generator.writeStringField(DEFAULT_KEY, bits.size() != 0 ? bits.iterator().next() + " " + lastBit : "");
        return STRING_TYPE;
    }

    private static String processStringType(final TypeDefinition<?> stringType, final JsonGenerator generator,
                                            final String nodeName) throws IOException {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        Optional<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraint();
        while (lengthConstraints.isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
            lengthConstraints = type.getLengthConstraint();
        }

        if (lengthConstraints.isPresent()) {
            final Range<Integer> range = lengthConstraints.get().getAllowedRanges().span();
            putIfNonNull(generator, MIN_LENGTH_KEY, range.lowerEndpoint());
            putIfNonNull(generator, MAX_LENGTH_KEY, range.upperEndpoint());
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
            generator.writeStringField(DEFAULT_KEY, defaultValue);
        } else {
            generator.writeStringField(DEFAULT_KEY, "Some " + nodeName);
        }
        return STRING_TYPE;
    }

    private static String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,
            final JsonGenerator generator) throws IOException {
        final Optional<Number> maybeLower = leafTypeDef.getRangeConstraint()
                .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            if (maybeLower.isPresent()) {
                setDefaultValue(generator, ((Decimal64) maybeLower.get()).decimalValue());
            }
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
                || leafTypeDef instanceof Uint16TypeDefinition
                || leafTypeDef instanceof Int8TypeDefinition
                || leafTypeDef instanceof Int16TypeDefinition
                || leafTypeDef instanceof Int32TypeDefinition) {
            generator.writeStringField(FORMAT_KEY, INT32_FORMAT);
            if (maybeLower.isPresent()) {
                setDefaultValue(generator,  maybeLower.get().intValue());
            }
        } else if (leafTypeDef instanceof Uint32TypeDefinition
                || leafTypeDef instanceof Int64TypeDefinition) {
            generator.writeStringField(FORMAT_KEY, INT64_FORMAT);
            if (maybeLower.isPresent()) {
                setDefaultValue(generator,  maybeLower.get().longValue());
            }
        } else {
            //uint64
            setDefaultValue(generator, 0);
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

    private static String processInstanceIdentifierType(final DataSchemaNode node, final JsonGenerator generator,
                                                        final EffectiveModelContext schemaContext) throws IOException {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.get().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            if (container.isPresent()) {
                setDefaultValue(generator, String.format("/%s:%s", module.get().getPrefix(), container.get().getQName()
                        .getLocalName()));
            }
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

    private static JsonGenerator buildXmlParameter(final String name, final JsonGenerator generator,
            final SchemaNode node) throws IOException {
        generator.writeObjectFieldStart(name);
        final QName qName = node.getQName();
        generator.writeStringField(NAME_KEY, qName.getLocalName());
        generator.writeStringField(NAME_KEY, qName.getNamespace().toString());
        generator.writeEndObject();
        return generator;
    }

    private static void putIfNonNull(final JsonGenerator generator, final String key, final Number number)
            throws IOException {
        if (key != null && number != null) {
            generator.writeFieldName(key);
            if (number instanceof Double doubleNumber) {
                generator.writeNumber(doubleNumber);
            } else if (number instanceof Float floatNumber) {
                generator.writeNumber(floatNumber);
            } else if (number instanceof Integer intNumber) {
                generator.writeNumber(intNumber);
            } else if (number instanceof Short shortNumber) {
                generator.writeNumber(shortNumber);
            } else if (number instanceof Long longNumber) {
                generator.writeNumber(longNumber);
            }
        }
    }

    private static void putIfNonNull(final JsonGenerator generator, final String key, final String value)
            throws IOException {
        if (key != null && value != null) {
            generator.writeStringField(key, value);
        }
    }

    private static void setRequiredIfNotEmpty(final JsonGenerator generator, final Set<String> required)
            throws IOException {
        if (required.size() > 0) {
            generator.writeArrayFieldStart(REQUIRED_KEY);
            generator.writeArray(required.toArray(new String[0]), 1, required.size());
            generator.writeEndArray();
        }
    }

    private static void setDefaultValue(final JsonGenerator generator, final String value) throws IOException {
        generator.writeStringField(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final JsonGenerator generator, final Integer value) throws IOException {
        generator.writeNumberField(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final JsonGenerator generator, final Long value) throws IOException {
        generator.writeNumberField(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final JsonGenerator generator, final BigDecimal value) throws IOException {
        generator.writeNumberField(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final JsonGenerator generator, final Boolean value) throws IOException {
        generator.writeBooleanField(DEFAULT_KEY, value);
    }
}
