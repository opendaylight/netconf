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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionObject.DataType;
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
                                          final OAversion oaversion, final boolean isForSingleModule)
            throws IOException {
        topLevelModule = module;
        final DefinitionObject base = new DefinitionObject();
        processIdentities(module, base, definitionNames, schemaContext);
        processContainersAndLists(module, base, definitionNames, schemaContext, oaversion);
        processRPCs(module, base, definitionNames, schemaContext, oaversion);

        if (isForSingleModule) {
            processModule(module, base, definitionNames, schemaContext, oaversion);
        }

        final ObjectNode jsonNodes = base.convertToObjectNode();
        final Iterator<Entry<String, JsonNode>> fields = jsonNodes.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            definitions.set(field.getKey(), field.getValue());
        }

        return definitions;
    }

    public ObjectNode convertToJsonSchema(final Module module, final EffectiveModelContext schemaContext,
                                          final DefinitionNames definitionNames, final OAversion oaversion,
                                          final boolean isForSingleModule)
            throws IOException {
        final ObjectNode definitions = JsonNodeFactory.instance.objectNode();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(module.getName() + MODULE_NAME_SUFFIX);
        }
        return convertToJsonSchema(module, schemaContext, definitions, definitionNames, oaversion, isForSingleModule);
    }

    private void processModule(final Module module, final DefinitionObject defObj,final DefinitionNames defNames,
                               final EffectiveModelContext schemaContext, final OAversion oaversion) {
        DefinitionObject definition = new DefinitionObject(defObj);
        DefinitionObject properties = new DefinitionObject(definition);

        final String moduleName = module.getName();
        final String definitionName = moduleName + MODULE_NAME_SUFFIX;
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode node : module.getChildNodes()) {
            stack.enterSchemaTree(node.getQName());
            final String localName = node.getQName().getLocalName();
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    for (final DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        DefinitionObject childNodeProp = new DefinitionObject(properties);

                        final String ref = getAppropriateModelPrefix(oaversion)
                                + moduleName + CONFIG
                                + "_" + localName
                                + defNames.getDiscriminator(node);

                        if (node instanceof ListSchemaNode) {
                            childNodeProp.addData(TYPE_KEY, ARRAY_TYPE);
                            DefinitionObject items = new DefinitionObject(childNodeProp);
                            items.addData(REF_KEY, ref);
                            childNodeProp.addData(ITEMS_KEY, items);
                            childNodeProp.addData(DESCRIPTION_KEY, childNode.getDescription().orElse(""));
                            childNodeProp.addData(TITLE_KEY, localName + CONFIG);
                        } else {
                         /*
                            Description can't be added, because nothing allowed alongside $ref.
                            allOf is not an option, because ServiceNow can't parse it.
                          */
                            childNodeProp.addData(REF_KEY, ref);
                        }
                        //add module name prefix to property name, when ServiceNow can process colons
                        properties.addData(localName, childNodeProp);
                    }
                } else if (node instanceof LeafSchemaNode) {
                    /*
                        Add module name prefix to property name, when ServiceNow can process colons(second parameter
                        of processLeafNode).
                     */
                    processLeafNode((LeafSchemaNode) node, localName, stack, defNames, oaversion, defObj);
                }
            }
            stack.exit();
        }
        definition.addData(TITLE_KEY, definitionName);
        definition.addData(TYPE_KEY, OBJECT_TYPE);
        definition.addData(PROPERTIES_KEY, properties);
        definition.addData(DESCRIPTION_KEY, module.getDescription().orElse(""));

        defObj.addData(definitionName, definition);
    }

    private void processContainersAndLists(final Module module, final DefinitionObject defObj,
            final DefinitionNames definitionNames, final EffectiveModelContext schemaContext, final OAversion oaversion)
                throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final DataSchemaNode childNode : module.getChildNodes()) {
            stack.enterSchemaTree(childNode.getQName());
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitionNames,
                            true, stack, oaversion, defObj);
                }
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitionNames,
                        false, stack, oaversion, defObj);
                processActionNodeContainer(childNode, moduleName, definitionNames, stack, oaversion, defObj);
            }
            stack.exit();
        }
    }


    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final DefinitionNames definitionNames,
                                            final SchemaInferenceStack stack, final OAversion oaversion,
                                            final DefinitionObject defObj)
            throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            processOperations(actionDef, moduleName, definitionNames, stack, oaversion, defObj);
            stack.exit();
        }
    }

    private void processRPCs(final Module module, final DefinitionObject defObj, final DefinitionNames definitionNames,
                             final EffectiveModelContext schemaContext, final OAversion oaversion) throws IOException {
        final String moduleName = module.getName();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(schemaContext);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            stack.enterSchemaTree(rpcDefinition.getQName());
            processOperations(rpcDefinition, moduleName, definitionNames, stack, oaversion, defObj);
            stack.exit();
        }
    }

    private void processOperations(final OperationDefinition operationDef, final String parentName,
            final DefinitionNames definitionNames, final SchemaInferenceStack stack, final OAversion oaversion,
            final DefinitionObject defObj) throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true,
                definitionNames, stack, oaversion, defObj);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false,
                definitionNames, stack, oaversion, defObj);
    }

    private void processOperationInputOutput(final ContainerLike container, final String operationName,
                                             final String parentName, final boolean isInput,
                                             final DefinitionNames definitionNames,
                                             final SchemaInferenceStack stack, final OAversion oaversion,
                                             final DefinitionObject defObj)
            throws IOException {
        stack.enterSchemaTree(container.getQName());
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            DefinitionObject root = defObj.getRoot();
            DefinitionObject childSchemaObj = new DefinitionObject(root);
            processChildren(container.getChildNodes(), parentName, definitionNames,
                    false, stack, oaversion, childSchemaObj);

            childSchemaObj.addData(TYPE_KEY, OBJECT_TYPE);
            DefinitionObject xmlObj = new DefinitionObject(childSchemaObj);
            xmlObj.addData(NAME_KEY, isInput ? INPUT : OUTPUT);
            childSchemaObj.addData(XML_KEY, xmlObj);
            childSchemaObj.addData(TITLE_KEY, filename);
            final String discriminator =
                    definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            root.addData(filename + discriminator, childSchemaObj);

            processTopData(filename, discriminator, container, oaversion , root);
        }
        stack.exit();
    }

    private static DefinitionObject processTopData(final String filename, final String discriminator,
            final SchemaNode schemaNode, final OAversion oaversion,
            final DefinitionObject defObj) {

        DefinitionObject finalChildObj = new DefinitionObject(defObj);
        DefinitionObject propertiesObj = new DefinitionObject(finalChildObj);
        DefinitionObject dataNodePropObj = new DefinitionObject(propertiesObj);

        final String name = filename + discriminator;
        final String ref = getAppropriateModelPrefix(oaversion) + name;
        final String topName = filename + TOP;

        if (schemaNode instanceof ListSchemaNode) {
            dataNodePropObj.addData(TYPE_KEY, ARRAY_TYPE);
            DefinitionObject itemsObj = new DefinitionObject(dataNodePropObj);
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
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        propertiesObj.addData(schemaNode.getQName().getLocalName(), dataNodePropObj);

        finalChildObj.addData(TYPE_KEY, OBJECT_TYPE);
        finalChildObj.addData(PROPERTIES_KEY, propertiesObj);
        finalChildObj.addData(TITLE_KEY, topName);

        defObj.addData(topName + discriminator, finalChildObj);

        return dataNodePropObj;
    }

    /**
     * Processes the 'identity' statement in a yang model and maps it to a 'model' in the Swagger JSON spec.
     * @param module          The module from which the identity stmt will be processed
     * @param definitions     The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames Store for definition names
     */
    private static DefinitionObject processIdentities(final Module module, DefinitionObject defObj,
                                          final DefinitionNames definitionNames, final EffectiveModelContext context) {
        final String moduleName = module.getName();
        final Collection<? extends IdentitySchemaNode> idNodes = module.getIdentities();
        LOG.debug("Processing Identities for module {} . Found {} identity statements", moduleName, idNodes.size());
        for (final IdentitySchemaNode idNode : idNodes) {
            final DefinitionObject identityDefObject = buildIdentityDefObject(idNode, context, defObj);
            final String idName = idNode.getQName().getLocalName();
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(idName));
            final String name = idName + discriminator;
            defObj.addData(name, identityDefObject);
        }
        return defObj;
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
                                                final ArrayNode enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private static void populateEnumWithDerived(final Collection<? extends IdentitySchemaNode> derivedIds,
            final List<String> enumPayload, final EffectiveModelContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private DefinitionObject processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
            final DefinitionNames definitionNames, final boolean isConfig, final SchemaInferenceStack stack,
            final OAversion oaversion, final DefinitionObject defObj) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = schemaNode.getQName().getLocalName();
            final DefinitionObject root = defObj.getRoot();
            final DefinitionObject childSchemaObj = new DefinitionObject(root);
            final String nameAsParent = parentName + "_" + localName;
            final DefinitionObject propertiesObj =
                    processChildren(containerChildren, parentName + "_" + localName,
                            definitionNames, isConfig, stack, oaversion, childSchemaObj);

            final String nodeName = parentName + (isConfig ? CONFIG : "") + "_" + localName;
            final String postNodeName = parentName + CONFIG + "_" + localName + POST_SUFFIX;
            final String postXmlNodeName = postNodeName + XML_SUFFIX;
            final String parentNameConfigLocalName = parentName + CONFIG + "_" + localName;

            final String description = schemaNode.getDescription().orElse("");
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

            if (isConfig) {
                final DefinitionObject postSchemaObj = createPostJsonSchema(schemaNode, postNodeName, description,
                        propertiesObj);
                String truePostNodeName = postNodeName + discriminator;
                root.addData(truePostNodeName, postSchemaObj);

                DefinitionObject postXmlObj = new DefinitionObject(root);
                postXmlObj.addData(REF_KEY, getAppropriateModelPrefix(oaversion) + truePostNodeName);
                root.addData(postXmlNodeName + discriminator, postXmlObj);
            }
            childSchemaObj.addData(TYPE_KEY, OBJECT_TYPE);
            childSchemaObj.addData(PROPERTIES_KEY, propertiesObj);
            childSchemaObj.addData(TITLE_KEY, nodeName);
            childSchemaObj.addData(DESCRIPTION_KEY, description);

            final String defName = nodeName + discriminator;
            childSchemaObj.addData(XML_KEY, buildXmlParameter(schemaNode, childSchemaObj, XML_KEY));
            root.addData(defName, childSchemaObj);

            return processTopData(nodeName, discriminator, schemaNode, oaversion, root);
        }
        return null;
    }

    private static DefinitionObject createPostJsonSchema(final SchemaNode dataNode,
            final String postNodeName, final String description, final DefinitionObject propertiesObj) {
        DefinitionObject postSchemaObj = new DefinitionObject(propertiesObj.getRoot());
        final DefinitionObject postItemPropObj;
        if (dataNode instanceof ListSchemaNode) {
            postItemPropObj = createListItemProperties(propertiesObj, postSchemaObj, (ListSchemaNode) dataNode);
        } else {
            postItemPropObj = propertiesObj.createCopy(postSchemaObj);
        }
        postSchemaObj.addData(TYPE_KEY, OBJECT_TYPE);
        postSchemaObj.addData(PROPERTIES_KEY, postItemPropObj);
        postSchemaObj.addData(TITLE_KEY, postNodeName);
        postSchemaObj.addData(DESCRIPTION_KEY, description);
        postSchemaObj.addData(XML_KEY, buildXmlParameter(dataNode, postSchemaObj, XML_KEY));

        return postSchemaObj;
    }

    private static DefinitionObject createListItemProperties(final DefinitionObject defObj,
            final DefinitionObject parent, final ListSchemaNode listNode) {
        DefinitionObject postItemPropertyObj = new DefinitionObject(parent);
        final List<QName> keyDefinition = listNode.getKeyDefinition();
        final Set<String> keys = listNode.getChildNodes().stream()
                .filter(node -> keyDefinition.contains(node.getQName()))
                .map(node -> node.getQName().getLocalName())
                .collect(Collectors.toSet());

        TreeMap<String, DataType<?>> data = defObj.getData();
        for (Entry<String, DataType<?>> stringDataTypeEntry : data.entrySet()) {
            if (!keys.contains(stringDataTypeEntry.getKey())) {
                postItemPropertyObj.addData(stringDataTypeEntry.getKey(), stringDataTypeEntry.getValue());
            }
        }

        return postItemPropertyObj;
    }

    /**
     * Processes the nodes.
     */
    private DefinitionObject processChildren(
            final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion, final DefinitionObject defObj)
            throws IOException {
        final DefinitionObject propertiesObj = new DefinitionObject(defObj);
        for (final DataSchemaNode node : nodes) {
            if (!isConfig || node.isConfiguration()) {
                processChildNode(node, parentName, definitionNames, isConfig, stack,
                        oaversion, propertiesObj);
            }
        }
        defObj.addData(PROPERTIES_KEY, propertiesObj);
        return propertiesObj;
    }

    private void processChildNode(
            final DataSchemaNode node, final String parentName,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaInferenceStack stack, final OAversion oaversion,
            final DefinitionObject defObj) throws IOException {

        stack.enterSchemaTree(node.getQName());

        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
        final String name = node.getQName().getLocalName();
        if (node instanceof LeafSchemaNode leaf) {
            processLeafNode(leaf, name, stack, definitionNames, oaversion, defObj);

        } else if (node instanceof AnyxmlSchemaNode anyxml) {
            processAnyXMLNode(anyxml, name, defObj);

        } else if (node instanceof AnydataSchemaNode anydata) {
            processAnydataNode(anydata, name, defObj);

        } else {
            final DefinitionObject propertyObj;
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                propertyObj = processDataNodeContainer((DataNodeContainer) node, parentName,
                        definitionNames, isConfig, stack, oaversion, defObj);
                if (!isConfig) {
                    processActionNodeContainer(node, parentName, definitionNames, stack, oaversion, defObj);
                }
            } else if (node instanceof LeafListSchemaNode leafList) {
                propertyObj = processLeafListNode(leafList, stack, definitionNames, oaversion, defObj);

            } else if (node instanceof ChoiceSchemaNode choice) {
                for (final CaseSchemaNode variant : choice.getCases()) {
                    stack.enterSchemaTree(variant.getQName());
                    for (final DataSchemaNode childNode : variant.getChildNodes()) {
                        processChildNode(childNode, parentName, definitionNames, isConfig, stack, oaversion, defObj);
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
                                           final DefinitionNames definitionNames,
                                           final OAversion oaversion, final DefinitionObject defObj) {
        DefinitionObject propsObj = new DefinitionObject(defObj);
        propsObj.addData(TYPE_KEY, ARRAY_TYPE);
        DefinitionObject itemsValObj = new DefinitionObject(defObj);
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        processElementCount(optConstraint, propsObj);

        processTypeDef(listNode.getType(), listNode, stack, definitionNames, oaversion, itemsValObj);
        defObj.addData(ITEMS_KEY, itemsValObj);
        defObj.addData(DESCRIPTION_KEY, listNode.getDescription().orElse(""));
        propsObj.addData(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return propsObj;
    }

    private static void processElementCount(final Optional<ElementCountConstraint> constraint,
            final DefinitionObject props) {
        if (constraint.isPresent()) {
            final ElementCountConstraint constr = constraint.get();
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
                                       final SchemaInferenceStack stack,
                                       final DefinitionNames definitionNames, final OAversion oaversion,
                                       final DefinitionObject defObj) {
        DefinitionObject propertyObj = new DefinitionObject(defObj);
        final String leafDescription = leafNode.getDescription().orElse("");
        /*
            Description can't be added, because nothing allowed alongside $ref.
            allOf is not an option, because ServiceNow can't parse it.
        */
        if (!(leafNode.getType() instanceof IdentityrefTypeDefinition)) {
            propertyObj.addData(DESCRIPTION_KEY, leafDescription);
        }

        processTypeDef(leafNode.getType(), leafNode, stack, definitionNames, oaversion, propertyObj);
        propertyObj.setName(jsonLeafName);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj, XML_KEY));
        defObj.addData(jsonLeafName, propertyObj);
    }

    private static void processAnydataNode(final AnydataSchemaNode leafNode, final String name,
                                                 final DefinitionObject defObj) {
        DefinitionObject propertyObj = new DefinitionObject(defObj);

        final String leafDescription = leafNode.getDescription().orElse("");
        propertyObj.addData(DEFAULT_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(propertyObj, String.format("<%s> ... </%s>", localName, localName));
        propertyObj.addData(TYPE_KEY, STRING_TYPE);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj, XML_KEY));
        defObj.addData(name, propertyObj);
    }

    private static void processAnyXMLNode(final AnyxmlSchemaNode leafNode, final String name,
                                                final DefinitionObject defObj) {
        DefinitionObject propertyObj = new DefinitionObject(defObj);

        final String leafDescription = leafNode.getDescription().orElse("");
        propertyObj.addData(DESCRIPTION_KEY, leafDescription);

        final String localName = leafNode.getQName().getLocalName();
        setDefaultValue(propertyObj, String.format("<%s> ... </%s>", localName, localName));
        propertyObj.addData(TYPE_KEY, STRING_TYPE);
        propertyObj.addData(XML_KEY, buildXmlParameter(leafNode, propertyObj, XML_KEY));
        defObj.addData(name, propertyObj);
    }

    private String processTypeDef(final TypeDefinition<?> leafTypeDef, final DataSchemaNode node,
                                  final SchemaInferenceStack stack,
                                  final DefinitionNames definitionNames,
                                  final OAversion oaversion, final DefinitionObject defObj) {
        final String jsonType;
        if (leafTypeDef instanceof BinaryTypeDefinition) {
            jsonType = processBinaryType(defObj);

        } else if (leafTypeDef instanceof BitsTypeDefinition) {
            jsonType = processBitsType((BitsTypeDefinition) leafTypeDef, defObj);

        } else if (leafTypeDef instanceof EnumTypeDefinition) {
            jsonType = processEnumType((EnumTypeDefinition) leafTypeDef, defObj);

        } else if (leafTypeDef instanceof IdentityrefTypeDefinition) {
            jsonType = processIdentityRefType((IdentityrefTypeDefinition) leafTypeDef,
                    definitionNames, oaversion, stack.getEffectiveModelContext(), defObj);

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, node.getQName().getLocalName(), defObj);

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processTypeDef(stack.resolveLeafref((LeafrefTypeDefinition) leafTypeDef), node,
                stack, definitionNames, oaversion, defObj);
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
                final Object defaultValue = leafTypeDef.getDefaultValue().get();
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

    private static String processEnumType(final EnumTypeDefinition enumLeafType,
                                          final DefinitionObject defObj) {
        final List<EnumPair> enumPairs = enumLeafType.getValues();
        String[] enumString = enumPairs.stream()
                .map(EnumPair::getName)
                .toArray(String[]::new);
        defObj.addData(ENUM_KEY, enumString);
        setDefaultValue(defObj, enumLeafType.getValues().iterator().next().getName());
        return STRING_TYPE;
    }

    private String processIdentityRefType(final IdentityrefTypeDefinition leafTypeDef,
                                          final DefinitionNames definitionNames,
                                          final OAversion oaversion, final EffectiveModelContext schemaContext,
                                          final DefinitionObject defObj) {
        final String definitionName;
        if (isImported(leafTypeDef)) {
            definitionName = addImportedIdentity(leafTypeDef, definitionNames, schemaContext, defObj);
        } else {
            final SchemaNode node = leafTypeDef.getIdentities().iterator().next();
            definitionName = node.getQName().getLocalName() + definitionNames.getDiscriminator(node);
        }
        defObj.addData(REF_KEY, getAppropriateModelPrefix(oaversion) + definitionName);
        return STRING_TYPE;
    }

    private static String addImportedIdentity(final IdentityrefTypeDefinition leafTypeDef,
                                              final DefinitionNames definitionNames,
                                              final EffectiveModelContext context, DefinitionObject defObj) {
        final IdentitySchemaNode idNode = leafTypeDef.getIdentities().iterator().next();
        final String identityName = idNode.getQName().getLocalName();
        if (!definitionNames.isListedNode(idNode)) {
            DefinitionObject definitionObject = buildIdentityDefObject(idNode, context, defObj);
            final String discriminator = definitionNames.pickDiscriminator(idNode, List.of(identityName));
            final String name = identityName + discriminator;
            definitionObject.setName(name);
            defObj.getRoot().addData(name, definitionObject);
            return name;
        } else {
            return identityName + definitionNames.getDiscriminator(idNode);
        }
    }

    private static DefinitionObject buildIdentityDefObject(final IdentitySchemaNode idNode,
            final EffectiveModelContext context, final DefinitionObject defObj) {
        DefinitionObject identityDefObj = new DefinitionObject(defObj);
        final String identityName = idNode.getQName().getLocalName();
        LOG.debug("Processing Identity: {}", identityName);
        identityDefObj.addData(TITLE_KEY, identityName);
        identityDefObj.addData(DESCRIPTION_KEY, idNode.getDescription().orElse(""));

        final Collection<? extends IdentitySchemaNode> derivedIds = context.getDerivedIdentities(idNode);

        ArrayList<String> enumPayloads = new ArrayList<>();
        enumPayloads.add(identityName);
        populateEnumWithDerived(derivedIds, enumPayloads, context);

        identityDefObj.addData(ENUM_KEY, enumPayloads.toArray(new String[0]));
        identityDefObj.addData(TYPE_KEY, STRING_TYPE);
        return identityDefObj;
    }

    private boolean isImported(final IdentityrefTypeDefinition leafTypeDef) {
        return !leafTypeDef.getQName().getModule().equals(topLevelModule.getQNameModule());
    }

    private static String processBitsType(final BitsTypeDefinition bitsType,
                                          final DefinitionObject defObj) {
        defObj.addData(MIN_ITEMS, 0);
        defObj.addData(MIN_ITEMS, true);
        final ArrayNode enumNames = new ArrayNode(JsonNodeFactory.instance);
        final Collection<? extends Bit> bits = bitsType.getBits();
        for (final Bit bit : bits) {
            enumNames.add(new TextNode(bit.getName()));
        }
        final String[] bitsArray = bits.stream()
                .map(Bit::getName)
                .toArray(String[]::new);
        defObj.addData(ENUM_KEY, bitsArray);
        defObj.addData(DEFAULT_KEY, enumNames.iterator().next() + " " + enumNames.get(enumNames.size() - 1));
        return STRING_TYPE;
    }

    private static String processStringType(final TypeDefinition<?> stringType,
                                            final String nodeName,final DefinitionObject defObj) {
        StringTypeDefinition type = (StringTypeDefinition) stringType;
        Optional<LengthConstraint> lengthConstraints = ((StringTypeDefinition) stringType).getLengthConstraint();
        while (lengthConstraints.isEmpty() && type.getBaseType() != null) {
            type = type.getBaseType();
            lengthConstraints = type.getLengthConstraint();
        }

        if (lengthConstraints.isPresent()) {
            final Range<Integer> range = lengthConstraints.get().getAllowedRanges().span();
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
            final String defaultValue = (String) optDefaultValue.get();
            return defaultValue.startsWith("0") || defaultValue.startsWith("-0");
        }
        return false;
    }

    private static String processInstanceIdentifierType(final DataSchemaNode node,
                                                        final EffectiveModelContext schemaContext,
                                                        final DefinitionObject defObj) {
        // create example instance-identifier to the first container of node's module if exists or leave it empty
        final var module = schemaContext.findModule(node.getQName().getModule());
        if (module.isPresent()) {
            final var container = module.get().getChildNodes().stream()
                    .filter(n -> n instanceof ContainerSchemaNode)
                    .findFirst();
            container.ifPresent(c -> setDefaultValue(defObj, String.format("/%s:%s", module.get().getPrefix(),
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

    private static DefinitionObject buildXmlParameter(final SchemaNode node, final DefinitionObject defObj,
            final String name) {
        DefinitionObject definitionObject = new DefinitionObject(defObj);
        definitionObject.setName(name);
        final ObjectNode xml = JsonNodeFactory.instance.objectNode();
        final QName qName = node.getQName();
        xml.put(NAME_KEY, qName.getLocalName());
        definitionObject.addData(NAME_KEY, qName.getLocalName());
        xml.put(NAMESPACE_KEY, qName.getNamespace().toString());
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
