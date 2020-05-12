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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
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
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
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
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
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
    private static final String ONE_OF_KEY  = "oneOf";
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


    public ObjectNode convertToJsonSchema(final Module module, final SchemaContext schemaContext,
                                          final ObjectNode definitions, final DefinitionNames definitionNames,
                                          final OAversion oaversion, final boolean isForSingleModule)
            throws IOException {
        topLevelModule = module;

        processIdentities(module, definitions, definitionNames, schemaContext);
        processContainersAndLists(module, definitions, definitionNames, schemaContext, oaversion);
        processRPCs(module, definitions, definitionNames, schemaContext, oaversion);

        if (isForSingleModule) {
            processModule(module, definitions, definitionNames, schemaContext, oaversion);
        }

        return definitions;
    }

    public ObjectNode convertToJsonSchema(final Module module, final SchemaContext schemaContext,
                                          final DefinitionNames definitionNames, final OAversion oaversion,
                                          final boolean isForSingleModule)
            throws IOException {
        final ObjectNode definitions = JsonNodeFactory.instance.objectNode();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(module.getName() + MODULE_NAME_SUFFIX);
        }
        return convertToJsonSchema(module, schemaContext, definitions, definitionNames, oaversion, isForSingleModule);
    }

    private void processModule(final Module module, final ObjectNode definitions, final DefinitionNames definitionNames,
                               final SchemaContext schemaContext, final OAversion oaversion) {
        final ObjectNode definition = JsonNodeFactory.instance.objectNode();
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        final String moduleName = module.getName();
        final String definitionName = moduleName + MODULE_NAME_SUFFIX;
        for (final DataSchemaNode node : module.getChildNodes()) {
            final String localName = node.getQName().getLocalName();
            if (node.isConfiguration()) {
                if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
                    for (final DataSchemaNode childNode : ((DataNodeContainer) node).getChildNodes()) {
                        final ObjectNode childNodeProperties = JsonNodeFactory.instance.objectNode();

                        final String ref = getAppropriateModelPrefix(oaversion)
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
                            Description can't be added, because nothing allowed alongside $ref.
                            allOf is not an option, because ServiceNow can't parse it.
                          */
                            childNodeProperties.put(REF_KEY, ref);
                        }
                        //add module name prefix to property name, when ServiceNow can process colons
                        properties.set(localName, childNodeProperties);
                    }
                } else {
                    if (node instanceof LeafSchemaNode) {
                        /*
                            Add module name prefix to property name, when ServiceNow can process colons(second parameter
                            of processLeafNode).
                         */
                        processLeafNode((LeafSchemaNode) node, localName, properties, required, schemaContext,
                                definitions, definitionNames, oaversion);
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
                                           final DefinitionNames definitionNames, final SchemaContext schemaContext,
                                           final OAversion oaversion) throws IOException {
        final String moduleName = module.getName();

        for (final DataSchemaNode childNode : module.getChildNodes()) {
            // For every container and list in the module
            if (childNode instanceof ContainerSchemaNode || childNode instanceof ListSchemaNode) {
                if (childNode.isConfiguration()) {
                    processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                            true, schemaContext, oaversion);
                }
                processDataNodeContainer((DataNodeContainer) childNode, moduleName, definitions, definitionNames,
                        false, schemaContext, oaversion);
                processActionNodeContainer(childNode, moduleName, definitions, definitionNames, schemaContext,
                        oaversion);
            }
        }
    }

    private void processActionNodeContainer(final DataSchemaNode childNode, final String moduleName,
                                            final ObjectNode definitions, final DefinitionNames definitionNames,
                                            final SchemaContext schemaContext, final OAversion oaversion)
            throws IOException {
        for (final ActionDefinition actionDef : ((ActionNodeContainer) childNode).getActions()) {
            processOperations(actionDef, moduleName, definitions, definitionNames, schemaContext, oaversion);
        }
    }

    private void processRPCs(final Module module, final ObjectNode definitions, final DefinitionNames definitionNames,
                             final SchemaContext schemaContext, final OAversion oaversion) throws IOException {
        final String moduleName = module.getName();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            processOperations(rpcDefinition, moduleName, definitions, definitionNames, schemaContext, oaversion);
        }
    }


    private void processOperations(final OperationDefinition operationDef, final String parentName,
                                   final ObjectNode definitions, final DefinitionNames definitionNames,
                                   final SchemaContext schemaContext, final OAversion oaversion) throws IOException {
        final String operationName = operationDef.getQName().getLocalName();
        processOperationInputOutput(operationDef.getInput(), operationName, parentName, true, definitions,
                definitionNames, schemaContext, oaversion);
        processOperationInputOutput(operationDef.getOutput(), operationName, parentName, false, definitions,
                definitionNames, schemaContext, oaversion);
    }

    private void processOperationInputOutput(final ContainerSchemaNode container, final String operationName,
                                             final String parentName, final boolean isInput,
                                             final ObjectNode definitions, final DefinitionNames definitionNames,
                                             final SchemaContext schemaContext, final OAversion oaversion)
            throws IOException {
        if (!container.getChildNodes().isEmpty()) {
            final String filename = parentName + "_" + operationName + (isInput ? INPUT_SUFFIX : OUTPUT_SUFFIX);
            final ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            processChildren(childSchema, container.getChildNodes(), parentName, definitions, definitionNames,
                    false, schemaContext, oaversion);

            childSchema.put(TYPE_KEY, OBJECT_TYPE);
            final ObjectNode xml = JsonNodeFactory.instance.objectNode();
            xml.put(NAME_KEY, isInput ? INPUT : OUTPUT);
            childSchema.set(XML_KEY, xml);
            childSchema.put(TITLE_KEY, filename);
            final String discriminator =
                    definitionNames.pickDiscriminator(container, List.of(filename, filename + TOP));
            definitions.set(filename + discriminator, childSchema);

            processTopData(filename, discriminator, definitions, container, oaversion);
        }
    }

    private ObjectNode processTopData(final String filename, final String discriminator, final ObjectNode definitions,
                                      final SchemaNode schemaNode, final OAversion oaversion) {
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
        /*
            Add module name prefix to property name, when needed, when ServiceNow can process colons,
            use RestDocGenUtil#resolveNodesName for creating property name
         */
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
     * @param module          The module from which the identity stmt will be processed
     * @param definitions     The ObjectNode in which the parsed identity will be put as a 'model' obj
     * @param definitionNames Store for definition names
     */
    private static void processIdentities(final Module module, final ObjectNode definitions,
                                          final DefinitionNames definitionNames, final SchemaContext context) {

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
                                                final ArrayNode enumPayload, final SchemaContext context) {
        for (final IdentitySchemaNode derivedId : derivedIds) {
            enumPayload.add(derivedId.getQName().getLocalName());
            populateEnumWithDerived(context.getDerivedIdentities(derivedId), enumPayload, context);
        }
    }

    private ObjectNode processDataNodeContainer(final DataNodeContainer dataNode, final String parentName,
                                                final ObjectNode definitions, final DefinitionNames definitionNames,
                                                final boolean isConfig, final SchemaContext schemaContext,
                                                final OAversion oaversion) throws IOException {
        if (dataNode instanceof ListSchemaNode || dataNode instanceof ContainerSchemaNode) {
            final Collection<? extends DataSchemaNode> containerChildren = dataNode.getChildNodes();
            final SchemaNode schemaNode = (SchemaNode) dataNode;
            final String localName = schemaNode.getQName().getLocalName();
            ObjectNode childSchema = JsonNodeFactory.instance.objectNode();
            final String nameAsParent = parentName + "_" + localName;
            final ObjectNode properties =
                    processChildren(childSchema, containerChildren, nameAsParent, definitions,
                            definitionNames, isConfig, schemaContext, oaversion);

            final String nodeName = parentName + (isConfig ? CONFIG : "") + "_" + localName;
            final String postNodeName = parentName + CONFIG + "_" + localName + POST_SUFFIX;
            final String postXmlNodeName = postNodeName + XML_SUFFIX;
            final String parentNameConfigLocalName = parentName + CONFIG + "_" + localName;

            final String description = schemaNode.getDescription().orElse("");
            final String discriminator;

            final List<String> names = new ArrayList<>();

            final List<List<String>> uniqueFields = new ArrayList<>();
            final List<OptionInfo> choicesCases = getChoicesCases(dataNode, uniqueFields);
            if (isOneOfContainer(oaversion, choicesCases)) {
                for (int i = 1; i <= choicesCases.size(); i++) {
                    names.addAll(List.of(parentNameConfigLocalName + "_" + i,
                            nameAsParent + "_" + i,
                            postNodeName + "_" + i,
                            postNodeName + XML_SUFFIX + "_" + i));
                }
            }

            names.addAll(List.of(parentNameConfigLocalName,
                    parentNameConfigLocalName + TOP,
                    nameAsParent,
                    nameAsParent + TOP,
                    postNodeName,
                    postXmlNodeName));

            if (!definitionNames.isListedNode(schemaNode)) {
                discriminator = definitionNames.pickDiscriminator(schemaNode, names);
            } else {
                discriminator = definitionNames.getDiscriminator(schemaNode);
            }

            if (isOneOfContainer(oaversion, choicesCases)) {
                childSchema = createOneOfChildSchema(properties, nodeName, localName, discriminator, choicesCases,
                        uniqueFields, definitions);
            }

            if (!isOneOfContainer(oaversion, choicesCases)) {
                childSchema.put(TYPE_KEY, OBJECT_TYPE);
                childSchema.set(PROPERTIES_KEY, properties);
                childSchema.put(TITLE_KEY, nodeName);
                childSchema.put(DESCRIPTION_KEY, description);
            }

            final String defName = nodeName + discriminator;
            childSchema.set(XML_KEY, buildXmlParameter(schemaNode));
            definitions.set(defName, childSchema);
            if (isConfig) {
                final ObjectNode postSchema = createPostJsonSchema(childSchema, dataNode, definitions, discriminator,
                        defName, isOneOfContainer(oaversion, choicesCases), oaversion);
                definitions.set(postNodeName + discriminator, postSchema);

                if (oaversion.equals(OAversion.V3_0)) {
                    final ObjectNode postChildXmlSchema = buildPostXmlSchema(properties, dataNode);
                    definitions.set(postXmlNodeName + discriminator, postChildXmlSchema);
                }
            }


            return processTopData(nodeName, discriminator, definitions, schemaNode, oaversion);
        }
        return null;
    }

    private ObjectNode createPostJsonSchema(final ObjectNode putSchema, final DataNodeContainer dataNode,
                                            final ObjectNode definitions, final String discriminator,
                                            final String putSchemaName, final boolean isOneOf,
                                            final OAversion oaversion) {
        ObjectNode postSchema;
        if (dataNode instanceof ListSchemaNode) {
            if (isOneOf) {
                ArrayNode options = JsonNodeFactory.instance.arrayNode();
                for (JsonNode jsonNode : putSchema.get(ONE_OF_KEY)) {
                    final String refValue = jsonNode.get(REF_KEY).asText();
                    final String defName = refValue.substring(getAppropriateModelPrefix(OAversion.V3_0).length());
                    final String cleanDefName = defName.substring(0, defName.length() - discriminator.length());
                    final ObjectNode postOptionSchema = excludeSchemaKeys((ObjectNode) definitions.get(defName),
                            (ListSchemaNode) dataNode);
                    final String postOptionSchemaName = cleanDefName + POST_SUFFIX + discriminator;
                    definitions.set(postOptionSchemaName, postOptionSchema);
                    final ObjectNode ref = JsonNodeFactory.instance.objectNode();
                    ref.put(REF_KEY, getAppropriateModelPrefix(OAversion.V3_0) + postOptionSchemaName);
                    options.add(ref);
                }
                postSchema = JsonNodeFactory.instance.objectNode();
                postSchema.set(ONE_OF_KEY, options);
            } else {
                postSchema = excludeSchemaKeys(putSchema, (ListSchemaNode) dataNode);
            }
        } else {
            postSchema = JsonNodeFactory.instance.objectNode();
            postSchema.put(REF_KEY, getAppropriateModelPrefix(oaversion) + putSchemaName);
        }
        return postSchema;
    }

    private boolean isOneOfContainer(final OAversion oaversion, final List<OptionInfo> choicesCases) {
        return oaversion.equals(OAversion.V3_0) && choicesCases.size() > 1;
    }

    private ObjectNode excludeSchemaKeys(final ObjectNode schema, final ListSchemaNode listNode) {
        final ObjectNode noKeysSchema = schema.deepCopy();
        noKeysSchema.set(PROPERTIES_KEY, excludePropertiesKeys((ObjectNode) schema.get(PROPERTIES_KEY), listNode));
        return noKeysSchema;
    }

    private ObjectNode excludePropertiesKeys(final ObjectNode properties, final ListSchemaNode listNode) {
        final ObjectNode noKeysProperties = JsonNodeFactory.instance.objectNode();
        final List<QName> keyDefinition = listNode.getKeyDefinition();
        final Set<String> keys = listNode.getChildNodes().stream()
                .filter(node -> keyDefinition.contains(node.getQName()))
                .map(node -> node.getQName().getLocalName())
                .collect(Collectors.toSet());

        final Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            final Map.Entry<String, JsonNode> property = it.next();
            if (!keys.contains(property.getKey())) {
                noKeysProperties.set(property.getKey(), property.getValue());
            }
        }
        return noKeysProperties;
    }

    private ObjectNode buildPostXmlSchema(final ObjectNode sourceProperties, final DataNodeContainer dataNode) {
        final ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.set(ONE_OF_KEY, createXmlOneOf(sourceProperties, dataNode));
        return schema;
    }

    private ArrayNode createXmlOneOf(final ObjectNode sourceProperties, final DataNodeContainer dataNode) {
        final ObjectNode noKeysProperties;
        if (dataNode instanceof ListSchemaNode) {
            noKeysProperties = excludePropertiesKeys(sourceProperties, (ListSchemaNode) dataNode);
        } else {
            noKeysProperties = sourceProperties.deepCopy();
        }
        final ArrayNode oneOf = JsonNodeFactory.instance.arrayNode();
        final Iterator<Map.Entry<String, JsonNode>> fields = noKeysProperties.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final ObjectNode option = JsonNodeFactory.instance.objectNode();
            final ObjectNode optionProperties = JsonNodeFactory.instance.objectNode();
            optionProperties.set(field.getKey(), field.getValue());
            option.set(PROPERTIES_KEY, optionProperties);
            option.put(TYPE_KEY, OBJECT_TYPE);
            option.put(TITLE_KEY, field.getKey());
            oneOf.add(option);
        }
        return oneOf;
    }

    private List<OptionInfo> getChoicesCases(final DataNodeContainer dataNode, final List<List<String>> uniqueFields) {
        final List<OptionInfo> choicesCases = new ArrayList<>();
        final OptionInfo constantInfo = new OptionInfo();
        final List<List<OptionInfo>> innerInfos = new ArrayList<>();
        for (final DataSchemaNode childNode : dataNode.getChildNodes()) {
            if (childNode instanceof ChoiceSchemaNode) {
                innerInfos.add(choiceToOptionInfo((ChoiceSchemaNode) childNode));
            } else {
                constantInfo.addNode(childNode);
            }
        }
        if (!innerInfos.isEmpty()) {
            int optionsAmount = 1;
            for (List<OptionInfo> innerInfo : innerInfos) {
                optionsAmount *= innerInfo.size();
            }
            for (int i = 0; i < optionsAmount; i++) {
                final OptionInfo info = chooseCases(innerInfos, i, optionsAmount);
                uniqueFields.add(info.getPropertiesNames());
                choicesCases.add(OptionInfo.merge(constantInfo, info));
            }
            return choicesCases;
        }
        return List.of(constantInfo);
    }

    private OptionInfo chooseCases(final List<List<OptionInfo>> choicesCases, final int id,
                                   final int optionsAmount) {
        int divider = optionsAmount;
        final OptionInfo optionInfo = new OptionInfo();
        for (final List<OptionInfo> choicesCase : choicesCases) {
            final int size = choicesCase.size();
            optionInfo.add(choicesCase.get(id % divider / (divider / size)));
            divider /= size;
        }
        return optionInfo;
    }


    private List<OptionInfo> choiceToOptionInfo(final ChoiceSchemaNode choiceNode) {
        final List<OptionInfo> optionInfos = new ArrayList<>();
        for (final CaseSchemaNode caseNode : choiceNode.getCases()) {
            optionInfos.addAll(getChoicesCases(caseNode, new ArrayList<>()));
        }
        return optionInfos;
    }

    private ObjectNode createOneOfChildSchema(final ObjectNode properties, final String nodeName,
                                              final String localName, final String discriminator,
                                              final List<OptionInfo> choicesCases,
                                              final List<List<String>> uniqueFields, final ObjectNode definitions) {
        final ObjectNode oneOfParent = JsonNodeFactory.instance.objectNode();
        final ArrayNode oneOf = JsonNodeFactory.instance.arrayNode();
        for (int id = 1; id <= choicesCases.size(); id++) {
            final OptionInfo currentOption = choicesCases.get(id - 1);
            final ObjectNode optionSchema = JsonNodeFactory.instance.objectNode();
            final ObjectNode optionProperties = createOptionProperties(properties, currentOption);
            optionSchema.set(PROPERTIES_KEY, optionProperties);
            optionSchema.put(TYPE_KEY, OBJECT_TYPE);
            optionSchema.put(TITLE_KEY, buildOptionTitle(localName, uniqueFields.get(id - 1)));
            addRequired(optionSchema, currentOption.getRequired());
            final String optionName = nodeName + "_" + id + discriminator;
            definitions.set(optionName, optionSchema);
            final ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put(REF_KEY, getAppropriateModelPrefix(OAversion.V3_0) + optionName);
            oneOf.add(schema);
        }
        oneOfParent.set(ONE_OF_KEY, oneOf);
        return oneOfParent;
    }

    private static void addRequired(final ObjectNode optionSchema, List<String> required) {
        if (!required.isEmpty()) {
            ArrayNode requiredNode = JsonNodeFactory.instance.arrayNode();
            for (String s : required) {
                requiredNode.add(s);
            }
            optionSchema.set(REQUIRED_KEY, requiredNode);
        }
    }

    private ObjectNode createOptionProperties(final ObjectNode properties, final OptionInfo chosenCases) {
        final ObjectNode optionProperties = JsonNodeFactory.instance.objectNode();
        for (final String name : chosenCases.getPropertiesNames()) {
            final JsonNode value = properties.get(name);
            if (value != null) {
                optionProperties.set(name, value);
            } else {
                chosenCases.getRequired().remove(name);
            }
        }
        return optionProperties;
    }

    private String buildOptionTitle(final String localName, final List<String> cases) {
        return localName + " " + String.join(", ", cases);
    }

    /**
     * Processes the nodes.
     */
    private ObjectNode processChildren(
            final ObjectNode parentNode, final Collection<? extends DataSchemaNode> nodes, final String parentName,
            final ObjectNode definitions, final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaContext schemaContext, final OAversion oaversion) throws IOException {
        final ObjectNode properties = JsonNodeFactory.instance.objectNode();
        final ArrayNode required = JsonNodeFactory.instance.arrayNode();
        for (final DataSchemaNode node : nodes) {
            if (!isConfig || node.isConfiguration()) {
                /*
                    Add module name prefix to property name, when needed, when ServiceNow can process colons,
                    use RestDocGenUtil#resolveNodesName for creating property name
                 */
                final String propertyName = node.getQName().getLocalName();
                final ObjectNode property;
                if (node instanceof LeafSchemaNode) {
                    processLeafNode((LeafSchemaNode) node, propertyName, properties,
                            required, schemaContext, definitions, definitionNames, oaversion);
                } else if (node instanceof AnyxmlSchemaNode) {
                    processAnyXMLNode((AnyxmlSchemaNode) node, propertyName, properties,
                            required);
                } else if (node instanceof AnydataSchemaNode) {
                    processAnydataNode((AnydataSchemaNode) node, propertyName, properties,
                            required);
                } else {
                    if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                        property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions,
                                definitionNames, isConfig, schemaContext, oaversion);
                        if (!isConfig) {
                            processActionNodeContainer(node, parentName, definitions, definitionNames, schemaContext,
                                    oaversion);
                        }
                    } else if (node instanceof LeafListSchemaNode) {
                        property = processLeafListNode((LeafListSchemaNode) node, schemaContext, definitions,
                                definitionNames, oaversion);

                    } else if (node instanceof ChoiceSchemaNode) {
                        for (final CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases()) {
                            processChoiceNode(variant.getChildNodes(), parentName, definitions, definitionNames,
                                    isConfig, schemaContext, properties, oaversion);
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

    private ObjectNode processLeafListNode(final LeafListSchemaNode listNode, final SchemaContext schemaContext,
                                           final ObjectNode definitions, final DefinitionNames definitionNames,
                                           final OAversion oaversion) {
        final ObjectNode props = JsonNodeFactory.instance.objectNode();
        props.put(TYPE_KEY, ARRAY_TYPE);

        final ObjectNode itemsVal = JsonNodeFactory.instance.objectNode();
        final Optional<ElementCountConstraint> optConstraint = listNode.getElementCountConstraint();
        processElementCount(optConstraint, props);

        processTypeDef(listNode.getType(), listNode, itemsVal, schemaContext, definitions, definitionNames, oaversion);
        props.set(ITEMS_KEY, itemsVal);

        props.put(DESCRIPTION_KEY, listNode.getDescription().orElse(""));

        return props;
    }

    private void processChoiceNode(
            final Iterable<? extends DataSchemaNode> nodes, final String parentName, final ObjectNode definitions,
            final DefinitionNames definitionNames, final boolean isConfig,
            final SchemaContext schemaContext, final ObjectNode properties, final OAversion oaversion)
            throws IOException {
        for (final DataSchemaNode node : nodes) {
            /*
                Add module name prefix to property name, when needed, when ServiceNow can process colons,
                use RestDocGenUtil#resolveNodesName for creating property name
             */
            final String name = node.getQName().getLocalName();
            final ObjectNode property;

            /*
                Ignore mandatoriness(passing unreferenced arrayNode to process...Node), because choice produces multiple
                properties
             */
            if (node instanceof LeafSchemaNode) {
                processLeafNode((LeafSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode(), schemaContext, definitions, definitionNames, oaversion);
            } else if (node instanceof AnyxmlSchemaNode) {
                processAnyXMLNode((AnyxmlSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode());
            } else if (node instanceof AnydataSchemaNode) {
                processAnydataNode((AnydataSchemaNode) node, name, properties,
                        JsonNodeFactory.instance.arrayNode());
            } else {
                if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                    property = processDataNodeContainer((DataNodeContainer) node, parentName, definitions,
                            definitionNames, isConfig, schemaContext, oaversion);
                    if (!isConfig) {
                        processActionNodeContainer(node, parentName, definitions, definitionNames, schemaContext,
                                oaversion);
                    }
                } else if (node instanceof LeafListSchemaNode) {
                    property = processLeafListNode((LeafListSchemaNode) node, schemaContext, definitions,
                            definitionNames, oaversion);

                } else if (node instanceof ChoiceSchemaNode) {
                    for (final CaseSchemaNode variant : ((ChoiceSchemaNode) node).getCases()) {
                        processChoiceNode(variant.getChildNodes(), parentName, definitions, definitionNames, isConfig,
                                schemaContext, properties, oaversion);
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

    private static void processMandatory(final MandatoryAware node, final String nodeName, final ArrayNode required) {
        if (node.isMandatory()) {
            required.add(nodeName);
        }
    }

    private ObjectNode processLeafNode(final LeafSchemaNode leafNode, final String jsonLeafName,
                                       final ObjectNode properties, final ArrayNode required,
                                       final SchemaContext schemaContext, final ObjectNode definitions,
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

        processTypeDef(leafNode.getType(), leafNode, property, schemaContext, definitions, definitionNames, oaversion);
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
                                  final ObjectNode property, final SchemaContext schemaContext,
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
                    definitionNames, oaversion, schemaContext);

        } else if (leafTypeDef instanceof StringTypeDefinition) {
            jsonType = processStringType(leafTypeDef, property, node.getQName().getLocalName());

        } else if (leafTypeDef instanceof UnionTypeDefinition) {
            jsonType = processUnionType((UnionTypeDefinition) leafTypeDef);

        } else if (leafTypeDef instanceof EmptyTypeDefinition) {
            jsonType = OBJECT_TYPE;
        } else if (leafTypeDef instanceof LeafrefTypeDefinition) {
            return processTypeDef(SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) leafTypeDef,
                    schemaContext, node), node, property, schemaContext, definitions, definitionNames, oaversion);
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
                                          final OAversion oaversion, final SchemaContext schemaContext) {
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
                                              final SchemaContext context) {
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

    private static ObjectNode buildIdentityObject(final IdentitySchemaNode idNode, final SchemaContext context) {
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
            final Generex generex = new Generex(regex);
            setDefaultValue(property, generex.random());
        } else {
            setDefaultValue(property, "Some " + nodeName);
        }
        return STRING_TYPE;
    }

    private String processNumberType(final RangeRestrictedTypeDefinition leafTypeDef, final ObjectNode property) {
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

        final QName rootContainer = path.getLastComponent();
        final String rootContainerName = rootContainer.getLocalName();
        final String prefix = schemaContext.findModule(rootContainer.getModule()).get().getPrefix();
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

    private static ObjectNode buildXmlParameter(SchemaNode node) {
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
