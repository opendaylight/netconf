/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.Draft02.RestConfModule;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.yangtools.concepts.IllegalArgumentCodec;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ControllerContext implements EffectiveModelContextListener, Closeable {
    // FIXME: this should be in md-sal somewhere
    public static final String MOUNT = "yang-ext:mount";

    private static final Logger LOG = LoggerFactory.getLogger(ControllerContext.class);

    private static final String NULL_VALUE = "null";

    private static final String MOUNT_MODULE = "yang-ext";

    private static final String MOUNT_NODE = "mount";

    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    private final AtomicReference<Map<QName, RpcDefinition>> qnameToRpc = new AtomicReference<>(Collections.emptyMap());

    private final DOMMountPointService mountService;
    private final DOMYangTextSourceProvider yangTextSourceProvider;
    private final ListenerRegistration<?> listenerRegistration;
    private volatile EffectiveModelContext globalSchema;
    private volatile DataNormalizer dataNormalizer;

    @Inject
    public ControllerContext(final DOMSchemaService schemaService, final DOMMountPointService mountService,
            final DOMSchemaService domSchemaService) {
        this.mountService = mountService;
        yangTextSourceProvider = domSchemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);

        onModelContextUpdated(schemaService.getGlobalContext());
        listenerRegistration = schemaService.registerSchemaContextListener(this);
    }

    /**
     * Factory method.
     *
     * @deprecated Just use the
     *             {@link #ControllerContext(DOMSchemaService, DOMMountPointService, DOMSchemaService)}
     *             constructor instead.
     */
    @Deprecated
    public static ControllerContext newInstance(final DOMSchemaService schemaService,
            final DOMMountPointService mountService, final DOMSchemaService domSchemaService) {
        return new ControllerContext(schemaService, mountService, domSchemaService);
    }

    private void setGlobalSchema(final EffectiveModelContext globalSchema) {
        this.globalSchema = globalSchema;
        dataNormalizer = new DataNormalizer(globalSchema);
    }

    public DOMYangTextSourceProvider getYangTextSourceProvider() {
        return yangTextSourceProvider;
    }

    private void checkPreconditions() {
        if (globalSchema == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        listenerRegistration.close();
    }

    public void setSchemas(final EffectiveModelContext schemas) {
        onModelContextUpdated(schemas);
    }

    public InstanceIdentifierContext toInstanceIdentifier(final String restconfInstance) {
        return toIdentifier(restconfInstance, false);
    }

    public EffectiveModelContext getGlobalSchema() {
        return globalSchema;
    }

    public InstanceIdentifierContext toMountPointIdentifier(final String restconfInstance) {
        return toIdentifier(restconfInstance, true);
    }

    private InstanceIdentifierContext toIdentifier(final String restconfInstance,
                                                   final boolean toMountPointIdentifier) {
        checkPreconditions();

        if (restconfInstance == null) {
            return InstanceIdentifierContext.ofLocalRoot(globalSchema);
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

        final Collection<? extends Module> latestModule = globalSchema.findModules(startModule);
        if (latestModule.isEmpty()) {
            throw new RestconfDocumentedException("The module named '" + startModule + "' does not exist.",
                    ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final InstanceIdentifierContext iiWithSchemaNode = collectPathArguments(YangInstanceIdentifier.builder(),
            new ArrayDeque<>(), pathArgs, latestModule.iterator().next(), null, toMountPointIdentifier);

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

    public Module findModuleByName(final String moduleName) {
        checkPreconditions();
        checkArgument(moduleName != null && !moduleName.isEmpty());
        return globalSchema.findModules(moduleName).stream().findFirst().orElse(null);
    }

    public static Module findModuleByName(final DOMMountPoint mountPoint, final String moduleName) {
        checkArgument(moduleName != null && mountPoint != null);

        final EffectiveModelContext mountPointSchema = getModelContext(mountPoint);
        return mountPointSchema == null ? null
            : mountPointSchema.findModules(moduleName).stream().findFirst().orElse(null);
    }

    public Module findModuleByNamespace(final XMLNamespace namespace) {
        checkPreconditions();
        checkArgument(namespace != null);
        return globalSchema.findModules(namespace).stream().findFirst().orElse(null);
    }

    public static Module findModuleByNamespace(final DOMMountPoint mountPoint, final XMLNamespace namespace) {
        checkArgument(namespace != null && mountPoint != null);

        final EffectiveModelContext mountPointSchema = getModelContext(mountPoint);
        return mountPointSchema == null ? null
            : mountPointSchema.findModules(namespace).stream().findFirst().orElse(null);
    }

    public Module findModuleByNameAndRevision(final String name, final Revision revision) {
        checkPreconditions();
        checkArgument(name != null && revision != null);

        return globalSchema.findModule(name, revision).orElse(null);
    }

    public Module findModuleByNameAndRevision(final DOMMountPoint mountPoint, final String name,
            final Revision revision) {
        checkPreconditions();
        checkArgument(name != null && revision != null && mountPoint != null);

        final EffectiveModelContext schemaContext = getModelContext(mountPoint);
        return schemaContext == null ? null : schemaContext.findModule(name, revision).orElse(null);
    }

    public DataNodeContainer getDataNodeContainerFor(final YangInstanceIdentifier path) {
        checkPreconditions();

        final Iterable<PathArgument> elements = path.getPathArguments();
        final PathArgument head = elements.iterator().next();
        final QName startQName = head.getNodeType();
        final Module initialModule = globalSchema.findModule(startQName.getModule()).orElse(null);
        DataNodeContainer node = initialModule;
        for (final PathArgument element : elements) {
            final QName _nodeType = element.getNodeType();
            final DataSchemaNode potentialNode = childByQName(node, _nodeType);
            if (potentialNode == null || !isListOrContainer(potentialNode)) {
                return null;
            }
            node = (DataNodeContainer) potentialNode;
        }

        return node;
    }

    public String toFullRestconfIdentifier(final YangInstanceIdentifier path, final DOMMountPoint mount) {
        checkPreconditions();

        final Iterable<PathArgument> elements = path.getPathArguments();
        final StringBuilder builder = new StringBuilder();
        final PathArgument head = elements.iterator().next();
        final QName startQName = head.getNodeType();
        final EffectiveModelContext schemaContext;
        if (mount != null) {
            schemaContext = getModelContext(mount);
        } else {
            schemaContext = globalSchema;
        }
        final Module initialModule = schemaContext.findModule(startQName.getModule()).orElse(null);
        DataNodeContainer node = initialModule;
        for (final PathArgument element : elements) {
            if (!(element instanceof AugmentationIdentifier)) {
                final QName _nodeType = element.getNodeType();
                final DataSchemaNode potentialNode = childByQName(node, _nodeType);
                if ((!(element instanceof NodeIdentifier) || !(potentialNode instanceof ListSchemaNode))
                        && !(potentialNode instanceof ChoiceSchemaNode)) {
                    builder.append(convertToRestconfIdentifier(element, potentialNode, mount));
                    if (potentialNode instanceof DataNodeContainer) {
                        node = (DataNodeContainer) potentialNode;
                    }
                }
            }
        }

        return builder.toString();
    }

    public String findModuleNameByNamespace(final XMLNamespace namespace) {
        checkPreconditions();

        final Module module = findModuleByNamespace(namespace);
        return module == null ? null : module.getName();
    }

    public static String findModuleNameByNamespace(final DOMMountPoint mountPoint, final XMLNamespace namespace) {
        final Module module = findModuleByNamespace(mountPoint, namespace);
        return module == null ? null : module.getName();
    }

    public XMLNamespace findNamespaceByModuleName(final String moduleName) {
        final Module module = findModuleByName(moduleName);
        return module == null ? null : module.getNamespace();
    }

    public static XMLNamespace findNamespaceByModuleName(final DOMMountPoint mountPoint, final String moduleName) {
        final Module module = findModuleByName(mountPoint, moduleName);
        return module == null ? null : module.getNamespace();
    }

    public Collection<? extends Module> getAllModules(final DOMMountPoint mountPoint) {
        checkPreconditions();

        final EffectiveModelContext schemaContext = mountPoint == null ? null : getModelContext(mountPoint);
        return schemaContext == null ? null : schemaContext.getModules();
    }

    public Collection<? extends Module> getAllModules() {
        checkPreconditions();
        return globalSchema.getModules();
    }

    private static String toRestconfIdentifier(final EffectiveModelContext context, final QName qname) {
        final Module schema = context.findModule(qname.getModule()).orElse(null);
        return schema == null ? null : schema.getName() + ':' + qname.getLocalName();
    }

    public String toRestconfIdentifier(final QName qname, final DOMMountPoint mountPoint) {
        return mountPoint != null ? toRestconfIdentifier(getModelContext(mountPoint), qname)
            : toRestconfIdentifier(qname);
    }

    public String toRestconfIdentifier(final QName qname) {
        checkPreconditions();

        return toRestconfIdentifier(globalSchema, qname);
    }

    public static String toRestconfIdentifier(final DOMMountPoint mountPoint, final QName qname) {
        return mountPoint == null ? null : toRestconfIdentifier(getModelContext(mountPoint), qname);
    }

    public Module getRestconfModule() {
        return findModuleByNameAndRevision(Draft02.RestConfModule.NAME, Revision.of(Draft02.RestConfModule.REVISION));
    }

    public Entry<SchemaInferenceStack, ContainerSchemaNode> getRestconfModuleErrorsSchemaNode() {
        checkPreconditions();

        final var schema = globalSchema;
        final var namespace = QNameModule.create(XMLNamespace.of(Draft02.RestConfModule.NAMESPACE),
            Revision.of(Draft02.RestConfModule.REVISION));
        if (schema.findModule(namespace).isEmpty()) {
            return null;
        }

        final var stack = SchemaInferenceStack.of(globalSchema);
        stack.enterGrouping(RestConfModule.ERRORS_QNAME);
        final var stmt = stack.enterSchemaTree(RestConfModule.ERRORS_QNAME);
        verify(stmt instanceof ContainerSchemaNode, "Unexpected statement %s", stmt);
        return Map.entry(stack, (ContainerSchemaNode) stmt);
    }

    public DataSchemaNode getRestconfModuleRestConfSchemaNode(final Module inRestconfModule,
            final String schemaNodeName) {
        Module restconfModule = inRestconfModule;
        if (restconfModule == null) {
            restconfModule = getRestconfModule();
        }

        if (restconfModule == null) {
            return null;
        }

        final Collection<? extends GroupingDefinition> groupings = restconfModule.getGroupings();
        final Iterable<? extends GroupingDefinition> filteredGroups = Iterables.filter(groupings,
            g -> RestConfModule.RESTCONF_GROUPING_SCHEMA_NODE.equals(g.getQName().getLocalName()));
        final GroupingDefinition restconfGrouping = Iterables.getFirst(filteredGroups, null);

        final var instanceDataChildrenByName = findInstanceDataChildrenByName(restconfGrouping,
                RestConfModule.RESTCONF_CONTAINER_SCHEMA_NODE);
        final DataSchemaNode restconfContainer = getFirst(instanceDataChildrenByName);

        if (RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE.equals(schemaNodeName)) {
            final var instances = findInstanceDataChildrenByName(
                    (DataNodeContainer) restconfContainer, RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            return getFirst(instances);
        } else if (RestConfModule.STREAM_LIST_SCHEMA_NODE.equals(schemaNodeName)) {
            var instances = findInstanceDataChildrenByName(
                    (DataNodeContainer) restconfContainer, RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            final DataSchemaNode modules = getFirst(instances);
            instances = findInstanceDataChildrenByName((DataNodeContainer) modules,
                    RestConfModule.STREAM_LIST_SCHEMA_NODE);
            return getFirst(instances);
        } else if (RestConfModule.MODULES_CONTAINER_SCHEMA_NODE.equals(schemaNodeName)) {
            final var instances = findInstanceDataChildrenByName(
                    (DataNodeContainer) restconfContainer, RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
            return getFirst(instances);
        } else if (RestConfModule.MODULE_LIST_SCHEMA_NODE.equals(schemaNodeName)) {
            var instances = findInstanceDataChildrenByName(
                    (DataNodeContainer) restconfContainer, RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
            final DataSchemaNode modules = getFirst(instances);
            instances = findInstanceDataChildrenByName((DataNodeContainer) modules,
                    RestConfModule.MODULE_LIST_SCHEMA_NODE);
            return getFirst(instances);
        } else if (RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE.equals(schemaNodeName)) {
            final var instances = findInstanceDataChildrenByName(
                    (DataNodeContainer) restconfContainer, RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
            return getFirst(instances);
        }

        return null;
    }

    public static @Nullable DataSchemaNode getFirst(final List<FoundChild> children) {
        return children.isEmpty() ? null : children.get(0).child;
    }

    private static DataSchemaNode childByQName(final ChoiceSchemaNode container, final QName name) {
        for (final CaseSchemaNode caze : container.getCases()) {
            final DataSchemaNode ret = childByQName(caze, name);
            if (ret != null) {
                return ret;
            }
        }

        return null;
    }

    private static DataSchemaNode childByQName(final CaseSchemaNode container, final QName name) {
        return container.dataChildByName(name);
    }

    private static DataSchemaNode childByQName(final ContainerSchemaNode container, final QName name) {
        return dataNodeChildByQName(container, name);
    }

    private static DataSchemaNode childByQName(final ListSchemaNode container, final QName name) {
        return dataNodeChildByQName(container, name);
    }

    private static DataSchemaNode childByQName(final Module container, final QName name) {
        return dataNodeChildByQName(container, name);
    }

    private static DataSchemaNode childByQName(final DataSchemaNode container, final QName name) {
        return null;
    }


    private static DataSchemaNode childByQName(final Object container, final QName name) {
        if (container instanceof CaseSchemaNode) {
            return childByQName((CaseSchemaNode) container, name);
        } else if (container instanceof ChoiceSchemaNode) {
            return childByQName((ChoiceSchemaNode) container, name);
        } else if (container instanceof ContainerSchemaNode) {
            return childByQName((ContainerSchemaNode) container, name);
        } else if (container instanceof ListSchemaNode) {
            return childByQName((ListSchemaNode) container, name);
        } else if (container instanceof DataSchemaNode) {
            return childByQName((DataSchemaNode) container, name);
        } else if (container instanceof Module) {
            return childByQName((Module) container, name);
        } else {
            throw new IllegalArgumentException("Unhandled parameter types: "
                    + Arrays.asList(container, name).toString());
        }
    }

    private static DataSchemaNode dataNodeChildByQName(final DataNodeContainer container, final QName name) {
        final DataSchemaNode ret = container.dataChildByName(name);
        if (ret == null) {
            for (final DataSchemaNode node : container.getChildNodes()) {
                if (node instanceof ChoiceSchemaNode) {
                    final ChoiceSchemaNode choiceNode = (ChoiceSchemaNode) node;
                    final DataSchemaNode childByQName = childByQName(choiceNode, name);
                    if (childByQName != null) {
                        return childByQName;
                    }
                }
            }
        }
        return ret;
    }

    private String toUriString(final Object object, final LeafSchemaNode leafNode, final DOMMountPoint mount)
            throws UnsupportedEncodingException {
        final IllegalArgumentCodec<Object, Object> codec = RestCodec.from(leafNode.getType(), mount, this);
        return object == null ? "" : URLEncoder.encode(codec.serialize(object).toString(), StandardCharsets.UTF_8);
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "Unrecognised NullableDecl")
    private InstanceIdentifierContext collectPathArguments(final InstanceIdentifierBuilder builder,
            final Deque<QName> schemaPath, final List<String> strings, final DataNodeContainer parentNode,
            final DOMMountPoint mountPoint, final boolean returnJustMountPoint) {
        requireNonNull(strings);

        if (parentNode == null) {
            return null;
        }

        if (strings.isEmpty()) {
            return createContext(builder.build(), (DataSchemaNode) parentNode, mountPoint,
                mountPoint != null ? getModelContext(mountPoint) : globalSchema);
        }

        final String head = strings.iterator().next();

        if (head.isEmpty()) {
            final List<String> remaining = strings.subList(1, strings.size());
            return collectPathArguments(builder, schemaPath, remaining, parentNode, mountPoint, returnJustMountPoint);
        }

        final String nodeName = toNodeName(head);
        final String moduleName = toModuleName(head);

        DataSchemaNode targetNode = null;
        if (!Strings.isNullOrEmpty(moduleName)) {
            if (MOUNT_MODULE.equals(moduleName) && MOUNT_NODE.equals(nodeName)) {
                if (mountPoint != null) {
                    throw new RestconfDocumentedException("Restconf supports just one mount point in URI.",
                            ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED);
                }

                if (mountService == null) {
                    throw new RestconfDocumentedException(
                            "MountService was not found. Finding behind mount points does not work.",
                            ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED);
                }

                final YangInstanceIdentifier partialPath = dataNormalizer.toNormalized(builder.build()).getKey();
                final Optional<DOMMountPoint> mountOpt = mountService.getMountPoint(partialPath);
                if (mountOpt.isEmpty()) {
                    LOG.debug("Instance identifier to missing mount point: {}", partialPath);
                    throw new RestconfDocumentedException("Mount point does not exist.", ErrorType.PROTOCOL,
                            ErrorTag.DATA_MISSING);
                }
                final DOMMountPoint mount = mountOpt.get();

                final EffectiveModelContext mountPointSchema = getModelContext(mount);
                if (mountPointSchema == null) {
                    throw new RestconfDocumentedException("Mount point does not contain any schema with modules.",
                            ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT);
                }

                if (returnJustMountPoint || strings.size() == 1) {
                    return InstanceIdentifierContext.ofMountPointRoot(mount, mountPointSchema);
                }

                final String moduleNameBehindMountPoint = toModuleName(strings.get(1));
                if (moduleNameBehindMountPoint == null) {
                    throw new RestconfDocumentedException(
                            "First node after mount point in URI has to be in format \"moduleName:nodeName\"",
                            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                }

                final Iterator<? extends Module> it = mountPointSchema.findModules(moduleNameBehindMountPoint)
                        .iterator();
                if (!it.hasNext()) {
                    throw new RestconfDocumentedException("\"" + moduleNameBehindMountPoint
                            + "\" module does not exist in mount point.", ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
                }

                final List<String> subList = strings.subList(1, strings.size());
                return collectPathArguments(YangInstanceIdentifier.builder(), new ArrayDeque<>(), subList, it.next(),
                        mount, returnJustMountPoint);
            }

            Module module = null;
            if (mountPoint == null) {
                checkPreconditions();
                module = globalSchema.findModules(moduleName).stream().findFirst().orElse(null);
                if (module == null) {
                    throw new RestconfDocumentedException("\"" + moduleName + "\" module does not exist.",
                            ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
                }
            } else {
                final EffectiveModelContext schemaContext = getModelContext(mountPoint);
                if (schemaContext != null) {
                    module = schemaContext.findModules(moduleName).stream().findFirst().orElse(null);
                } else {
                    module = null;
                }
                if (module == null) {
                    throw new RestconfDocumentedException("\"" + moduleName
                            + "\" module does not exist in mount point.", ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
                }
            }

            final var found = findInstanceDataChildByNameAndNamespace(parentNode, nodeName, module.getNamespace());
            if (found == null) {
                if (parentNode instanceof Module) {
                    final RpcDefinition rpc;
                    if (mountPoint == null) {
                        rpc = getRpcDefinition(head, module.getRevision());
                    } else {
                        rpc = getRpcDefinition(module, toNodeName(head));
                    }
                    if (rpc != null) {
                        final var ctx = mountPoint == null ? globalSchema : getModelContext(mountPoint);
                        return InstanceIdentifierContext.ofRpcInput(ctx, rpc, mountPoint);
                    }
                }
                throw new RestconfDocumentedException("URI has bad format. Possible reasons:\n" + " 1. \"" + head
                    + "\" was not found in parent data node.\n" + " 2. \"" + head
                    + "\" is behind mount point. Then it should be in format \"/" + MOUNT + "/" + head + "\".",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            targetNode = found.child;
            schemaPath.addAll(found.intermediate);
            schemaPath.add(targetNode.getQName());
        } else {
            final var potentialSchemaNodes = findInstanceDataChildrenByName(parentNode, nodeName);
            if (potentialSchemaNodes.size() > 1) {
                final StringBuilder strBuilder = new StringBuilder();
                for (var potentialNodeSchema : potentialSchemaNodes) {
                    strBuilder.append("   ").append(potentialNodeSchema.child.getQName().getNamespace()).append("\n");
                }

                throw new RestconfDocumentedException(
                        "URI has bad format. Node \""
                                + nodeName + "\" is added as augment from more than one module. "
                                + "Therefore the node must have module name "
                                + "and it has to be in format \"moduleName:nodeName\"."
                                + "\nThe node is added as augment from modules with namespaces:\n"
                                + strBuilder.toString(), ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }

            if (potentialSchemaNodes.isEmpty()) {
                throw new RestconfDocumentedException("\"" + nodeName + "\" in URI was not found in parent data node",
                        ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
            }

            final var found = potentialSchemaNodes.get(0);
            targetNode = found.child;
            schemaPath.addAll(found.intermediate);
            schemaPath.add(targetNode.getQName());
        }

        if (!isListOrContainer(targetNode)) {
            throw new RestconfDocumentedException("URI has bad format. Node \"" + head
                    + "\" must be Container or List yang type.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        int consumed = 1;
        if (targetNode instanceof ListSchemaNode) {
            final ListSchemaNode listNode = (ListSchemaNode) targetNode;
            final int keysSize = listNode.getKeyDefinition().size();
            if (strings.size() - consumed < keysSize) {
                throw new RestconfDocumentedException("Missing key for list \"" + listNode.getQName().getLocalName()
                        + "\".", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
            }

            final List<String> uriKeyValues = strings.subList(consumed, consumed + keysSize);
            final HashMap<QName, Object> keyValues = new HashMap<>();
            int index = 0;
            for (final QName key : listNode.getKeyDefinition()) {
                final String uriKeyValue = uriKeyValues.get(index);
                if (uriKeyValue.equals(NULL_VALUE)) {
                    throw new RestconfDocumentedException("URI has bad format. List \""
                        + listNode.getQName().getLocalName() + "\" cannot contain \"null\" value as a key.",
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
                }

                final var keyChild = listNode.getDataChildByName(key);
                schemaPath.addLast(keyChild.getQName());
                addKeyValue(keyValues, schemaPath, keyChild, uriKeyValue, mountPoint);
                schemaPath.removeLast();
                index++;
            }

            consumed = consumed + index;
            builder.nodeWithKey(targetNode.getQName(), keyValues);
        } else {
            builder.node(targetNode.getQName());
        }

        if (targetNode instanceof DataNodeContainer) {
            final List<String> remaining = strings.subList(consumed, strings.size());
            return collectPathArguments(builder, schemaPath, remaining, (DataNodeContainer) targetNode, mountPoint,
                    returnJustMountPoint);
        }

        return createContext(builder.build(), targetNode, mountPoint,
            mountPoint != null ? getModelContext(mountPoint) : globalSchema);
    }

    private static InstanceIdentifierContext createContext(final YangInstanceIdentifier instance,
            final DataSchemaNode dataSchemaNode, final DOMMountPoint mountPoint,
            final EffectiveModelContext schemaContext) {
        final var normalized = new DataNormalizer(schemaContext).toNormalized(instance);
        return InstanceIdentifierContext.ofPath(normalized.getValue(), dataSchemaNode, normalized.getKey(), mountPoint);
    }

    public static @Nullable FoundChild findInstanceDataChildByNameAndNamespace(final DataNodeContainer container,
            final String name, final XMLNamespace namespace) {
        requireNonNull(namespace);

        for (var node : findInstanceDataChildrenByName(container, name)) {
            if (namespace.equals(node.child.getQName().getNamespace())) {
                return node;
            }
        }
        return null;
    }

    public static List<FoundChild> findInstanceDataChildrenByName(final DataNodeContainer container,
            final String name) {
        final List<FoundChild> instantiatedDataNodeContainers = new ArrayList<>();
        collectInstanceDataNodeContainers(instantiatedDataNodeContainers, requireNonNull(container),
            requireNonNull(name), List.of());
        return instantiatedDataNodeContainers;
    }

    private static void collectInstanceDataNodeContainers(final List<FoundChild> potentialSchemaNodes,
            final DataNodeContainer container, final String name, final List<QName> intermediate) {
        // We perform two iterations to retain breadth-first ordering
        for (var child : container.getChildNodes()) {
            if (name.equals(child.getQName().getLocalName()) && isInstantiatedDataSchema(child)) {
                potentialSchemaNodes.add(new FoundChild(child, intermediate));
            }
        }

        for (var child : container.getChildNodes()) {
            if (child instanceof ChoiceSchemaNode) {
                for (var caze : ((ChoiceSchemaNode) child).getCases()) {
                    collectInstanceDataNodeContainers(potentialSchemaNodes, caze, name,
                        ImmutableList.<QName>builderWithExpectedSize(intermediate.size() + 2)
                            .addAll(intermediate).add(child.getQName()).add(caze.getQName())
                            .build());
                }
            }
        }
    }

    public static boolean isInstantiatedDataSchema(final DataSchemaNode node) {
        return node instanceof LeafSchemaNode || node instanceof LeafListSchemaNode
                || node instanceof ContainerSchemaNode || node instanceof ListSchemaNode
                || node instanceof AnyxmlSchemaNode;
    }

    private void addKeyValue(final HashMap<QName, Object> map, final Deque<QName> schemaPath, final DataSchemaNode node,
            final String uriValue, final DOMMountPoint mountPoint) {
        checkArgument(node instanceof LeafSchemaNode);

        final EffectiveModelContext schemaContext = mountPoint == null ? globalSchema : getModelContext(mountPoint);
        final String urlDecoded = urlPathArgDecode(requireNonNull(uriValue));
        TypeDefinition<?> typedef = ((LeafSchemaNode) node).getType();
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            final var stack = SchemaInferenceStack.of(schemaContext);
            schemaPath.forEach(stack::enterSchemaTree);
            typedef = stack.resolveLeafref((LeafrefTypeDefinition) baseType);
        }
        final IllegalArgumentCodec<Object, Object> codec = RestCodec.from(typedef, mountPoint, this);
        Object decoded = codec.deserialize(urlDecoded);
        String additionalInfo = "";
        if (decoded == null) {
            if (typedef instanceof IdentityrefTypeDefinition) {
                decoded = toQName(schemaContext, urlDecoded);
                additionalInfo =
                        "For key which is of type identityref it should be in format module_name:identity_name.";
            }
        }

        if (decoded == null) {
            throw new RestconfDocumentedException(uriValue + " from URI can't be resolved. " + additionalInfo,
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        map.put(node.getQName(), decoded);
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

    private QName toQName(final EffectiveModelContext schemaContext, final String name,
            final Optional<Revision> revisionDate) {
        checkPreconditions();
        final String module = toModuleName(name);
        final String node = toNodeName(name);
        final Module m = schemaContext.findModule(module, revisionDate).orElse(null);
        return m == null ? null : QName.create(m.getQNameModule(), node);
    }

    private QName toQName(final EffectiveModelContext schemaContext, final String name) {
        checkPreconditions();
        final String module = toModuleName(name);
        final String node = toNodeName(name);
        final Collection<? extends Module> modules = schemaContext.findModules(module);
        return modules.isEmpty() ? null : QName.create(modules.iterator().next().getQNameModule(), node);
    }

    private static boolean isListOrContainer(final DataSchemaNode node) {
        return node instanceof ListSchemaNode || node instanceof ContainerSchemaNode;
    }

    public RpcDefinition getRpcDefinition(final String name, final Optional<Revision> revisionDate) {
        final QName validName = toQName(globalSchema, name, revisionDate);
        return validName == null ? null : qnameToRpc.get().get(validName);
    }

    public RpcDefinition getRpcDefinition(final String name) {
        final QName validName = toQName(globalSchema, name);
        return validName == null ? null : qnameToRpc.get().get(validName);
    }

    private static RpcDefinition getRpcDefinition(final Module module, final String rpcName) {
        final QName rpcQName = QName.create(module.getQNameModule(), rpcName);
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            if (rpcQName.equals(rpcDefinition.getQName())) {
                return rpcDefinition;
            }
        }
        return null;
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext context) {
        if (context != null) {
            final Collection<? extends RpcDefinition> defs = context.getOperations();
            final Map<QName, RpcDefinition> newMap = new HashMap<>(defs.size());

            for (final RpcDefinition operation : defs) {
                newMap.put(operation.getQName(), operation);
            }

            // FIXME: still not completely atomic
            qnameToRpc.set(ImmutableMap.copyOf(newMap));
            setGlobalSchema(context);
        }
    }

    private static List<String> urlPathArgsDecode(final Iterable<String> strings) {
        final List<String> decodedPathArgs = new ArrayList<>();
        for (final String pathArg : strings) {
            final String _decode = URLDecoder.decode(pathArg, StandardCharsets.UTF_8);
            decodedPathArgs.add(_decode);
        }
        return decodedPathArgs;
    }

    static String urlPathArgDecode(final String pathArg) {
        if (pathArg == null) {
            return null;
        }
        return URLDecoder.decode(pathArg, StandardCharsets.UTF_8);
    }

    private String convertToRestconfIdentifier(final PathArgument argument, final DataSchemaNode node,
            final DOMMountPoint mount) {
        if (argument instanceof NodeIdentifier) {
            return convertToRestconfIdentifier((NodeIdentifier) argument, mount);
        } else if (argument instanceof NodeIdentifierWithPredicates && node instanceof ListSchemaNode) {
            return convertToRestconfIdentifierWithPredicates((NodeIdentifierWithPredicates) argument,
                (ListSchemaNode) node, mount);
        } else if (argument != null && node != null) {
            throw new IllegalArgumentException("Conversion of generic path argument is not supported");
        } else {
            throw new IllegalArgumentException("Unhandled parameter types: " + Arrays.asList(argument, node));
        }
    }

    private String convertToRestconfIdentifier(final NodeIdentifier argument, final DOMMountPoint node) {
        return "/" + toRestconfIdentifier(argument.getNodeType(), node);
    }

    private String convertToRestconfIdentifierWithPredicates(final NodeIdentifierWithPredicates argument,
            final ListSchemaNode node, final DOMMountPoint mount) {
        final QName nodeType = argument.getNodeType();
        final String nodeIdentifier = toRestconfIdentifier(nodeType, mount);

        final StringBuilder builder = new StringBuilder().append('/').append(nodeIdentifier).append('/');

        final List<QName> keyDefinition = node.getKeyDefinition();
        boolean hasElements = false;
        for (final QName key : keyDefinition) {
            for (final DataSchemaNode listChild : node.getChildNodes()) {
                if (listChild.getQName().equals(key)) {
                    if (!hasElements) {
                        hasElements = true;
                    } else {
                        builder.append('/');
                    }

                    checkState(listChild instanceof LeafSchemaNode,
                        "List key has to consist of leaves, not %s", listChild);

                    final Object value = argument.getValue(key);
                    try {
                        builder.append(toUriString(value, (LeafSchemaNode)listChild, mount));
                    } catch (final UnsupportedEncodingException e) {
                        LOG.error("Error parsing URI: {}", value, e);
                        return null;
                    }
                    break;
                }
            }
        }

        return builder.toString();
    }

    public YangInstanceIdentifier toXpathRepresentation(final YangInstanceIdentifier instanceIdentifier) {
        if (dataNormalizer == null) {
            throw new RestconfDocumentedException("Data normalizer isn't set. Normalization isn't possible");
        }

        try {
            return dataNormalizer.toLegacy(instanceIdentifier);
        } catch (final DataNormalizationException e) {
            throw new RestconfDocumentedException("Data normalizer failed. Normalization isn't possible", e);
        }
    }

    public boolean isNodeMixin(final YangInstanceIdentifier path) {
        final DataNormalizationOperation<?> operation;
        try {
            operation = dataNormalizer.getOperation(path);
        } catch (final DataNormalizationException e) {
            throw new RestconfDocumentedException("Data normalizer failed. Normalization isn't possible", e);
        }
        return operation.isMixin();
    }

    private static EffectiveModelContext getModelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }

    public static final class FoundChild {
        // Intermediate schema tree children, usually empty
        public final @NonNull List<QName> intermediate;
        public final @NonNull DataSchemaNode child;

        private FoundChild(final DataSchemaNode child, final List<QName> intermediate) {
            this.child = requireNonNull(child);
            this.intermediate = requireNonNull(intermediate);
        }
    }
}
