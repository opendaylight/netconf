/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TOP;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildDelete;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildGet;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPost;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPostOperation;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPut;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.getTypeParentNode;
import static org.opendaylight.netconf.sal.rest.doc.util.JsonUtil.addFields;
import static org.opendaylight.netconf.sal.rest.doc.util.RestDocgenUtil.resolvePathArgumentsName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.openapi.Components;
import org.opendaylight.netconf.sal.rest.doc.openapi.Info;
import org.opendaylight.netconf.sal.rest.doc.openapi.OpenApiObject;
import org.opendaylight.netconf.sal.rest.doc.openapi.Server;
import org.opendaylight.netconf.sal.rest.doc.util.JsonUtil;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseYangOpenApiGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BaseYangOpenApiGenerator.class);

    private static final String API_VERSION = "1.0.0";
    private static final String OPEN_API_VERSION = "3.0.3";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DefinitionGenerator jsonConverter = new DefinitionGenerator();
    private final DOMSchemaService schemaService;

    public static final String BASE_PATH = "/";
    public static final String MODULE_NAME_SUFFIX = "_module";

    static {
        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    protected BaseYangOpenApiGenerator(final Optional<DOMSchemaService> schemaService) {
        this.schemaService = schemaService.orElse(null);
    }

    public DOMSchemaService getSchemaService() {
        return schemaService;
    }

    public OpenApiObject getAllModulesDoc(final UriInfo uriInfo, final DefinitionNames definitionNames) {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getAllModulesDoc(uriInfo, Optional.empty(), schemaContext, Optional.empty(), "", definitionNames);
    }

    public OpenApiObject getAllModulesDoc(final UriInfo uriInfo, final Optional<Range<Integer>> range,
            final EffectiveModelContext schemaContext, final Optional<String> deviceName, final String context,
            final DefinitionNames definitionNames) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);
        String name = "Controller";
        if (deviceName.isPresent()) {
            name = deviceName.get();
        }

        final String title = name + " modules of RESTCONF";
        final OpenApiObject doc = createOpenApiObject(schema, host, BASE_PATH, title);
        doc.setDefinitions(JsonNodeFactory.instance.objectNode());
        doc.setComponents(new Components(doc.getDefinitions()));
        doc.setPaths(JsonNodeFactory.instance.objectNode());

        fillDoc(doc, range, schemaContext, context, deviceName, definitionNames);

        return doc;
    }

    public void fillDoc(final OpenApiObject doc, final Optional<Range<Integer>> range,
            final EffectiveModelContext schemaContext, final String context, final Optional<String> deviceName,
            final DefinitionNames definitionNames) {
        final SortedSet<Module> modules = getSortedModules(schemaContext);
        final Set<Module> filteredModules;
        if (range.isPresent()) {
            filteredModules = filterByRange(modules, range.get());
        } else {
            filteredModules = modules;
        }

        for (final Module module : filteredModules) {
            final String revisionString = module.getQNameModule().getRevision().map(Revision::toString).orElse(null);

            LOG.debug("Working on [{},{}]...", module.getName(), revisionString);

            getOpenApiDocSpec(module, context, deviceName, schemaContext, definitionNames, doc, false);
        }
    }

    private static Set<Module> filterByRange(final SortedSet<Module> modules, final Range<Integer> range) {
        final int begin = range.lowerEndpoint();
        final int end = range.upperEndpoint();

        Module firstModule = null;

        final Iterator<Module> iterator = modules.iterator();
        int counter = 0;
        while (iterator.hasNext() && counter < end) {
            final Module module = iterator.next();
            if (containsListOrContainer(module.getChildNodes()) || !module.getRpcs().isEmpty()) {
                if (counter == begin) {
                    firstModule = module;
                }
                counter++;
            }
        }

        if (iterator.hasNext()) {
            return modules.subSet(firstModule, iterator.next());
        } else {
            return modules.tailSet(firstModule);
        }
    }

    public OpenApiObject getApiDeclaration(final String module, final String revision, final UriInfo uriInfo) {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        final OpenApiObject doc = getApiDeclaration(module, revision, uriInfo, schemaContext, "");
        return doc;
    }

    public OpenApiObject getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext schemaContext, final String context) {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final Module module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        return getApiDeclaration(module, uriInfo, context, schemaContext);
    }

    public OpenApiObject getApiDeclaration(final Module module, final UriInfo uriInfo, final String context,
            final EffectiveModelContext schemaContext) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);

        return getOpenApiDocSpec(module, schema, host, BASE_PATH, context, schemaContext);
    }

    public String createHostFromUriInfo(final UriInfo uriInfo) {
        String portPart = "";
        final int port = uriInfo.getBaseUri().getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uriInfo.getBaseUri().getHost() + portPart;
    }

    public String createSchemaFromUriInfo(final UriInfo uriInfo) {
        return uriInfo.getBaseUri().getScheme();
    }

    public OpenApiObject getOpenApiDocSpec(final Module module, final String schema, final String host,
            final String basePath, final String context, final EffectiveModelContext schemaContext) {
        final OpenApiObject doc = createOpenApiObject(schema, host, basePath, module.getName());
        final DefinitionNames definitionNames = new DefinitionNames();
        return getOpenApiDocSpec(module, context, Optional.empty(), schemaContext, definitionNames, doc, true);
    }

    public OpenApiObject getOpenApiDocSpec(final Module module, final String context, final Optional<String> deviceName,
            final EffectiveModelContext schemaContext, final DefinitionNames definitionNames, final OpenApiObject doc,
            final boolean isForSingleModule) {
        final ObjectNode definitions;

        try {
            if (isForSingleModule) {
                definitions = jsonConverter.convertToJsonSchema(module, schemaContext, definitionNames, true);
                doc.setDefinitions(definitions);
            } else {
                definitions = jsonConverter.convertToJsonSchema(module, schemaContext, definitionNames, false);
                addFields(doc.getDefinitions(), definitions.fields());
            }
            doc.setComponents(new Components(doc.getDefinitions()));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Document: {}", MAPPER.writeValueAsString(doc));
            }
        } catch (final IOException e) {
            LOG.error("Exception occured in DefinitionGenerator", e);
        }

        final ObjectNode paths = JsonNodeFactory.instance.objectNode();
        final String moduleName = module.getName();

        boolean hasAddRootPostLink = false;

        final Collection<? extends DataSchemaNode> dataSchemaNodes = module.getChildNodes();
        LOG.debug("child nodes size [{}]", dataSchemaNodes.size());
        for (final DataSchemaNode node : dataSchemaNodes) {
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                LOG.debug("Is Configuration node [{}] [{}]", node.isConfiguration(), node.getQName().getLocalName());

                final String localName = module.getName() + ":" + node.getQName().getLocalName();
                ArrayNode pathParams = JsonNodeFactory.instance.arrayNode();
                String resourcePath;

                if (node.isConfiguration()) { // This node's config statement is
                    // true.
                    resourcePath = getResourcePath("config", context);

                    /*
                     * When there are two or more top container or list nodes
                     * whose config statement is true in module, make sure that
                     * only one root post link is added for this module.
                     */
                    if (isForSingleModule && !hasAddRootPostLink) {
                        LOG.debug("Has added root post link for module {}", module.getName());
                        addRootPostLink(module, deviceName, pathParams, resourcePath, paths);

                        hasAddRootPostLink = true;
                    }

                    final String resolvedPath = resourcePath + "/" + createPath(node, pathParams, localName);
                    addPaths(node, deviceName, moduleName, paths, pathParams, schemaContext, true, module.getName(),
                        definitionNames, resolvedPath);
                }
                pathParams = JsonNodeFactory.instance.arrayNode();
                resourcePath = getResourcePath("operational", context);

                if (!node.isConfiguration()) {
                    final String resolvedPath = resourcePath + "/" + createPath(node, pathParams, localName);
                    addPaths(node, deviceName, moduleName, paths, pathParams, schemaContext, false, moduleName,
                        definitionNames, resolvedPath);
                }
            }
        }

        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            final String resolvedPath = getResourcePath("operations", context) + "/" + moduleName + ":"
                    + rpcDefinition.getQName().getLocalName();
            addOperations(rpcDefinition, moduleName, deviceName, paths, module.getName(), definitionNames,
                resolvedPath);
        }

        LOG.debug("Number of Paths found [{}]", paths.size());

        if (isForSingleModule) {
            doc.setPaths(paths);
        } else {
            addFields(doc.getPaths(), paths.fields());
        }

        return doc;
    }

    private static void addRootPostLink(final Module module, final Optional<String> deviceName,
            final ArrayNode pathParams, final String resourcePath, final ObjectNode paths) {
        if (containsListOrContainer(module.getChildNodes())) {
            final ObjectNode post = JsonNodeFactory.instance.objectNode();
            final String moduleName = module.getName();
            final String name = moduleName + MODULE_NAME_SUFFIX;
            post.set("post", buildPost("", name, "", moduleName, deviceName,
                    module.getDescription().orElse(""), pathParams));
            paths.set(resourcePath, post);
        }
    }



    public OpenApiObject createOpenApiObject(final String schema, final String host, final String basePath,
                                             final String title) {
        final OpenApiObject doc = new OpenApiObject();
        doc.setOpenapi(OPEN_API_VERSION);
        final Info info = new Info();
        info.setTitle(title);
        info.setVersion(API_VERSION);
        doc.setInfo(info);
        doc.setServers(convertToServers(ImmutableList.of(schema), host, basePath));
        return doc;
    }

    private static List<Server> convertToServers(final List<String> schemes, final String host, final String basePath) {
        return ImmutableList.of(new Server(schemes.get(0) + "://" + host + basePath));
    }

    protected abstract String getPathVersion();

    public abstract String getResourcePath(String resourceType, String context);

    public abstract String getResourcePathPart(String resourceType);

    private void addPaths(final DataSchemaNode node, final Optional<String> deviceName, final String moduleName,
            final ObjectNode paths, final ArrayNode parentPathParams, final EffectiveModelContext schemaContext,
            final boolean isConfig, final String parentName, final DefinitionNames definitionNames,
            final String resourcePath) {
        LOG.debug("Adding path: [{}]", resourcePath);

        final ArrayNode pathParams = JsonUtil.copy(parentPathParams);
        Iterable<? extends DataSchemaNode> childSchemaNodes = Collections.emptySet();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            final DataNodeContainer dataNodeContainer = (DataNodeContainer) node;
            childSchemaNodes = dataNodeContainer.getChildNodes();
        }

        final ObjectNode path = JsonNodeFactory.instance.objectNode();
        path.setAll(operations(node, moduleName, deviceName, pathParams, isConfig, parentName, definitionNames));
        paths.set(resourcePath, path);

        if (node instanceof ActionNodeContainer) {
            ((ActionNodeContainer) node).getActions().forEach(actionDef -> {
                final String resolvedPath = "rests/operations" + resourcePath.substring(11)
                        + "/" + resolvePathArgumentsName(actionDef.getQName(), node.getQName(), schemaContext);
                addOperations(actionDef, moduleName, deviceName, paths, parentName, definitionNames, resolvedPath);
            });
        }

        for (final DataSchemaNode childNode : childSchemaNodes) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final String newParent = parentName + "_" + node.getQName().getLocalName();
                final String localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final String newResourcePath = resourcePath + "/" + createPath(childNode, pathParams, localName);
                final boolean newIsConfig = isConfig && childNode.isConfiguration();
                addPaths(childNode, deviceName, moduleName, paths, pathParams, schemaContext,
                    newIsConfig, newParent, definitionNames, newResourcePath);
            }
        }
    }

    private static boolean containsListOrContainer(final Iterable<? extends DataSchemaNode> nodes) {
        for (final DataSchemaNode child : nodes) {
            if (child instanceof ListSchemaNode || child instanceof ContainerSchemaNode) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, ObjectNode> operations(final DataSchemaNode node, final String moduleName,
            final Optional<String> deviceName, final ArrayNode pathParams, final boolean isConfig,
            final String parentName, final DefinitionNames definitionNames) {
        final Map<String, ObjectNode> operations = new HashMap<>();
        final String discriminator = definitionNames.getDiscriminator(node);

        final String nodeName = node.getQName().getLocalName();

        final String defName = parentName + "_" + nodeName + TOP + discriminator;
        final ObjectNode get = buildGet(node, moduleName, deviceName, pathParams, defName, isConfig);
        operations.put("get", get);


        if (isConfig) {
            final ObjectNode put = buildPut(parentName, nodeName, discriminator, moduleName, deviceName,
                    node.getDescription().orElse(""), pathParams);
            operations.put("put", put);

            final ObjectNode delete = buildDelete(node, moduleName, deviceName, pathParams);
            operations.put("delete", delete);

            operations.put("post", buildPost(parentName, nodeName, discriminator, moduleName, deviceName,
                    node.getDescription().orElse(""), pathParams));
        }
        return operations;
    }

    protected abstract ListPathBuilder newListPathBuilder();

    private String createPath(final DataSchemaNode schemaNode, final ArrayNode pathParams, final String localName) {
        final StringBuilder path = new StringBuilder();
        path.append(localName);

        if (schemaNode instanceof ListSchemaNode) {
            final ListPathBuilder keyBuilder = newListPathBuilder();
            for (final QName listKey : ((ListSchemaNode) schemaNode).getKeyDefinition()) {
                final String paramName = createUniquePathParamName(listKey.getLocalName(), pathParams);
                final String pathParamIdentifier = keyBuilder.nextParamIdentifier(paramName);

                path.append(pathParamIdentifier);

                final ObjectNode pathParam = JsonNodeFactory.instance.objectNode();
                pathParam.put("name", paramName);

                ((DataNodeContainer) schemaNode).findDataChildByName(listKey).flatMap(DataSchemaNode::getDescription)
                        .ifPresent(desc -> pathParam.put("description", desc));

                final ObjectNode typeParent = getTypeParentNode(pathParam);

                typeParent.put("type", "string");
                pathParam.put("in", "path");
                pathParam.put("required", true);

                pathParams.add(pathParam);
            }
        }
        return path.toString();
    }

    private String createUniquePathParamName(final String clearName, final ArrayNode pathParams) {
        for (final JsonNode pathParam : pathParams) {
            if (isNamePicked(clearName, pathParam)) {
                return createUniquePathParamName(clearName, pathParams, 1);
            }
        }
        return clearName;
    }

    private String createUniquePathParamName(final String clearName, final ArrayNode pathParams,
            final int discriminator) {
        final String newName = clearName + discriminator;
        for (final JsonNode pathParam : pathParams) {
            if (isNamePicked(newName, pathParam)) {
                return createUniquePathParamName(clearName, pathParams, discriminator + 1);
            }
        }
        return newName;
    }

    private static boolean isNamePicked(final String name, final JsonNode pathParam) {
        return name.equals(pathParam.get("name").asText());
    }

    public SortedSet<Module> getSortedModules(final EffectiveModelContext schemaContext) {
        if (schemaContext == null) {
            return Collections.emptySortedSet();
        }

        final SortedSet<Module> sortedModules = new TreeSet<>((module1, module2) -> {
            int result = module1.getName().compareTo(module2.getName());
            if (result == 0) {
                result = Revision.compare(module1.getRevision(), module2.getRevision());
            }
            if (result == 0) {
                result = module1.getNamespace().compareTo(module2.getNamespace());
            }
            return result;
        });
        for (final Module m : schemaContext.getModules()) {
            if (m != null) {
                sortedModules.add(m);
            }
        }
        return sortedModules;
    }

    private static void addOperations(final OperationDefinition operDef, final String moduleName,
            final Optional<String> deviceName, final ObjectNode paths, final String parentName,
            final DefinitionNames definitionNames, final String resourcePath) {
        final ObjectNode operations = JsonNodeFactory.instance.objectNode();
        operations.set("post", buildPostOperation(operDef, moduleName, deviceName, parentName, definitionNames));
        paths.set(resourcePath, operations);
    }

    protected abstract void appendPathKeyValue(StringBuilder builder, Object value);

    public String generateUrlPrefixFromInstanceID(final YangInstanceIdentifier key, final String moduleName) {
        final StringBuilder builder = new StringBuilder();
        builder.append("/");
        if (moduleName != null) {
            builder.append(moduleName).append(':');
        }
        for (final PathArgument arg : key.getPathArguments()) {
            final String name = arg.getNodeType().getLocalName();
            if (arg instanceof NodeIdentifierWithPredicates) {
                final NodeIdentifierWithPredicates nodeId = (NodeIdentifierWithPredicates) arg;
                for (final Entry<QName, Object> entry : nodeId.entrySet()) {
                    appendPathKeyValue(builder, entry.getValue());
                }
            } else {
                builder.append(name).append('/');
            }
        }
        return builder.toString();
    }

    protected interface ListPathBuilder {
        String nextParamIdentifier(String key);
    }
}
