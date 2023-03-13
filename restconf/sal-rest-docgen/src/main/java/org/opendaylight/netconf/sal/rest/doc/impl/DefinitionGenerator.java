/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.impl.BaseYangOpenApiGenerator.MODULE_NAME_SUFFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.COMPONENTS_PREFIX;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.CONFIG;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.NAME_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.XML_KEY;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.mifmif.common.regex.Generex;
import java.io.IOException;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangOpenApiGenerator.MapperGeneratorRecord;
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
import org.opendaylight.yangtools.yang.model.api.ElementCountConstraint;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
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
        convertToJsonSchema(value.module(), value.schemaContext(), gen, value.definitionNames(),
                value.isForSingleModule());
        gen.writeEndObject();
    }

    /**
     * Creates Json schema definitions from provided module according to OpenApi spec.
     *
     * @param module            - Yang module to be converted
     * @param schemaContext     - SchemaContext of all Yang files used by Api Doc
     * @param generator         - Appending here new Json nodes
     * @param definitionNames   - Store for definition names
     * @param isForSingleModule - If true, creates also json node for module
     * @return JsonGenerator containing data used for creating examples and definitions in Api Doc
     * @throws IOException if I/O operation fails
     */
    public JsonGenerator convertToJsonSchema(final Module module, final EffectiveModelContext schemaContext,
            final JsonGenerator generator, final DefinitionNames definitionNames, final boolean isForSingleModule)
            throws IOException {
        qnameModule = module.getQNameModule();

        processIdentities(module, generator, definitionNames, schemaContext);
        processContainersAndLists(module, generator, definitionNames, schemaContext);
        processRPCs(module, generator, definitionNames, schemaContext);

        if (isForSingleModule) {
            processModule(module, generator, definitionNames, schemaContext);
        }

        return generator;
    }

    private void processModule(final Module module, final JsonGenerator generator,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext) throws IOException {
        final String moduleName = module.getName();
        final String definitionName = moduleName + MODULE_NAME_SUFFIX;
        generator.writeObjectFieldStart(definitionName); // definition
        generator.writeStringField(TITLE_KEY, definitionName);
        generator.writeStringField(TYPE_KEY, OBJECT_TYPE);
        generator.writeObjectFieldStart(PROPERTIES_KEY); // properties
        DefinitionObject definitionObject = new DefinitionObject();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode node : module.getChildNodes()) {
            stack.enterSchemaTree(node.getQName());
            final String localName = node.getQName().getLocalName();
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    for (final DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        generator.writeObjectFieldStart(localName);  // childNodeProperties
                        final String ref = COMPONENTS_PREFIX
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
                } else if (node instanceof LeafSchemaNode leafNode) {
                    /*
                        Add module name prefix to property name, when ServiceNow can process colons(second parameter
                        of processLeafNode).
                     */

                    processLeafNode(leafNode, localName, stack, definitionNames, definitionObject);
                }
            }
            stack.exit();
        }
        generator.writeEndObject();
        generator.writeStringField(DESCRIPTION_KEY, module.getDescription().orElse(""));
        generator.writeEndObject();
        definitionObject.writeDataToJsonGenerator(generator);
    }

    private void processContainersAndLists(final Module module, final JsonGenerator gen,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                DefinitionObject definitionObject = new DefinitionObject();
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitionNames,
                        stack, definitionObject);
                definitionObject.writeDataToJsonGenerator(gen);

                definitionObject = new DefinitionObject();
                processActionNodeContainer(childNode, moduleName, definitionNames, stack, definitionObject);
                definitionObject.writeDataToJsonGenerator(gen);
            }
            stack.exit();
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final DefinitionObject defObj)
            throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, definitionNames, stack, defObj);
            stack.exit();
        }
    }

    private void processRPCs(final Module module, final JsonGenerator gen, final DefinitionNames definitionNames,
            final EffectiveModelContext schemaContext) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        final DefinitionObject definitionObject = new DefinitionObject();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, definitionNames, stack, definitionObject);
            stack.exit();
        }
        definitionObject.writeDataToJsonGenerator(gen);
    }

    private void processOperations(final OperationDefinition operationDef, final String parentName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final DefinitionObject defObj)
            throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitionNames, stack,
                defObj);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitionNames, stack,
                defObj);
    }

    private void processOperationInputOutput(final ContainerLike container, final String operationName,
            final String parentName, final boolean isInput, final DefinitionNames definitionNames,
            final SchemaInferenceStack stack, final DefinitionObject defObj) throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final DefinitionObject childSchemaObj = new DefinitionObject(defObj);
            processChildren(container.getChildNodes(), parentName, definitionNames, stack, childSchemaObj);

            childSchemaObj.addData(TYPE_KEY, OBJECT_TYPE);
            final DefinitionObject xmlObj = new DefinitionObject(childSchemaObj);
            xmlObj.addData(NAME_KEY, isInput ? INPUT : OUTPUT);
            childSchemaObj.addData(XML_KEY, xmlObj);
            childSchemaObj.addData(TITLE_KEY, filename);
            final String discriminator = definitionNames.pickDiscriminator(container, List.of(filename));
            defObj.addData(filename + discriminator, childSchemaObj);
        }
        stack.exit();
    }

    private static DefinitionObject processInnerData(final String filename, final String discriminator,
            final SchemaNode schemaNode, final DefinitionObject defObj) {
        final DefinitionObject dataNodePropObj = new DefinitionObject(defObj);

        final String name = filename + discriminator;
        final String ref = COMPONENTS_PREFIX + name;

        if (schemaNode instanceof ListSchemaNode) {
            dataNodePropObj.addData(TYPE_KEY, ARRAY_TYPE);
            final DefinitionObject itemsObj = new DefinitionObject(dataNodePropObj);
            itemsObj.addData(REF_KEY, ref);
            dataNodePropObj.addData(ITEMS_KEY, itemsObj);
            dataNodePropObj.addData(DESCRIPTION_KEY, schemaNode.getDescription().orElse(""));
        } else {
             /*
                Description can't be added, because nothing allowed alongside $ref.
                allOf is not an option, because ServiceNow can't parse it.
              */
            dataNodePropObj.addData(REF_KEY, ref);
        }
        return dataNodePropObj;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the OpenApi JSON spec.
     *
     * @param module            The module from which the identity stmt will be processed
     * @param generator         The JsonGenerator in which the parsed identity will be put as a 'model' obj
     * @param definitionNames   Store for definition names
     * @param context           SchemaContext of all Yang files used by Api Doc
     * @throws IOException      if JsonGenerator I/O operation fails
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
            final List<String> enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final JsonGenerator generator, final EffectiveModelContext context) throws IOException {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            generator.writeString(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), generator, context);
        }
    }

    private DefinitionObject processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final DefinitionObject defObj)
            throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = schemaNode.getQName().getLocalName();
            final DefinitionObject root = defObj.getRoot();
            final DefinitionObject childSchemaObj = new DefinitionObject(root);
            final String nameAsParent = parentName + "_" + localName;
            final DefinitionObject propertiesObj = processChildren(containerChildren, parentName + "_" + localName,
                            definitionNames, stack, childSchemaObj);

            final String nodeName = parentName + CONFIG + "_" + localName;
            final String parentNameConfigLocalName = parentName + CONFIG + "_" + localName;

            final String description = schemaNode.getDescription().orElse("");
            final String discriminator;

            if (!definitionNames.isListedNode(schemaNode)) {
                final List<String> names = List.of(parentNameConfigLocalName, nameAsParent);
                discriminator = definitionNames.pickDiscriminator(schemaNode, names);
            } else {
                discriminator = definitionNames.getDiscriminator(schemaNode);
            }

            childSchemaObj.addData(TYPE_KEY, OBJECT_TYPE);
            childSchemaObj.addData(PROPERTIES_KEY, propertiesObj);
            childSchemaObj.addData(TITLE_KEY, nodeName);
            childSchemaObj.addData(DESCRIPTION_KEY, description);

            final String defName = nodeName + discriminator;
            childSchemaObj.addData(XML_KEY, buildXmlParameter(schemaNode, childSchemaObj));
            root.addData(defName, childSchemaObj);

            return processInnerData(nodeName, discriminator, schemaNode, defObj);
        }
        return null;
    }

    /**
     * Processes the nodes.
     */
    private DefinitionObject processChildren(final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final DefinitionObject defObj)
            throws IOException {
        final DefinitionObject propertiesObj = new DefinitionObject(defObj);
        for (final DataSchemaNode node : nodes) {
            processChildNode(node, parentName, definitionNames, stack, propertiesObj);
        }
        defObj.addData(PROPERTIES_KEY, propertiesObj);
        return propertiesObj;
    }

    private void processChildNode(final DataSchemaNode node, final String parentName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final DefinitionObject defObj)
            throws IOException {
        stack.enterSchemaTree(node.getQName());

        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();
        if (node instanceof LeafSchemaNode leaf) {
            processLeafNode(leaf, name, stack, definitionNames, defObj);

        } else if (node instanceof AnyxmlSchemaNode anyxml) {
            processAnyXMLNode(anyxml, name, defObj);

        } else if (node instanceof AnydataSchemaNode anydata) {
            processAnydataNode(anydata, name, defObj);

        } else {
            final DefinitionObject propertyObj;
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                propertyObj = processDataNodeContainer((DataNodeContainer) node, parentName, definitionNames, stack,
                        defObj);
            } else if (node instanceof LeafListSchemaNode leafList) {
                propertyObj = processLeafListNode(leafList, stack, definitionNames, defObj);

            } else if (node instanceof ChoiceSchemaNode choice) {
                if (!choice.getCases().isEmpty()) {
                    CaseSchemaNode caseSchemaNode = choice.getDefaultCase()
                            .orElse(choice.getCases().stream()
                                    .findFirst().orElseThrow());
                    stack.enterSchemaTree(caseSchemaNode.getQName());
                    for (final DataSchemaNode childNode : caseSchemaNode.getChildNodes()) {
                        processChildNode(childNode, parentName, definitionNames, stack, defObj);
                    }
                    stack.exit();
                }
                propertyObj = null;

            } else {
                throw new IllegalArgumentException("Unknown DataSchemaNode type: " + node.getClass());
            }
            if (propertyObj != null) {
                defObj.addData(name, propertyObj);
            }
        }

        stack.exit();
    }

    private DefinitionObject processLeafListNode(final LeafListSchemaNode listNode, final SchemaInferenceStack stack,
            final DefinitionNames definitionNames, final DefinitionObject defObj) {
        final DefinitionObject propsObj = new DefinitionObject(defObj);
        propsObj.addData(TYPE_KEY, ARRAY_TYPE);
        final DefinitionObject itemsValObj = new DefinitionObject(defObj);
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        processElementCount(optConstraint, propsObj);

        processTypeDef(listNode.getType(), listNode, stack, definitionNames, itemsValObj);
        defObj.addData(ITEMS_KEY, itemsValObj);
        defObj.addData(DESCRIPTION_KEY, listNode.getDescription().orElse(""));
        propsObj.addData(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return propsObj;
    }

    private static void processElementCount(final Optional<ElementCountConstraint> constraint,
            final DefinitionObject props) {
        if (constraint.isPresent()) {
            final ElementCountConstraint constr = constraint.orElseThrow();
            final Integer minElements = constr.getMinElements();
            if (minElements != null) {
                props.addData(MIN_ITEMS, minElements);
            }
            final Integer maxElements = constr.getMaxElements();
            if (maxElements != null) {
                props.addData(MAX_ITEMS, maxElements);
            }
        }
    }

    private void processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
            final SchemaInferenceStack stack, final DefinitionNames definitionNames, final DefinitionObject defObj) {
        final DefinitionObject propertyObj = new DefinitionObject(defObj);
        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            Description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            propertyObj.addData(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, stack, definitionNames, propertyObj);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj));
        defObj.addData(jsonLeafName, propertyObj);
    }

    private static void processAnydataNode(final AnydataSchemaNode leafNode, final String name,
            final DefinitionObject defObj) {
        final DefinitionObject propertyObj = new DefinitionObject(defObj);
        final String leafDescription = leafNode.getDescription().orElse("");
        propertyObj.addData(DEFAULT_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(propertyObj, String.format("<%s> ... </%s>", localName, localName));
        propertyObj.addData(TYPE_KEY, STRING_TYPE);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj));
        defObj.addData(name, propertyObj);
    }

    private static void processAnyXMLNode(final AnyxmlSchemaNode leafNode, final String name,
            final DefinitionObject defObj) {
        final DefinitionObject propertyObj = new DefinitionObject(defObj);
        final String leafDescription = leafNode.getDescription().orElse("");
        propertyObj.addData(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(propertyObj, String.format("<%s> ... </%s>", localName, localName));
        propertyObj.addData(TYPE_KEY, STRING_TYPE);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj));
        defObj.addData(name, propertyObj);
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
            final SchemaInferenceStack stack, final DefinitionNames definitionNames, final DefinitionObject defObj) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(defObj);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, defObj);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, defObj);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef, definitionNames,
                    stack.getEffectiveModelContext(), defObj);

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, node.getQName().getLocalName(), defObj);

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processTypeDef(stack.resolveLeafref((LeafrefTypeDefinition) leafTypeDef), node, stack,
                    definitionNames, defObj);
        } else if (leafTypeDef instanceof BooleanTypeDefinition) {
            jsonType = BOOLEAN_TYPE;
            setDefaultValue(defObj, true);
        } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
            jsonType = processNumberType((RangeRestrictedTypeDefinition<?, ?>) leafTypeDef, defObj);
        } else if (leafTypeDef instanceof InstanceIdentifierTypeDefinition) {
            jsonType = processInstanceIdentifierType(node, stack.getEffectiveModelContext(), defObj);
        } else {
            jsonType = STRING_TYPE;
        }
        if (!(leafTypeDef instanceof IdentityrefTypeDefinition)) {
            putIfNonNull(defObj, TYPE_KEY, jsonType);
            if (leafTypeDef.getDefaultValue().isPresent()) {
                final Object defaultValue = leafTypeDef.getDefaultValue().orElseThrow();
                if (defaultValue instanceof String stringDefaultValue) {
                    if (leafTypeDef instanceof BooleanTypeDefinition) {
                        setDefaultValue(defObj, Boolean.valueOf(stringDefaultValue));
                    } else if (leafTypeDef instanceof DecimalTypeDefinition
                            || leafTypeDef instanceof Uint64TypeDefinition) {
                        setDefaultValue(defObj, new BigDecimal(stringDefaultValue));
                    } else if (leafTypeDef instanceof RangeRestrictedTypeDefinition) {
                        //uint8,16,32 int8,16,32,64
                        if (isHexadecimalOrOctal((RangeRestrictedTypeDefinition<?, ?>)leafTypeDef)) {
                            setDefaultValue(defObj, stringDefaultValue);
                        } else {
                            setDefaultValue(defObj, Long.valueOf(stringDefaultValue));
                        }
                    } else {
                        setDefaultValue(defObj, stringDefaultValue);
                    }
                } else {
                    //we should never get here. getDefaultValue always gives us string
                    setDefaultValue(defObj, defaultValue.toString());
                }
            }
        }
        return jsonType;
    }

    private static String processBinaryType(final DefinitionObject defObj) {
        defObj.addData(FORMAT_KEY, "byte");
        return STRING_TYPE;
    }

    private static String processEnumType(final EnumTypeDefinition enumLeafType, final DefinitionObject defObj) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        final String[] enumString = enumPairs.stream()
                .map(EnumPair::getName)
                .toArray(String[]::new);
        defObj.addData(ENUM_KEY, enumString);
        setDefaultValue(defObj, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext,
            final DefinitionObject defObj) {
        final String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, definitionNames, schemaContext, defObj);
        } else {
            final SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        defObj.addData(REF_KEY, COMPONENTS_PREFIX + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
            final DefinitionNames definitionNames, final EffectiveModelContext context, final DefinitionObject defObj) {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        final String identityName = idNode.getQName().getLocalName();
        if (!definitionNames.isListedNode(idNode)) {
            final DefinitionObject definitionObject = buildIdentityDefObject(idNode, context, defObj);
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            final String name = identityName + discriminator;
            defObj.getRoot().addData(name, definitionObject);
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

    private static DefinitionObject buildIdentityDefObject(final IdentitySchemaNode idNode,
            final EffectiveModelContext context, final DefinitionObject defObj) {
        final DefinitionObject identityDefObj = new DefinitionObject(defObj);
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);
        identityDefObj.addData(TITLE_KEY, identityName);
        identityDefObj.addData(DESCRIPTION_KEY, idNode.getDescription().orElse(""));

        final Collection<? extends IdentitySchemaNode> derivedIds = context.getDerivedIdentities(idNode);

        final ArrayList<String> enumPayloads = new ArrayList<>();
        enumPayloads.add(identityName);
        populateEnumWithDerived(derivedIds, enumPayloads, context);

        identityDefObj.addData(ENUM_KEY, enumPayloads.toArray(new String[0]));
        identityDefObj.addData(TYPE_KEY, STRING_TYPE);
        return identityDefObj;
    }

    private boolean isImported(final IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(qnameModule);
    }

    private static String processBitsType(final BitsTypeDefinition bitsType, final DefinitionObject defObj) {
        defObj.addData(MIN_ITEMS, 0);
        defObj.addData(MIN_ITEMS, true);
        final Collection<? extends Bit> bits = bitsType.getBits();
        final String[] bitsArray = bits.stream()
                .map(Bit::getName)
                .toArray(String[]::new);
        defObj.addData(ENUM_KEY, bitsArray);
        defObj.addData(DEFAULT_KEY, Arrays.stream(bitsArray).findFirst().orElse("")
                + " " + bitsArray[bitsArray.length - 1]);
        return STRING_TYPE;
    }

    private static String processStringType(final TypeDefinition<?> stringType, final String nodeName,
            final DefinitionObject defObj) {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        Optional<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraint();
        while (lengthConstraints.isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
            lengthConstraints = type.getLengthConstraint();
        }

        if (lengthConstraints.isPresent()) {
            final Range<Integer> range = lengthConstraints.orElseThrow().getAllowedRanges().span();
            putIfNonNull(defObj, MIN_LENGTH_KEY, range.lowerEndpoint());
            putIfNonNull(defObj, MAX_LENGTH_KEY, range.upperEndpoint());
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
            setDefaultValue(defObj, defaultValue);
        } else {
            setDefaultValue(defObj, "Some " + nodeName);
        }
        return STRING_TYPE;
    }

    private static String processNumberType(final RangeRestrictedTypeDefinition<?, ?> leafTypeDef,
            final DefinitionObject defObj) {
        final Optional<Number> maybeLower = leafTypeDef.getRangeConstraint()
                .map(RangeConstraint::getAllowedRanges).map(RangeSet::span).map(Range::lowerEndpoint);

        if (isHexadecimalOrOctal(leafTypeDef)) {
            return STRING_TYPE;
        }

        if (leafTypeDef instanceof DecimalTypeDefinition) {
            maybeLower.ifPresent(number -> setDefaultValue(defObj, ((Decimal64) number).decimalValue()));
            return NUMBER_TYPE;
        }
        if (leafTypeDef instanceof Uint8TypeDefinition
                || leafTypeDef instanceof Uint16TypeDefinition
                || leafTypeDef instanceof Int8TypeDefinition
                || leafTypeDef instanceof Int16TypeDefinition
                || leafTypeDef instanceof Int32TypeDefinition) {
            defObj.addData(FORMAT_KEY, INT32_FORMAT);
            maybeLower.ifPresent(number -> setDefaultValue(defObj, Integer.valueOf(number.toString())));
        } else if (leafTypeDef instanceof Uint32TypeDefinition
                || leafTypeDef instanceof Int64TypeDefinition) {
            defObj.addData(FORMAT_KEY, INT64_FORMAT);
            maybeLower.ifPresent(number -> setDefaultValue(defObj, Long.valueOf(number.toString())));
        } else {
            //uint64
            setDefaultValue(defObj, 0);
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

    private static String processInstanceIdentifierType(final DataSchemaNode node,
            final EffectiveModelContext schemaContext, final DefinitionObject defObj) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.orElseThrow().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            container.ifPresent(c -> setDefaultValue(defObj, String.format("/%s:%s", module.orElseThrow().getPrefix(),
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

    private static DefinitionObject buildXmlParameter(final SchemaNode node, final DefinitionObject defObj) {
        final DefinitionObject definitionObject = new DefinitionObject(defObj);
        final QName qName = node.getQName();
        definitionObject.addData(NAME_KEY, qName.getLocalName());
        definitionObject.addData(NAMESPACE_KEY, qName.getNamespace().toString());
        return definitionObject;
    }

    private static void putIfNonNull(final DefinitionObject defObj, final String key, final Number number) {
        if (key != null && number != null) {
            if (number instanceof Double) {
                defObj.addData(key, (Double) number);
            } else if (number instanceof Float) {
                defObj.addData(key, (Float) number);
            } else if (number instanceof Integer) {
                defObj.addData(key, (Integer) number);
            } else if (number instanceof Short) {
                defObj.addData(key, (Short) number);
            } else if (number instanceof Long) {
                defObj.addData(key, (Long) number);
            }
        }
    }

    private static void putIfNonNull(final DefinitionObject defObj, final String key, final String value) {
        if (key != null && value != null) {
            defObj.addData(key, value);
        }
    }

    private static void setDefaultValue(final DefinitionObject property, final String value) {
        property.addData(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final DefinitionObject property, final Integer value) {
        property.addData(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final DefinitionObject property, final Long value) {
        property.addData(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final DefinitionObject property, final BigDecimal value) {
        property.addData(DEFAULT_KEY, value);
    }

    private static void setDefaultValue(final DefinitionObject property, final Boolean value) {
        property.addData(DEFAULT_KEY, value);
    }
}
