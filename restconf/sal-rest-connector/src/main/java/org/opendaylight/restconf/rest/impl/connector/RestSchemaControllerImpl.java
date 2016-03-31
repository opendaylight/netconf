/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.connector;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.common.impl.util.compat.DataNormalizer;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.rest.impl.RestUtil;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.utils.RestSchemaControllerUtil;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class RestSchemaControllerImpl implements RestSchemaController {

    private static final Logger LOG = LoggerFactory.getLogger(RestSchemaControllerImpl.class);
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');
    private static final String URI_ENCODING_CHAR_SET = "ISO-8859-1";
    private static final String NULL_VALUE = "null";
    private static final String MOUNT_MODULE = "yang-ext";
    private static final String MOUNT_NODE = "mount";
    private static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.builder().build();
    private static final String MOUNT = "yang-ext:mount";
    private static final Predicate<GroupingDefinition> GROUPING_FILTER = new Predicate<GroupingDefinition>() {

        @Override
        public boolean apply(final GroupingDefinition input) {
            return Draft09.RestConfModule.RESTCONF_GROUPING_SCHEMA_NODE.equals(input.getQName().getLocalName());
        }
    };

    private final AtomicReference<Map<QName, RpcDefinition>> qnameToRpc = new AtomicReference<>(
            Collections.<QName, RpcDefinition> emptyMap());

    private volatile SchemaContext globalSchema;
    private volatile DOMMountPointService mountService;
    private DataNormalizer dataNormalizer;

    @Override
    public void onGlobalContextUpdated(final SchemaContext context) {
        if (context != null) {
            final Collection<RpcDefinition> defs = context.getOperations();
            final Map<QName, RpcDefinition> newMap = new HashMap<>(defs.size());

            for (final RpcDefinition operation : defs) {
                newMap.put(operation.getQName(), operation);
            }

            // FIXME: still not completely atomic
            this.qnameToRpc.set(ImmutableMap.copyOf(newMap));
            setGlobalSchema(context);
        }
    }

    @Override
    public void setGlobalSchema(final SchemaContext globalSchema) {
        this.globalSchema = globalSchema;
        this.dataNormalizer = new DataNormalizer(globalSchema);
    }

    @Override
    public SchemaContext getGlobalSchema() {
        return this.globalSchema;
    }

    @Override
    public Set<Module> getAllModules() {
        checkPreconditions();
        return this.globalSchema.getModules();
    }

    @Override
    public DataSchemaNode getRestconfModuleRestConfSchemaNode(final Module inRestconfModule,
            final String schemaNodeName) {
        Module restconfModule = inRestconfModule;
        if (restconfModule == null) {
            restconfModule = getRestconfModule();
        }

        if (restconfModule == null) {
            return null;
        }

        final Set<GroupingDefinition> groupings = restconfModule.getGroupings();
        final Iterable<GroupingDefinition> filteredGroups = Iterables.filter(groupings, GROUPING_FILTER);
        final GroupingDefinition restconfGrouping = Iterables.getFirst(filteredGroups, null);

        final List<DataSchemaNode> instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                restconfGrouping, Draft09.RestConfModule.RESTCONF_CONTAINER_SCHEMA_NODE);
        final DataSchemaNode restconfContainer = Iterables.getFirst(instanceDataChildrenByName, null);

        if (Objects.equal(schemaNodeName, Draft09.RestConfModule.OPERATIONS_CONTAINER_SCHEMA_NODE)) {
            final List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.OPERATIONS_CONTAINER_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        } else if (Objects.equal(schemaNodeName, Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE)) {
            final List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        } else if (Objects.equal(schemaNodeName, Draft09.RestConfModule.STREAM_LIST_SCHEMA_NODE)) {
            List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            final DataSchemaNode modules = Iterables.getFirst(instances, null);
            instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(((DataNodeContainer) modules),
                    Draft09.RestConfModule.STREAM_LIST_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        } else if (Objects.equal(schemaNodeName, Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE)) {
            final List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        } else if (Objects.equal(schemaNodeName, Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE)) {
            List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
            final DataSchemaNode modules = Iterables.getFirst(instances, null);
            instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(((DataNodeContainer) modules),
                    Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        } else if (Objects.equal(schemaNodeName, Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE)) {
            final List<DataSchemaNode> instances = RestSchemaControllerUtil.findInstanceDataChildrenByName(
                    ((DataNodeContainer) restconfContainer), Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            return Iterables.getFirst(instances, null);
        }
        return null;
    }

    @Override
    public Module getRestconfModule() {
        return findModuleByNameAndRevision(Draft09.RestConfModule.IETF_RESTCONF_QNAME);

    }

    @Override
    public InstanceIdentifierContext<?> toMountPointIdentifier(final String restconfInstance) {
        return toIdentifier(restconfInstance, true);

    }

    @Override
    public Module findModuleByNameAndRevision(final DOMMountPoint mountPoint, final QName module) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Module findModuleByNameAndRevision(final QName module) {
        checkPreconditions();
        Preconditions.checkArgument((module != null) && (module.getLocalName() != null) && (module.getRevision() != null));

        return this.globalSchema.findModuleByName(module.getLocalName(), module.getRevision());
    }

    @Override
    public Set<Module> getAllModules(final DOMMountPoint mountPoint) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub

    }

    private void checkPreconditions() {
        if (this.globalSchema == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
    }

    private InstanceIdentifierContext<?> toIdentifier(final String restconfInstance,
            final boolean toMountPointIdentifier) {
        checkPreconditions();

        if (restconfInstance == null) {
            return new InstanceIdentifierContext<>(ROOT, this.globalSchema, null, this.globalSchema);
        }

        final List<String> pathArgs = urlPathArgsDecode(SLASH_SPLITTER.split(restconfInstance));
        omitFirstAndLastEmptyString(pathArgs);
        if (pathArgs.isEmpty()) {
            return null;
        }

        final String first = pathArgs.iterator().next();
        final String startModule = toModuleName(first);
        if (startModule == null) {
            throw new RestconfDocumentedException("First node in URI has to be in format \"moduleName:nodeName\"",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        final Module latestModule = this.globalSchema.findModuleByName(startModule, null);

        if (latestModule == null) {
            throw new RestconfDocumentedException("The module named '" + startModule + "' does not exist.",
                    ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final InstanceIdentifierContext<?> iiWithSchemaNode = collectPathArguments(builder, pathArgs, latestModule,
                null, toMountPointIdentifier);

        if (iiWithSchemaNode == null) {
            throw new RestconfDocumentedException("URI has bad format", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        return iiWithSchemaNode;
    }

    private static List<String> omitFirstAndLastEmptyString(final List<String> list) {
        if (list.isEmpty()) {
            return list;
        }

        final String head = list.iterator().next();
        if (head.isEmpty()) {
            list.remove(0);
        }

        if (list.isEmpty()) {
            return list;
        }

        final String last = list.get(list.size() - 1);
        if (last.isEmpty()) {
            list.remove(list.size() - 1);
        }

        return list;
    }

    private static List<String> urlPathArgsDecode(final Iterable<String> strings) {
        try {
            final List<String> decodedPathArgs = new ArrayList<String>();
            for (final String pathArg : strings) {
                final String _decode = URLDecoder.decode(pathArg, URI_ENCODING_CHAR_SET);
                decodedPathArgs.add(_decode);
            }
            return decodedPathArgs;
        } catch (final UnsupportedEncodingException e) {
            throw new RestconfDocumentedException("Invalid URL path '" + strings + "': " + e.getMessage(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    private static String toModuleName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return null;
        }

        // Make sure there is only one occurrence
        if (str.indexOf(':', idx + 1) != -1) {
            return null;
        }

        return str.substring(0, idx);
    }

    private InstanceIdentifierContext<?> collectPathArguments(final InstanceIdentifierBuilder builder,
            final List<String> strings, final DataNodeContainer parentNode, final DOMMountPoint mountPoint,
            final boolean returnJustMountPoint) {
        Preconditions.<List<String>> checkNotNull(strings);

        if (parentNode == null) {
            return null;
        }

        if (strings.isEmpty()) {
            return createContext(builder.build(), ((DataSchemaNode) parentNode), mountPoint,
                    mountPoint != null ? mountPoint.getSchemaContext() : this.globalSchema);
        }

        final String head = strings.iterator().next();
        final String nodeName = toNodeName(head);
        final String moduleName = toModuleName(head);

        DataSchemaNode targetNode = null;
        if (!Strings.isNullOrEmpty(moduleName)) {
            if (Objects.equal(moduleName, MOUNT_MODULE) && Objects.equal(nodeName, MOUNT_NODE)) {
                if (mountPoint != null) {
                    throw new RestconfDocumentedException("Restconf supports just one mount point in URI.",
                            ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED);
                }

                if (this.mountService == null) {
                    throw new RestconfDocumentedException(
                            "MountService was not found. Finding behind mount points does not work.",
                            ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED);
                }

                final YangInstanceIdentifier partialPath = this.dataNormalizer.toNormalized(builder.build());
                final Optional<DOMMountPoint> mountOpt = this.mountService.getMountPoint(partialPath);
                if (!mountOpt.isPresent()) {
                    LOG.debug("Instance identifier to missing mount point: {}", partialPath);
                    throw new RestconfDocumentedException("Mount point does not exist.", ErrorType.PROTOCOL,
                            ErrorTag.DATA_MISSING);
                }
                final DOMMountPoint mount = mountOpt.get();

                final SchemaContext mountPointSchema = mount.getSchemaContext();
                if (mountPointSchema == null) {
                    throw new RestconfDocumentedException("Mount point does not contain any schema with modules.",
                            ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT);
                }

                if (returnJustMountPoint || (strings.size() == 1)) {
                    final YangInstanceIdentifier instance = YangInstanceIdentifier.builder().build();
                    return new InstanceIdentifierContext<>(instance, mountPointSchema, mount, mountPointSchema);
                }

                final String moduleNameBehindMountPoint = toModuleName(strings.get(1));
                if (moduleNameBehindMountPoint == null) {
                    throw new RestconfDocumentedException(
                            "First node after mount point in URI has to be in format \"moduleName:nodeName\"",
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                }

                final Module moduleBehindMountPoint = mountPointSchema.findModuleByName(moduleNameBehindMountPoint,
                        null);
                if (moduleBehindMountPoint == null) {
                    throw new RestconfDocumentedException(
                            "\"" + moduleName + "\" module does not exist in mount point.", ErrorType.PROTOCOL,
                            ErrorTag.UNKNOWN_ELEMENT);
                }

                final List<String> subList = strings.subList(1, strings.size());
                return collectPathArguments(YangInstanceIdentifier.builder(), subList, moduleBehindMountPoint, mount,
                        returnJustMountPoint);
            }

            Module module = null;
            if (mountPoint == null) {
                checkPreconditions();
                module = this.globalSchema.findModuleByName(moduleName, null);
                if (module == null) {
                    throw new RestconfDocumentedException("\"" + moduleName + "\" module does not exist.",
                            ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
                }
            } else {
                final SchemaContext schemaContext = mountPoint.getSchemaContext();
                if (schemaContext != null) {
                    module = schemaContext.findModuleByName(moduleName, null);
                } else {
                    module = null;
                }
                if (module == null) {
                    throw new RestconfDocumentedException(
                            "\"" + moduleName + "\" module does not exist in mount point.", ErrorType.PROTOCOL,
                            ErrorTag.UNKNOWN_ELEMENT);
                }
            }

            targetNode = RestSchemaControllerUtil.findInstanceDataChildByNameAndNamespace(parentNode, nodeName,
                    module.getNamespace());

            if ((targetNode == null) && (parentNode instanceof Module)) {
                final RpcDefinition rpc = getRpcDefinition(head, module.getRevision());
                if (rpc != null) {
                    return new InstanceIdentifierContext<RpcDefinition>(builder.build(), rpc, mountPoint,
                            mountPoint != null ? mountPoint.getSchemaContext() : this.globalSchema);
                }
            }

            if (targetNode == null) {
                throw new RestconfDocumentedException("URI has bad format. Possible reasons:\n" + " 1. \"" + head
                        + "\" was not found in parent data node.\n" + " 2. \"" + head
                        + "\" is behind mount point. Then it should be in format \"/" + MOUNT + "/" + head + "\".",
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        } else {
            final List<DataSchemaNode> potentialSchemaNodes = RestSchemaControllerUtil
                    .findInstanceDataChildrenByName(parentNode, nodeName);
            if (potentialSchemaNodes.size() > 1) {
                final StringBuilder strBuilder = new StringBuilder();
                for (final DataSchemaNode potentialNodeSchema : potentialSchemaNodes) {
                    strBuilder.append("   ").append(potentialNodeSchema.getQName().getNamespace()).append("\n");
                }

                throw new RestconfDocumentedException("URI has bad format. Node \"" + nodeName
                        + "\" is added as augment from more than one module. "
                        + "Therefore the node must have module name and it has to be in format \"moduleName:nodeName\"."
                        + "\nThe node is added as augment from modules with namespaces:\n" + strBuilder.toString(),
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            if (potentialSchemaNodes.isEmpty()) {
                throw new RestconfDocumentedException("\"" + nodeName + "\" in URI was not found in parent data node",
                        ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
            }

            targetNode = potentialSchemaNodes.iterator().next();
        }

        if (!isListOrContainer(targetNode)) {
            throw new RestconfDocumentedException(
                    "URI has bad format. Node \"" + head + "\" must be Container or List yang type.",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        int consumed = 1;
        if ((targetNode instanceof ListSchemaNode)) {
            final ListSchemaNode listNode = ((ListSchemaNode) targetNode);
            final int keysSize = listNode.getKeyDefinition().size();
            if ((strings.size() - consumed) < keysSize) {
                throw new RestconfDocumentedException(
                        "Missing key for list \"" + listNode.getQName().getLocalName() + "\".", ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING);
            }

            final List<String> uriKeyValues = strings.subList(consumed, consumed + keysSize);
            final HashMap<QName, Object> keyValues = new HashMap<QName, Object>();
            int i = 0;
            for (final QName key : listNode.getKeyDefinition()) {
                {
                    final String uriKeyValue = uriKeyValues.get(i);
                    if (uriKeyValue.equals(NULL_VALUE)) {
                        throw new RestconfDocumentedException(
                                "URI has bad format. List \"" + listNode.getQName().getLocalName()
                                        + "\" cannot contain \"null\" value as a key.",
                                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                    }

                    addKeyValue(keyValues, listNode.getDataChildByName(key), uriKeyValue, mountPoint);
                    i++;
                }
            }

            consumed = consumed + i;
            builder.nodeWithKey(targetNode.getQName(), keyValues);
        } else {
            builder.node(targetNode.getQName());
        }

        if ((targetNode instanceof DataNodeContainer)) {
            final List<String> remaining = strings.subList(consumed, strings.size());
            return collectPathArguments(builder, remaining, ((DataNodeContainer) targetNode), mountPoint,
                    returnJustMountPoint);
        }

        return createContext(builder.build(), targetNode, mountPoint,
                mountPoint != null ? mountPoint.getSchemaContext() : this.globalSchema);
    }

    private InstanceIdentifierContext<?> createContext(final YangInstanceIdentifier instance,
            final DataSchemaNode dataSchemaNode, final DOMMountPoint mountPoint, final SchemaContext schemaContext) {

        final YangInstanceIdentifier instanceIdentifier = new DataNormalizer(schemaContext).toNormalized(instance);
        return new InstanceIdentifierContext<>(instanceIdentifier, dataSchemaNode, mountPoint, schemaContext);
    }

    private static String toNodeName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return str;
        }

        // Make sure there is only one occurrence
        if (str.indexOf(':', idx + 1) != -1) {
            return str;
        }

        return str.substring(idx + 1);
    }

    private static boolean isListOrContainer(final DataSchemaNode node) {
        return (node instanceof ListSchemaNode) || (node instanceof ContainerSchemaNode);
    }

    private RpcDefinition getRpcDefinition(final String name, final Date revisionDate) {
        final QName validName = toQName(name, revisionDate);
        return validName == null ? null : this.qnameToRpc.get().get(validName);
    }

    private QName toQName(final String name, final Date revisionDate) {
        checkPreconditions();
        final String module = toModuleName(name);
        final String node = toNodeName(name);
        final Module m = this.globalSchema.findModuleByName(module, revisionDate);
        return m == null ? null : QName.create(m.getQNameModule(), node);
    }

    private void addKeyValue(final HashMap<QName, Object> map, final DataSchemaNode node, final String uriValue,
            final DOMMountPoint mountPoint) {
        Preconditions.checkNotNull(uriValue);
        Preconditions.checkArgument((node instanceof LeafSchemaNode));

        final String urlDecoded = urlPathArgDecode(uriValue);
        TypeDefinition<?> typedef = ((LeafSchemaNode) node).getType();
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) baseType, this.globalSchema,
                    node);
        }
        final Codec<Object, Object> codec = RestCodec.from(typedef, mountPoint);
        Object decoded = codec.deserialize(urlDecoded);
        String additionalInfo = "";
        if (decoded == null) {
            if ((baseType instanceof IdentityrefTypeDefinition)) {
                decoded = toQName(urlDecoded, null);
                additionalInfo = "For key which is of type identityref it should be in format module_name:identity_name.";
            }
        }

        if (decoded == null) {
            throw new RestconfDocumentedException(uriValue + " from URI can't be resolved. " + additionalInfo,
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        map.put(node.getQName(), decoded);
    }

    private String urlPathArgDecode(final String pathArg) {
        if (pathArg != null) {
            try {
                return URLDecoder.decode(pathArg, URI_ENCODING_CHAR_SET);
            } catch (final UnsupportedEncodingException e) {
                throw new RestconfDocumentedException("Invalid URL path arg '" + pathArg + "': " + e.getMessage(),
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
        return null;
    }

    // CHANGE ARGUMENT WITH IETF_MONITORING_QNAME AFTER IMPL IN MDSAL
    @Override
    public Module getMonitoringModule() {
        return findModuleByNameAndRevision(Draft09.RestConfModule.IETF_RESTCONF_QNAME);
    }
}
