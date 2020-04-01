/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.DEFAULT_PAGESIZE;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.CONFIG;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TOP;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildDelete;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildGet;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPost;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPostOperation;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildPut;
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
import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.swagger.Info;
import org.opendaylight.netconf.sal.rest.doc.swagger.Resource;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
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
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseYangSwaggerGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BaseYangSwaggerGenerator.class);

    private static final String API_VERSION = "1.0.0";
    private static final String SWAGGER_VERSION = "2.0";

    private final DefinitionGenerator jsonConverter = new DefinitionGenerator();

    private final ObjectMapper mapper = new ObjectMapper();
    private final DOMSchemaService schemaService;

    public static final String BASE_PATH = "/";
    public static final String MODULE_NAME_SUFFIX = "_module";

    protected BaseYangSwaggerGenerator(final Optional<DOMSchemaService> schemaService) {
        this.schemaService = schemaService.orElse(null);
        this.mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public DOMSchemaService getSchemaService() {
        return schemaService;
    }

    public ResourceList getResourceListing(final UriInfo uriInfo, final URIType uriType) {
        final SchemaContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getResourceListing(uriInfo, schemaContext, "", 0, true, uriType);
    }

    public ResourceList getResourceListing(final UriInfo uriInfo, final SchemaContext schemaContext,
                                           final String context, final URIType uriType) {
        return getResourceListing(uriInfo, schemaContext, context, 0, true, uriType);
    }

    /**
     * Return list of modules converted to swagger compliant resource list.
     */
    public ResourceList getResourceListing(final UriInfo uriInfo, final SchemaContext schemaContext,
                                           final String context, final int pageNum, final boolean all,
                                           final URIType uriType) {
        final ResourceList resourceList = createResourceList();

        final Set<Module> modules = getSortedModules(schemaContext);

        final List<Resource> resources = new ArrayList<>(DEFAULT_PAGESIZE);

        LOG.info("Modules found [{}]", modules.size());
        final int start = DEFAULT_PAGESIZE * pageNum;
        final int end = start + DEFAULT_PAGESIZE;
        int count = 0;
        for (final Module module : modules) {
            final String revisionString = module.getQNameModule().getRevision().map(Revision::toString).orElse(null);

            LOG.debug("Working on [{},{}]...", module.getName(), revisionString);
            final SwaggerObject doc =
                    getApiDeclaration(module.getName(), revisionString, uriInfo, schemaContext, context, uriType);
            if (doc != null) {
                count++;
                if (count >= start && count < end || all) {
                    final Resource resource = new Resource();
                    resource.setPath(generatePath(uriInfo, module.getName(), revisionString));
                    resources.add(resource);
                }

                if (count >= end && !all) {
                    break;
                }
            } else {
                LOG.warn("Could not generate doc for {},{}", module.getName(), revisionString);
            }
        }

        resourceList.setApis(resources);

        return resourceList;
    }

    public SwaggerObject getAllModulesDoc(final UriInfo uriInfo, final DefinitionNames definitionNames,
                                          final URIType uriType) {
        final SchemaContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getAllModulesDoc(uriInfo, schemaContext, "",definitionNames,  uriType);
    }

    public SwaggerObject getAllModulesDoc(final UriInfo uriInfo, final SchemaContext schemaContext,
                                          final String context, final DefinitionNames definitionNames,
                                          final URIType uriType) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);
        final SwaggerObject doc = createSwaggerObject(schema, host, BASE_PATH, "All modules API");
        doc.setDefinitions(JsonNodeFactory.instance.objectNode());
        doc.setPaths(JsonNodeFactory.instance.objectNode());

        fillDoc(doc, schemaContext, context, Optional.empty(), uriType, definitionNames);

        return doc;
    }

    public void fillDoc(final SwaggerObject doc, final SchemaContext schemaContext, final String context,
                        final Optional<String> deviceName, final URIType uriType,
                        final DefinitionNames definitionNames) {
        final Set<Module> modules = getSortedModules(schemaContext);

        for (final Module module : modules) {
            final String revisionString = module.getQNameModule().getRevision().map(Revision::toString).orElse(null);

            LOG.debug("Working on [{},{}]...", module.getName(), revisionString);

            getSwaggerDocSpec(module, context, deviceName, schemaContext, uriType,
                    definitionNames, doc, false);
        }
    }

    public ResourceList createResourceList() {
        final ResourceList resourceList = new ResourceList();
        resourceList.setApiVersion(API_VERSION);
        resourceList.setSwaggerVersion(SWAGGER_VERSION);
        return resourceList;
    }

    public String generatePath(final UriInfo uriInfo, final String name, final String revision) {
        final URI uri = uriInfo.getRequestUriBuilder().replaceQuery("").path(generateCacheKey(name, revision)).build();
        return uri.toASCIIString();
    }

    public SwaggerObject getApiDeclaration(final String module, final String revision, final UriInfo uriInfo,
                                           final URIType uriType) {
        final SchemaContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getApiDeclaration(module, revision, uriInfo, schemaContext, "", uriType);
    }

    public SwaggerObject getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
                                           final SchemaContext schemaContext, final String context,
                                           final URIType uriType) {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final Module module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        return getApiDeclaration(module, uriInfo, context, schemaContext, uriType);
    }

    public SwaggerObject getApiDeclaration(final Module module, final UriInfo uriInfo,
                                           final String context, final SchemaContext schemaContext,
                                           final URIType uriType) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);

        return getSwaggerDocSpec(module, schema, host, BASE_PATH, context, schemaContext, uriType);
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

    public SwaggerObject getSwaggerDocSpec(final Module module, final String schema, final String host,
                                           final String basePath, final String context,
                                           final SchemaContext schemaContext, final URIType uriType) {
        final SwaggerObject doc = createSwaggerObject(schema, host, basePath, module.getName());
        final DefinitionNames definitionNames = new DefinitionNames();
        return getSwaggerDocSpec(module, context, Optional.empty(), schemaContext, uriType, definitionNames, doc, true);
    }



    public SwaggerObject getSwaggerDocSpec(final Module module, final String context, final Optional<String> deviceName,
                                           final SchemaContext schemaContext, final URIType uriType,
                                           final DefinitionNames definitionNames, final SwaggerObject doc,
                                           final boolean isForSingleModule) {
        final ObjectNode definitions;

        try {
            if (isForSingleModule) {
                definitions = this.jsonConverter.convertToJsonSchema(module, schemaContext, definitionNames, true);
                doc.setDefinitions(definitions);
            } else {
                definitions = this.jsonConverter.convertToJsonSchema(module, schemaContext, definitionNames, false);
                addFields(doc.getDefinitions(), definitions.fields());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Document: {}", this.mapper.writeValueAsString(doc));
            }
        } catch (final IOException e) {
            LOG.error("Exception occured in DefinitionGenerator", e);
        }

        final ObjectNode paths = JsonNodeFactory.instance.objectNode();
        final String moduleName = module.getName();

        boolean hasAddRootPostLink = false;

        final Collection<DataSchemaNode> dataSchemaNodes = module.getChildNodes();
        LOG.debug("child nodes size [{}]", dataSchemaNodes.size());
        for (final DataSchemaNode node : dataSchemaNodes) {
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                LOG.debug("Is Configuration node [{}] [{}]", node.isConfiguration(), node.getQName().getLocalName());

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

                    addPaths(node, deviceName, moduleName, paths, resourcePath, pathParams, schemaContext, true,
                            module.getName(), definitionNames, uriType);
                }
                pathParams = JsonNodeFactory.instance.arrayNode();
                resourcePath = getResourcePath("operational", context);

                if (uriType.equals(URIType.DRAFT02)
                        || uriType.equals(URIType.RFC8040) && !node.isConfiguration()) {
                    addPaths(node, deviceName, moduleName, paths, resourcePath, pathParams, schemaContext, false,
                            moduleName, definitionNames, uriType);
                }
            }
        }

        final Set<RpcDefinition> rpcs = module.getRpcs();
        for (final RpcDefinition rpcDefinition : rpcs) {
            final String resourcePath;
            resourcePath = getResourcePath("operations", context);
            addOperations(rpcDefinition, moduleName, deviceName, paths, resourcePath, module.getName(), definitionNames,
                    schemaContext);
        }

        LOG.debug("Number of Paths found [{}]", paths.size());

        if (isForSingleModule) {
            doc.setPaths(paths);
        } else {
            addFields(doc.getPaths(), paths.fields());
        }

        return doc;
    }

    private void addRootPostLink(final Module module, final Optional<String> deviceName, final ArrayNode pathParams,
                                 final String resourcePath, final ObjectNode paths) {
        if (containsListOrContainer(module.getChildNodes())) {
            final ObjectNode post = JsonNodeFactory.instance.objectNode();
            final String moduleName = module.getName();
            final String name = moduleName + MODULE_NAME_SUFFIX;
            post.set("post", buildPost(name, moduleName, deviceName, module.getDescription().orElse(""), name,
                    pathParams));
            paths.set(resourcePath, post);
        }
    }

    public SwaggerObject createSwaggerObject(final String schema, final String host, final String basePath,
                                             final String title) {
        final SwaggerObject doc = new SwaggerObject();
        doc.setSwagger(SWAGGER_VERSION);
        final Info info = new Info();
        info.setTitle(title);
        info.setVersion(API_VERSION);
        doc.setInfo(info);
        doc.setSchemes(ImmutableList.of(schema));
        doc.setHost(host);
        doc.setBasePath(basePath);
        doc.setProduces(Arrays.asList("application/xml", "application/json"));
        return doc;
    }

    public abstract String getResourcePath(String resourceType, String context);

    public abstract String getResourcePathPart(String resourceType);

    private static String generateCacheKey(final String module, final String revision) {
        return module + "(" + revision + ")";
    }

    private void addPaths(final DataSchemaNode node, final Optional<String> deviceName, final String moduleName,
                          final ObjectNode paths, final String parentPath, final ArrayNode parentPathParams,
                          final SchemaContext schemaContext, final boolean isConfig, final String parentName,
                          final DefinitionNames definitionNames, final URIType uriType) {
        final ArrayNode pathParams = JsonUtil.copy(parentPathParams);
        final String resourcePath = parentPath + "/" + createPath(node, pathParams, schemaContext);
        LOG.debug("Adding path: [{}]", resourcePath);

        Iterable<DataSchemaNode> childSchemaNodes = Collections.emptySet();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            final DataNodeContainer dataNodeContainer = (DataNodeContainer) node;
            childSchemaNodes = dataNodeContainer.getChildNodes();
        }

        final ObjectNode path = JsonNodeFactory.instance.objectNode();
        path.setAll(operations(node, moduleName, deviceName, pathParams, isConfig, parentName, definitionNames,
                uriType));
        paths.set(resourcePath, path);


        if (uriType.equals(URIType.RFC8040)) {
            final String operationPath = "rests/operations" + resourcePath.substring(11);
            ((ActionNodeContainer) node).getActions().forEach((actionDef ->
                    addOperations(actionDef, moduleName, deviceName, paths, operationPath, parentName, definitionNames,
                            schemaContext)));
        }

        for (final DataSchemaNode childNode : childSchemaNodes) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final String newParent = parentName + "_" + node.getQName().getLocalName();
                if (uriType.equals(URIType.RFC8040)) {
                    final boolean newIsConfig = isConfig && childNode.isConfiguration();
                    addPaths(childNode, deviceName, moduleName, paths, resourcePath, pathParams, schemaContext,
                            newIsConfig, newParent, definitionNames, uriType);
                } else {
                    if (!isConfig || childNode.isConfiguration()) {
                        addPaths(childNode, deviceName, moduleName, paths, resourcePath, pathParams, schemaContext,
                                isConfig, newParent, definitionNames, uriType);
                    }
                }
            }
        }
    }

    private static boolean containsListOrContainer(final Iterable<DataSchemaNode> nodes) {
        for (final DataSchemaNode child : nodes) {
            if (child instanceof ListSchemaNode || child instanceof ContainerSchemaNode) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, ObjectNode> operations(final DataSchemaNode node, final String moduleName,
                                                      final Optional<String> deviceName, final ArrayNode pathParams,
                                                      final boolean isConfig, final String parentName,
                                                      final DefinitionNames definitionNames, final URIType uriType) {
        final Map<String, ObjectNode> operations = new HashMap<>();
        final String discriminator = definitionNames.getDiscriminator(node);

        final String nodeName = node.getQName().getLocalName();

        String prefix = "_";
        if (isConfig && uriType.equals(URIType.DRAFT02)) {
            prefix = CONFIG + "_";
        }

        String defName = parentName + prefix + nodeName + TOP + discriminator;
        final ObjectNode get = buildGet(node, moduleName, deviceName, pathParams, defName, isConfig, uriType);
        operations.put("get", get);


        if (isConfig) {
            defName = parentName + CONFIG + "_" + nodeName + TOP + discriminator;
            final ObjectNode put = buildPut(nodeName, moduleName, deviceName, node.getDescription().orElse(""),
                    defName, pathParams);
            operations.put("put", put);

            final ObjectNode delete = buildDelete(node, moduleName, deviceName, pathParams);
            operations.put("delete", delete);

            defName = parentName + CONFIG + "_" + nodeName + discriminator;
            operations.put("post", buildPost(nodeName, moduleName, deviceName, node.getDescription().orElse(""),
                    defName, pathParams));
        }
        return operations;
    }

    protected abstract ListPathBuilder newListPathBuilder();

    private String createPath(final DataSchemaNode schemaNode, final ArrayNode pathParams,
                              final SchemaContext schemaContext) {
        final StringBuilder path = new StringBuilder();
        final String localName = resolvePathArgumentsName(schemaNode, schemaContext);
        path.append(localName);

        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> listKeys = ((ListSchemaNode) schemaNode).getKeyDefinition();
            for (final QName listKey : listKeys) {
                final ListPathBuilder keyBuilder = newListPathBuilder();
                final String paramName = createUniquePathParamName(listKey.getLocalName(), pathParams);
                final String pathParamIdentifier = keyBuilder.nextParamIdentifier(paramName);

                path.append(pathParamIdentifier);

                final ObjectNode pathParam = JsonNodeFactory.instance.objectNode();
                pathParam.put("name", paramName);

                ((DataNodeContainer) schemaNode).findDataChildByName(listKey).flatMap(DataSchemaNode::getDescription)
                        .ifPresent(desc -> pathParam.put("description", desc));

                pathParam.put("type", "string");
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

    private boolean isNamePicked(final String name, final JsonNode pathParam) {
        return name.equals(pathParam.get("name").asText());
    }

    public SortedSet<Module> getSortedModules(final SchemaContext schemaContext) {
        if (schemaContext == null) {
            return Collections.emptySortedSet();
        }

        final Set<Module> modules = schemaContext.getModules();

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
        for (final Module m : modules) {
            if (m != null) {
                sortedModules.add(m);
            }
        }
        return sortedModules;
    }

    private void addOperations(final OperationDefinition operDef, final String moduleName,
                               final Optional<String> deviceName, final ObjectNode paths, final String parentPath,
                               final String parentName, final DefinitionNames definitionNames,
                               final SchemaContext schemaContext) {
        final ObjectNode operations = JsonNodeFactory.instance.objectNode();
        final String resourcePath = parentPath + "/" + resolvePathArgumentsName(operDef, schemaContext);
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
