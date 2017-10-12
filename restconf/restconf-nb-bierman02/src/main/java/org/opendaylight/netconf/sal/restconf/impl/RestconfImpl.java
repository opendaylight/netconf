/*
 * Copyright (c) 2014 - 2016 Brocade Communication Systems, Inc., Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.netconf.sal.rest.api.RestconfService;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.validation.RestconfValidationUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModifiedNodeDoesNotExistException;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.util.SimpleSchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfImpl implements RestconfService {

    private static final RestconfImpl INSTANCE = new RestconfImpl();

    /**
     * Notifications are served on port 8181.
     */
    private static final int NOTIFICATION_PORT = 8181;

    private static final int CHAR_NOT_FOUND = -1;

    private static final String SAL_REMOTE_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote";

    private static final Logger LOG = LoggerFactory.getLogger(RestconfImpl.class);

    private static final DataChangeScope DEFAULT_SCOPE = DataChangeScope.BASE;

    private static final LogicalDatastoreType DEFAULT_DATASTORE = LogicalDatastoreType.CONFIGURATION;

    private static final URI NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT = URI.create("urn:sal:restconf:event:subscription");

    private static final String DATASTORE_PARAM_NAME = "datastore";

    private static final String SCOPE_PARAM_NAME = "scope";

    private static final String OUTPUT_TYPE_PARAM_NAME = "notification-output-type";

    private static final String NETCONF_BASE = "urn:ietf:params:xml:ns:netconf:base:1.0";

    private static final String NETCONF_BASE_PAYLOAD_NAME = "data";

    private static final QName NETCONF_BASE_QNAME = QName.create(QNameModule.create(URI.create(NETCONF_BASE), null),
        NETCONF_BASE_PAYLOAD_NAME).intern();

    private static final QNameModule SAL_REMOTE_AUGMENT;

    static {
        try {
            SAL_REMOTE_AUGMENT = QNameModule.create(NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT,
                SimpleDateFormatUtil.getRevisionFormat().parse("2014-07-08"));
        } catch (final ParseException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final AugmentationIdentifier SAL_REMOTE_AUG_IDENTIFIER =
            new AugmentationIdentifier(ImmutableSet.of(
                QName.create(SAL_REMOTE_AUGMENT, "scope"), QName.create(SAL_REMOTE_AUGMENT, "datastore"),
                QName.create(SAL_REMOTE_AUGMENT, "notification-output-type")));

    public static final CharSequence DATA_SUBSCR = "data-change-event-subscription";
    private static final CharSequence CREATE_DATA_SUBSCR = "create-" + DATA_SUBSCR;

    public static final CharSequence NOTIFICATION_STREAM = "notification-stream";
    private static final CharSequence CREATE_NOTIFICATION_STREAM = "create-" + NOTIFICATION_STREAM;

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HH:MM", "Z").toFormatter();

    private BrokerFacade broker;

    private ControllerContext controllerContext;

    public void setBroker(final BrokerFacade broker) {
        this.broker = broker;
    }

    public void setControllerContext(final ControllerContext controllerContext) {
        this.controllerContext = controllerContext;
    }

    private RestconfImpl() {
    }

    public static RestconfImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        final Set<Module> allModules = this.controllerContext.getAllModules();
        final MapNode allModuleMap = makeModuleMapNode(allModules);

        final SchemaContext schemaContext = this.controllerContext.getGlobalSchema();

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(allModuleMap);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, modulesSchemaNode, null, schemaContext),
                moduleContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    /**
     * Valid only for mount point.
     */
    @Override
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        if (!identifier.contains(ControllerContext.MOUNT)) {
            final String errMsg = "URI has bad format. If modules behind mount point should be showed,"
                    + " URI has to end with " + ControllerContext.MOUNT;
            LOG.debug(errMsg + " for " + identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext<?> mountPointIdentifier =
                this.controllerContext.toMountPointIdentifier(identifier);
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        final Set<Module> modules = this.controllerContext.getAllModules(mountPoint);
        final MapNode mountPointModulesMap = makeModuleMapNode(modules);

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(mountPointModulesMap);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, modulesSchemaNode, mountPoint,
                        this.controllerContext.getGlobalSchema()),
                moduleContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        Preconditions.checkNotNull(identifier);
        final QName moduleNameAndRevision = getModuleNameAndRevision(identifier);
        Module module = null;
        DOMMountPoint mountPoint = null;
        final SchemaContext schemaContext;
        if (identifier.contains(ControllerContext.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier =
                    this.controllerContext.toMountPointIdentifier(identifier);
            mountPoint = mountPointIdentifier.getMountPoint();
            module = this.controllerContext.findModuleByNameAndRevision(mountPoint, moduleNameAndRevision);
            schemaContext = mountPoint.getSchemaContext();
        } else {
            module = this.controllerContext.findModuleByNameAndRevision(moduleNameAndRevision);
            schemaContext = this.controllerContext.getGlobalSchema();
        }

        if (module == null) {
            final String errMsg = "Module with name '" + moduleNameAndRevision.getLocalName() + "' and revision '"
                    + moduleNameAndRevision.getRevision() + "' was not found.";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final Module restconfModule = getRestconfModule();
        final Set<Module> modules = Collections.singleton(module);
        final MapNode moduleMap = makeModuleMapNode(modules);

        final DataSchemaNode moduleSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, schemaContext), moduleMap,
                QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        final SchemaContext schemaContext = this.controllerContext.getGlobalSchema();
        final Set<String> availableStreams = Notificator.getStreamNames();
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode streamSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.STREAM_LIST_SCHEMA_NODE);
        Preconditions.checkState(streamSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listStreamsBuilder =
                Builders.mapBuilder((ListSchemaNode) streamSchemaNode);

        for (final String streamName : availableStreams) {
            listStreamsBuilder.withChild(toStreamEntryNode(streamName, streamSchemaNode));
        }

        final DataSchemaNode streamsContainerSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(streamsContainerSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> streamsContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) streamsContainerSchemaNode);
        streamsContainerBuilder.withChild(listStreamsBuilder.build());

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, streamsContainerSchemaNode, null, schemaContext),
                streamsContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        final Set<Module> allModules = this.controllerContext.getAllModules();
        return operationsFromModulesToNormalizedContext(allModules, null);
    }

    @Override
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        Set<Module> modules = null;
        DOMMountPoint mountPoint = null;
        if (identifier.contains(ControllerContext.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier =
                    this.controllerContext.toMountPointIdentifier(identifier);
            mountPoint = mountPointIdentifier.getMountPoint();
            modules = this.controllerContext.getAllModules(mountPoint);

        } else {
            final String errMsg =
                    "URI has bad format. If operations behind mount point should be showed, URI has to " + "end with ";
            LOG.debug(errMsg + ControllerContext.MOUNT + " for " + identifier);
            throw new RestconfDocumentedException(errMsg + ControllerContext.MOUNT, ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        return operationsFromModulesToNormalizedContext(modules, mountPoint);
    }

    /**
     * Special case only for GET restconf/operations use (since moment of
     * pre-Beryllium Yang parser and Yang model API removal). The method is
     * creating fake schema context with fake module and fake data by use own
     * implementations of schema nodes and module.
     *
     * @param modules
     *            set of modules for get RPCs from every module
     * @param mountPoint
     *            mount point, if in use otherwise null
     * @return {@link NormalizedNodeContext}
     */
    private static NormalizedNodeContext operationsFromModulesToNormalizedContext(final Set<Module> modules,
            final DOMMountPoint mountPoint) {

        final Collection<Module> neededModules = new ArrayList<>(modules.size());
        final ArrayList<LeafSchemaNode> fakeRpcSchema = new ArrayList<>();

        for (final Module m : modules) {
            final Set<RpcDefinition> rpcs = m.getRpcs();
            if (!rpcs.isEmpty()) {
                neededModules.add(m);

                fakeRpcSchema.ensureCapacity(fakeRpcSchema.size() + rpcs.size());
                rpcs.forEach(rpc -> fakeRpcSchema.add(new FakeLeafSchemaNode(rpc.getQName())));
            }
        }

        final ContainerSchemaNode fakeCont = new FakeContainerSchemaNode(fakeRpcSchema);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder =
                Builders.containerBuilder(fakeCont);

        for (final LeafSchemaNode leaf : fakeRpcSchema) {
            containerBuilder.withChild(Builders.leafBuilder(leaf).build());
        }

        final Collection<Module> fakeModules = new ArrayList<>(neededModules.size() + 1);
        neededModules.forEach(imp -> fakeModules.add(new FakeImportedModule(imp)));
        fakeModules.add(new FakeRestconfModule(neededModules, fakeCont));

        final SchemaContext fakeSchemaCtx = SimpleSchemaContext.forModules(ImmutableSet.copyOf(fakeModules));
        final InstanceIdentifierContext<ContainerSchemaNode> instanceIdentifierContext =
                new InstanceIdentifierContext<>(null, fakeCont, mountPoint, fakeSchemaCtx);
        return new NormalizedNodeContext(instanceIdentifierContext, containerBuilder.build());
    }

    private Module getRestconfModule() {
        final Module restconfModule = this.controllerContext.getRestconfModule();
        if (restconfModule == null) {
            LOG.debug("ietf-restconf module was not found.");
            throw new RestconfDocumentedException("ietf-restconf module was not found.", ErrorType.APPLICATION,
                    ErrorTag.OPERATION_NOT_SUPPORTED);
        }

        return restconfModule;
    }

    private static QName getModuleNameAndRevision(final String identifier) {
        final int mountIndex = identifier.indexOf(ControllerContext.MOUNT);
        String moduleNameAndRevision = "";
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + ControllerContext.MOUNT.length());
        } else {
            moduleNameAndRevision = identifier;
        }

        final Splitter splitter = Splitter.on("/").omitEmptyStrings();
        final Iterable<String> split = splitter.split(moduleNameAndRevision);
        final List<String> pathArgs = Lists.<String>newArrayList(split);
        if (pathArgs.size() < 2) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        try {
            final String moduleName = pathArgs.get(0);
            final String revision = pathArgs.get(1);
            return QName.create(null, SimpleDateFormatUtil.getRevisionFormat().parse(revision), moduleName);
        } catch (final ParseException e) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' " + identifier);
            throw new RestconfDocumentedException("URI has bad format. It should be \'moduleName/yyyy-MM-dd\'",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    @Override
    public Object getRoot() {
        return null;
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        final SchemaPath type = payload.getInstanceIdentifierContext().getSchemaNode().getPath();
        final URI namespace = payload.getInstanceIdentifierContext().getSchemaNode().getQName().getNamespace();
        final CheckedFuture<DOMRpcResult, DOMRpcException> response;
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final SchemaContext schemaContext;

        if (mountPoint != null) {
            final Optional<DOMRpcService> mountRpcServices = mountPoint.getService(DOMRpcService.class);
            if (!mountRpcServices.isPresent()) {
                LOG.debug("Error: Rpc service is missing.");
                throw new RestconfDocumentedException("Rpc service is missing.");
            }
            schemaContext = mountPoint.getSchemaContext();
            response = mountRpcServices.get().invokeRpc(type, payload.getData());
        } else {
            if (namespace.toString().equals(SAL_REMOTE_NAMESPACE)) {
                if (identifier.contains(CREATE_DATA_SUBSCR)) {
                    response = invokeSalRemoteRpcSubscribeRPC(payload);
                } else if (identifier.contains(CREATE_NOTIFICATION_STREAM)) {
                    response = invokeSalRemoteRpcNotifiStrRPC(payload);
                } else {
                    final String msg = "Not supported operation";
                    LOG.warn(msg);
                    throw new RestconfDocumentedException(msg, ErrorType.RPC, ErrorTag.OPERATION_NOT_SUPPORTED);
                }
            } else {
                response = this.broker.invokeRpc(type, payload.getData());
            }
            schemaContext = this.controllerContext.getGlobalSchema();
        }

        final DOMRpcResult result = checkRpcResponse(response);

        RpcDefinition resultNodeSchema = null;
        final NormalizedNode<?, ?> resultData = result.getResult();
        if (result != null && result.getResult() != null) {
            resultNodeSchema = (RpcDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        }

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, resultNodeSchema, mountPoint, schemaContext),
                resultData, QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final String noPayload, final UriInfo uriInfo) {
        if (noPayload != null && !CharMatcher.WHITESPACE.matchesAllOf(noPayload)) {
            throw new RestconfDocumentedException("Content must be empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        String identifierEncoded = null;
        DOMMountPoint mountPoint = null;
        final SchemaContext schemaContext;
        if (identifier.contains(ControllerContext.MOUNT)) {
            // mounted RPC call - look up mount instance.
            final InstanceIdentifierContext<?> mountPointId = this.controllerContext.toMountPointIdentifier(identifier);
            mountPoint = mountPointId.getMountPoint();
            schemaContext = mountPoint.getSchemaContext();
            final int startOfRemoteRpcName =
                    identifier.lastIndexOf(ControllerContext.MOUNT) + ControllerContext.MOUNT.length() + 1;
            final String remoteRpcName = identifier.substring(startOfRemoteRpcName);
            identifierEncoded = remoteRpcName;

        } else if (identifier.indexOf("/") != CHAR_NOT_FOUND) {
            final String slashErrorMsg = String.format(
                    "Identifier %n%s%ncan\'t contain slash "
                            + "character (/).%nIf slash is part of identifier name then use %%2F placeholder.",
                    identifier);
            LOG.debug(slashErrorMsg);
            throw new RestconfDocumentedException(slashErrorMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        } else {
            identifierEncoded = identifier;
            schemaContext = this.controllerContext.getGlobalSchema();
        }

        final String identifierDecoded = this.controllerContext.urlPathArgDecode(identifierEncoded);

        RpcDefinition rpc = null;
        if (mountPoint == null) {
            rpc = this.controllerContext.getRpcDefinition(identifierDecoded, null);
        } else {
            rpc = findRpc(mountPoint.getSchemaContext(), identifierDecoded);
        }

        if (rpc == null) {
            LOG.debug("RPC " + identifierDecoded + " does not exist.");
            throw new RestconfDocumentedException("RPC does not exist.", ErrorType.RPC, ErrorTag.UNKNOWN_ELEMENT);
        }

        if (!rpc.getInput().getChildNodes().isEmpty()) {
            LOG.debug("RPC " + rpc + " does not need input value.");
            // FIXME : find a correct Error from specification
            throw new IllegalStateException("RPC " + rpc + " does'n need input value!");
        }

        final CheckedFuture<DOMRpcResult, DOMRpcException> response;
        if (mountPoint != null) {
            final Optional<DOMRpcService> mountRpcServices = mountPoint.getService(DOMRpcService.class);
            if (!mountRpcServices.isPresent()) {
                throw new RestconfDocumentedException("Rpc service is missing.");
            }
            response = mountRpcServices.get().invokeRpc(rpc.getPath(), null);
        } else {
            response = this.broker.invokeRpc(rpc.getPath(), null);
        }

        final DOMRpcResult result = checkRpcResponse(response);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpc, mountPoint, schemaContext),
                result.getResult(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    private static DOMRpcResult checkRpcResponse(final CheckedFuture<DOMRpcResult, DOMRpcException> response) {
        if (response == null) {
            return null;
        }
        try {
            final DOMRpcResult retValue = response.get();
            if (retValue.getErrors() == null || retValue.getErrors().isEmpty()) {
                return retValue;
            }
            LOG.debug("RpcError message", retValue.getErrors());
            throw new RestconfDocumentedException("RpcError message", null, retValue.getErrors());
        } catch (final InterruptedException e) {
            final String errMsg = "The operation was interrupted while executing and did not complete.";
            LOG.debug("Rpc Interrupt - " + errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION);
        } catch (final ExecutionException e) {
            LOG.debug("Execution RpcError: ", e);
            Throwable cause = e.getCause();
            if (cause != null) {
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }

                if (cause instanceof IllegalArgumentException) {
                    throw new RestconfDocumentedException(cause.getMessage(), ErrorType.PROTOCOL,
                            ErrorTag.INVALID_VALUE);
                } else if (cause instanceof DOMRpcImplementationNotAvailableException) {
                    throw new RestconfDocumentedException(cause.getMessage(), ErrorType.APPLICATION,
                            ErrorTag.OPERATION_NOT_SUPPORTED);
                }
                throw new RestconfDocumentedException("The operation encountered an unexpected error while executing.",
                        cause);
            } else {
                throw new RestconfDocumentedException("The operation encountered an unexpected error while executing.",
                        e);
            }
        } catch (final CancellationException e) {
            final String errMsg = "The operation was cancelled while executing.";
            LOG.debug("Cancel RpcExecution: " + errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION);
        }
    }

    private static void validateInput(final SchemaNode inputSchema, final NormalizedNodeContext payload) {
        if (inputSchema != null && payload.getData() == null) {
            // expected a non null payload
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        } else if (inputSchema == null && payload.getData() != null) {
            // did not expect any input
            throw new RestconfDocumentedException("No input expected.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
    }

    private CheckedFuture<DOMRpcResult, DOMRpcException>
            invokeSalRemoteRpcSubscribeRPC(final NormalizedNodeContext payload) {
        final ContainerNode value = (ContainerNode) payload.getData();
        final QName rpcQName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final java.util.Optional<DataContainerChild<? extends PathArgument, ?>> path = value.getChild(
            new NodeIdentifier(QName.create(payload.getInstanceIdentifierContext().getSchemaNode().getQName(),
                "path")));
        final Object pathValue = path.isPresent() ? path.get().getValue() : null;

        if (!(pathValue instanceof YangInstanceIdentifier)) {
            final String errMsg = "Instance identifier was not normalized correctly ";
            LOG.debug(errMsg + rpcQName);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }

        final YangInstanceIdentifier pathIdentifier = (YangInstanceIdentifier) pathValue;
        String streamName = (String) CREATE_DATA_SUBSCR;
        NotificationOutputType outputType = null;
        if (!pathIdentifier.isEmpty()) {
            final String fullRestconfIdentifier =
                    DATA_SUBSCR + this.controllerContext.toFullRestconfIdentifier(pathIdentifier, null);

            LogicalDatastoreType datastore =
                    parseEnumTypeParameter(value, LogicalDatastoreType.class, DATASTORE_PARAM_NAME);
            datastore = datastore == null ? DEFAULT_DATASTORE : datastore;

            DataChangeScope scope = parseEnumTypeParameter(value, DataChangeScope.class, SCOPE_PARAM_NAME);
            scope = scope == null ? DEFAULT_SCOPE : scope;

            outputType = parseEnumTypeParameter(value, NotificationOutputType.class, OUTPUT_TYPE_PARAM_NAME);
            outputType = outputType == null ? NotificationOutputType.XML : outputType;

            streamName = Notificator
                    .createStreamNameFromUri(fullRestconfIdentifier + "/datastore=" + datastore + "/scope=" + scope);
        }

        if (Strings.isNullOrEmpty(streamName)) {
            final String errMsg = "Path is empty or contains value node which is not Container or List build-in type.";
            LOG.debug(errMsg + pathIdentifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final QName outputQname = QName.create(rpcQName, "output");
        final QName streamNameQname = QName.create(rpcQName, "stream-name");

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existListenerFor(streamName)) {
            Notificator.createListener(pathIdentifier, streamName, outputType);
        }

        final DOMRpcResult defaultDOMRpcResult = new DefaultDOMRpcResult(output);

        return Futures.immediateCheckedFuture(defaultDOMRpcResult);
    }

    private static RpcDefinition findRpc(final SchemaContext schemaContext, final String identifierDecoded) {
        final String[] splittedIdentifier = identifierDecoded.split(":");
        if (splittedIdentifier.length != 2) {
            final String errMsg = identifierDecoded + " couldn't be splitted to 2 parts (module:rpc name)";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
        }
        for (final Module module : schemaContext.getModules()) {
            if (module.getName().equals(splittedIdentifier[0])) {
                for (final RpcDefinition rpcDefinition : module.getRpcs()) {
                    if (rpcDefinition.getQName().getLocalName().equals(splittedIdentifier[1])) {
                        return rpcDefinition;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public NormalizedNodeContext readConfigurationData(final String identifier, final UriInfo uriInfo) {
        boolean withDefaUsed = false;
        String withDefa = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "with-defaults":
                    if (!withDefaUsed) {
                        withDefaUsed = true;
                        withDefa = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("With-defaults parameter can be used only once.");
                    }
                    break;
                default:
                    LOG.info("Unknown key : {}.", entry.getKey());
                    break;
            }
        }
        boolean tagged = false;
        if (withDefaUsed) {
            if (withDefa.equals("report-all-tagged")) {
                tagged = true;
                withDefa = null;
            }
            if (withDefa.equals("report-all")) {
                withDefa = null;
            }
        }

        final InstanceIdentifierContext<?> iiWithData = this.controllerContext.toInstanceIdentifier(identifier);
        final DOMMountPoint mountPoint = iiWithData.getMountPoint();
        NormalizedNode<?, ?> data = null;
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();
        if (mountPoint != null) {
            data = this.broker.readConfigurationData(mountPoint, normalizedII, withDefa);
        } else {
            data = this.broker.readConfigurationData(normalizedII, withDefa);
        }
        if (data == null) {
            final String errMsg =
                    "Request could not be completed because the relevant data model content does not exist ";
            LOG.debug(errMsg + identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }
        return new NormalizedNodeContext(iiWithData, data,
                QueryParametersParser.parseWriterParameters(uriInfo, tagged));
    }

    @Override
    public NormalizedNodeContext readOperationalData(final String identifier, final UriInfo uriInfo) {
        final InstanceIdentifierContext<?> iiWithData = this.controllerContext.toInstanceIdentifier(identifier);
        final DOMMountPoint mountPoint = iiWithData.getMountPoint();
        NormalizedNode<?, ?> data = null;
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();
        if (mountPoint != null) {
            data = this.broker.readOperationalData(mountPoint, normalizedII);
        } else {
            data = this.broker.readOperationalData(normalizedII);
        }
        if (data == null) {
            final String errMsg =
                    "Request could not be completed because the relevant data model content does not exist ";
            LOG.debug(errMsg + identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
        }
        return new NormalizedNodeContext(iiWithData, data, QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    public Response updateConfigurationData(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        boolean insertUsed = false;
        boolean pointUsed = false;
        String insert = null;
        String point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "insert":
                    if (!insertUsed) {
                        insertUsed = true;
                        insert = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Insert parameter can be used only once.");
                    }
                    break;
                case "point":
                    if (!pointUsed) {
                        pointUsed = true;
                        point = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Point parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey());
            }
        }

        if (pointUsed && !insertUsed) {
            throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.");
        }
        if (pointUsed && (insert.equals("first") || insert.equals("last"))) {
            throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }

        Preconditions.checkNotNull(identifier);

        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();

        validateInput(iiWithData.getSchemaNode(), payload);
        validateTopLevelNodeName(payload, iiWithData.getInstanceIdentifier());
        validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = iiWithData.getMountPoint();
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();

        /*
         * There is a small window where another write transaction could be
         * updating the same data simultaneously and we get an
         * OptimisticLockFailedException. This error is likely transient and The
         * WriteTransaction#submit API docs state that a retry will likely
         * succeed. So we'll try again if that scenario occurs. If it fails a
         * third time then it probably will never succeed so we'll fail in that
         * case.
         *
         * By retrying we're attempting to hide the internal implementation of
         * the data store and how it handles concurrent updates from the
         * restconf client. The client has instructed us to put the data and we
         * should make every effort to do so without pushing optimistic lock
         * failures back to the client and forcing them to handle it via retry
         * (and having to document the behavior).
         */
        PutResult result = null;
        int tries = 2;
        while (true) {
            if (mountPoint != null) {

                result = this.broker.commitMountPointDataPut(mountPoint, normalizedII, payload.getData(), insert,
                        point);
            } else {
                result = this.broker.commitConfigurationDataPut(this.controllerContext.getGlobalSchema(), normalizedII,
                        payload.getData(), insert, point);
            }

            try {
                result.getFutureOfPutData().checkedGet();
                return Response.status(result.getStatus()).build();
            } catch (final TransactionCommitFailedException e) {
                if (e instanceof OptimisticLockFailedException) {
                    if (--tries <= 0) {
                        LOG.debug("Got OptimisticLockFailedException on last try - failing " + identifier);
                        throw new RestconfDocumentedException(e.getMessage(), e, e.getErrorList());
                    }

                    LOG.debug("Got OptimisticLockFailedException - trying again " + identifier);
                } else {
                    LOG.debug("Update failed for " + identifier, e);
                    throw new RestconfDocumentedException(e.getMessage(), e, e.getErrorList());
                }
            }
        }
    }

    private static void validateTopLevelNodeName(final NormalizedNodeContext node,
            final YangInstanceIdentifier identifier) {

        final String payloadName = node.getData().getNodeType().getLocalName();

        // no arguments
        if (identifier.isEmpty()) {
            // no "data" payload
            if (!node.getData().getNodeType().equals(NETCONF_BASE_QNAME)) {
                throw new RestconfDocumentedException("Instance identifier has to contain at least one path argument",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
            // any arguments
        } else {
            final String identifierName = identifier.getLastPathArgument().getNodeType().getLocalName();
            if (!payloadName.equals(identifierName)) {
                throw new RestconfDocumentedException(
                        "Payload name (" + payloadName + ") is different from identifier name (" + identifierName + ")",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        }
    }

    /**
     * Validates whether keys in {@code payload} are equal to values of keys in
     * {@code iiWithData} for list schema node.
     *
     * @throws RestconfDocumentedException
     *             if key values or key count in payload and URI isn't equal
     *
     */
    private static void validateListKeysEqualityInPayloadAndUri(final NormalizedNodeContext payload) {
        Preconditions.checkArgument(payload != null);
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode<?, ?> data = payload.getData();
        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> keyDefinitions = ((ListSchemaNode) schemaNode).getKeyDefinition();
            if (lastPathArgument instanceof NodeIdentifierWithPredicates && data instanceof MapEntryNode) {
                final Map<QName, Object> uriKeyValues =
                        ((NodeIdentifierWithPredicates) lastPathArgument).getKeyValues();
                isEqualUriAndPayloadKeyValues(uriKeyValues, (MapEntryNode) data, keyDefinitions);
            }
        }
    }

    @VisibleForTesting
    public static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
            final List<QName> keyDefinitions) {

        final Map<QName, Object> mutableCopyUriKeyValues = Maps.newHashMap(uriKeyValues);
        for (final QName keyDefinition : keyDefinitions) {
            final Object uriKeyValue = mutableCopyUriKeyValues.remove(keyDefinition);
            // should be caught during parsing URI to InstanceIdentifier
            RestconfValidationUtils.checkDocumentedError(uriKeyValue != null, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    "Missing key " + keyDefinition + " in URI.");

            final Object dataKeyValue = payload.getIdentifier().getKeyValues().get(keyDefinition);

            if (!Objects.deepEquals(uriKeyValue, dataKeyValue)) {
                final String errMsg = "The value '" + uriKeyValue + "' for key '" + keyDefinition.getLocalName()
                        + "' specified in the URI doesn't match the value '" + dataKeyValue
                        + "' specified in the message body. ";
                throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }

    @Override
    public Response createConfigurationData(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        return createConfigurationData(payload, uriInfo);
    }

    @Override
    public Response createConfigurationData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        if (payload == null) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();

        boolean insertUsed = false;
        boolean pointUsed = false;
        String insert = null;
        String point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "insert":
                    if (!insertUsed) {
                        insertUsed = true;
                        insert = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Insert parameter can be used only once.");
                    }
                    break;
                case "point":
                    if (!pointUsed) {
                        pointUsed = true;
                        point = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Point parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey());
            }
        }

        if (pointUsed && !insertUsed) {
            throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.");
        }
        if (pointUsed && (insert.equals("first") || insert.equals("last"))) {
            throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }

        CheckedFuture<Void, TransactionCommitFailedException> future;
        if (mountPoint != null) {
            future = this.broker.commitConfigurationDataPost(mountPoint, normalizedII, payload.getData(), insert,
                    point);
        } else {
            future = this.broker.commitConfigurationDataPost(this.controllerContext.getGlobalSchema(), normalizedII,
                    payload.getData(), insert, point);
        }

        try {
            future.checkedGet();
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final TransactionCommitFailedException e) {
            LOG.info("Error creating data " + (uriInfo != null ? uriInfo.getPath() : ""), e);
            throw new RestconfDocumentedException(e.getMessage(), e, e.getErrorList());
        }

        LOG.trace("Successfuly created data.");

        final ResponseBuilder responseBuilder = Response.status(Status.NO_CONTENT);
        // FIXME: Provide path to result.
        final URI location = resolveLocation(uriInfo, "", mountPoint, normalizedII);
        if (location != null) {
            responseBuilder.location(location);
        }
        return responseBuilder.build();
    }

    // FIXME create RestconfIdetifierHelper and move this method there
    private static YangInstanceIdentifier checkConsistencyOfNormalizedNodeContext(final NormalizedNodeContext payload) {
        Preconditions.checkArgument(payload != null);
        Preconditions.checkArgument(payload.getData() != null);
        Preconditions.checkArgument(payload.getData().getNodeType() != null);
        Preconditions.checkArgument(payload.getInstanceIdentifierContext() != null);
        Preconditions.checkArgument(payload.getInstanceIdentifierContext().getInstanceIdentifier() != null);

        final QName payloadNodeQname = payload.getData().getNodeType();
        final YangInstanceIdentifier yangIdent = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        if (payloadNodeQname.compareTo(yangIdent.getLastPathArgument().getNodeType()) > 0) {
            return yangIdent;
        }
        final InstanceIdentifierContext<?> parentContext = payload.getInstanceIdentifierContext();
        final SchemaNode parentSchemaNode = parentContext.getSchemaNode();
        if (parentSchemaNode instanceof DataNodeContainer) {
            final DataNodeContainer cast = (DataNodeContainer) parentSchemaNode;
            for (final DataSchemaNode child : cast.getChildNodes()) {
                if (payloadNodeQname.compareTo(child.getQName()) == 0) {
                    return YangInstanceIdentifier.builder(yangIdent).node(child.getQName()).build();
                }
            }
        }
        if (parentSchemaNode instanceof RpcDefinition) {
            return yangIdent;
        }
        final String errMsg = "Error parsing input: DataSchemaNode has not children ";
        LOG.info(errMsg + yangIdent);
        throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private URI resolveLocation(final UriInfo uriInfo, final String uriBehindBase, final DOMMountPoint mountPoint,
            final YangInstanceIdentifier normalizedII) {
        if (uriInfo == null) {
            // This is null if invoked internally
            return null;
        }

        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        uriBuilder.path("config");
        try {
            uriBuilder.path(this.controllerContext.toFullRestconfIdentifier(normalizedII, mountPoint));
        } catch (final Exception e) {
            LOG.info("Location for instance identifier" + normalizedII + "wasn't created", e);
            return null;
        }
        return uriBuilder.build();
    }

    @Override
    public Response deleteConfigurationData(final String identifier) {
        final InstanceIdentifierContext<?> iiWithData = this.controllerContext.toInstanceIdentifier(identifier);
        final DOMMountPoint mountPoint = iiWithData.getMountPoint();
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();

        final CheckedFuture<Void, TransactionCommitFailedException> future;
        if (mountPoint != null) {
            future = this.broker.commitConfigurationDataDelete(mountPoint, normalizedII);
        } else {
            future = this.broker.commitConfigurationDataDelete(normalizedII);
        }

        try {
            future.checkedGet();
        } catch (final TransactionCommitFailedException e) {
            final Optional<Throwable> searchedException = Iterables.tryFind(Throwables.getCausalChain(e),
                    Predicates.instanceOf(ModifiedNodeDoesNotExistException.class));
            if (searchedException.isPresent()) {
                throw new RestconfDocumentedException("Data specified for delete doesn't exist.", ErrorType.APPLICATION,
                        ErrorTag.DATA_MISSING);
            }

            final String errMsg = "Error while deleting data";
            LOG.info(errMsg, e);
            throw new RestconfDocumentedException(e.getMessage(), e, e.getErrorList());
        }

        return Response.status(Status.OK).build();
    }

    /**
     * Subscribes to some path in schema context (stream) to listen on changes
     * on this stream.
     *
     * <p>
     * Additional parameters for subscribing to stream are loaded via rpc input
     * parameters:
     * <ul>
     * <li>datastore - default CONFIGURATION (other values of
     * {@link LogicalDatastoreType} enum type)</li>
     * <li>scope - default BASE (other values of {@link DataChangeScope})</li>
     * </ul>
     */
    @Override
    public NormalizedNodeContext subscribeToStream(final String identifier, final UriInfo uriInfo) {
        boolean startTimeUsed = false;
        boolean stopTimeUsed = false;
        Instant start = Instant.now();
        Instant stop = null;
        boolean filterUsed = false;
        String filter = null;
        boolean leafNodesOnlyUsed = false;
        boolean leafNodesOnly = false;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "start-time":
                    if (!startTimeUsed) {
                        startTimeUsed = true;
                        start = parseDateFromQueryParam(entry);
                    } else {
                        throw new RestconfDocumentedException("Start-time parameter can be used only once.");
                    }
                    break;
                case "stop-time":
                    if (!stopTimeUsed) {
                        stopTimeUsed = true;
                        stop = parseDateFromQueryParam(entry);
                    } else {
                        throw new RestconfDocumentedException("Stop-time parameter can be used only once.");
                    }
                    break;
                case "filter":
                    if (!filterUsed) {
                        filterUsed = true;
                        filter = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Filter parameter can be used only once.");
                    }
                    break;
                case "odl-leaf-nodes-only":
                    if (!leafNodesOnlyUsed) {
                        leafNodesOnlyUsed = true;
                        leafNodesOnly = Boolean.parseBoolean(entry.getValue().iterator().next());
                    } else {
                        throw new RestconfDocumentedException("Odl-leaf-nodes-only parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter used with notifications: " + entry.getKey());
            }
        }
        if (!startTimeUsed && stopTimeUsed) {
            throw new RestconfDocumentedException("Stop-time parameter has to be used with start-time parameter.");
        }
        URI response = null;
        if (identifier.contains(DATA_SUBSCR)) {
            response = dataSubs(identifier, uriInfo, start, stop, filter, leafNodesOnly);
        } else if (identifier.contains(NOTIFICATION_STREAM)) {
            response = notifStream(identifier, uriInfo, start, stop, filter);
        }

        if (response != null) {
            // prepare node with value of location
            final InstanceIdentifierContext<?> iid = prepareIIDSubsStreamOutput();
            final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> builder =
                    ImmutableLeafNodeBuilder.create().withValue(response.toString());
            builder.withNodeIdentifier(
                    NodeIdentifier.create(QName.create("subscribe:to:notification", "2016-10-28", "location")));

            // prepare new header with location
            final Map<String, Object> headers = new HashMap<>();
            headers.put("Location", response);

            return new NormalizedNodeContext(iid, builder.build(), headers);
        }

        final String msg = "Bad type of notification of sal-remote";
        LOG.warn(msg);
        throw new RestconfDocumentedException(msg);
    }

    private static Instant parseDateFromQueryParam(final Entry<String, List<String>> entry) {
        final DateAndTime event = new DateAndTime(entry.getValue().iterator().next());
        final String value = event.getValue();
        final TemporalAccessor p;
        try {
            p = FORMATTER.parse(value);
        } catch (final DateTimeParseException e) {
            throw new RestconfDocumentedException("Cannot parse of value in date: " + value, e);
        }
        return Instant.from(p);
    }

    /**
     * Prepare instance identifier.
     *
     * @return {@link InstanceIdentifierContext} of location leaf for
     *         notification
     */
    private static InstanceIdentifierContext<?> prepareIIDSubsStreamOutput() {
        final QName qnameBase = QName.create("subscribe:to:notification", "2016-10-28", "notifi");
        final SchemaContext schemaCtx = ControllerContext.getInstance().getGlobalSchema();
        final DataSchemaNode location = ((ContainerSchemaNode) schemaCtx
                .findModuleByNamespaceAndRevision(qnameBase.getNamespace(), qnameBase.getRevision())
                .getDataChildByName(qnameBase)).getDataChildByName(QName.create(qnameBase, "location"));
        final List<PathArgument> path = new ArrayList<>();
        path.add(NodeIdentifier.create(qnameBase));
        path.add(NodeIdentifier.create(QName.create(qnameBase, "location")));

        return new InstanceIdentifierContext<SchemaNode>(YangInstanceIdentifier.create(path), location, null,
                schemaCtx);
    }

    /**
     * Register notification listener by stream name.
     *
     * @param identifier
     *            stream name
     * @param uriInfo
     *            uriInfo
     * @param stop
     *            stop-time of getting notification
     * @param start
     *            start-time of getting notification
     * @param filter
     *            indicate which subset of all possible events are of interest
     * @return {@link URI} of location
     */
    private URI notifStream(final String identifier, final UriInfo uriInfo, final Instant start,
            final Instant stop, final String filter) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        final List<NotificationListenerAdapter> listeners = Notificator.getNotificationListenerFor(streamName);
        if (listeners == null || listeners.isEmpty()) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }

        for (final NotificationListenerAdapter listener : listeners) {
            this.broker.registerToListenNotification(listener);
            listener.setQueryParams(start,
                    java.util.Optional.ofNullable(stop), java.util.Optional.ofNullable(filter), false);
        }

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();

        final WebSocketServer webSocketServerInstance = WebSocketServer.getInstance(NOTIFICATION_PORT);
        final int notificationPort = webSocketServerInstance.getPort();

        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme("ws");

        return uriToWebsocketServerBuilder.replacePath(streamName).build();
    }

    /**
     * Register data change listener by stream name.
     *
     * @param identifier
     *            stream name
     * @param uriInfo
     *            uri info
     * @param stop
     *            start-time of getting notification
     * @param start
     *            stop-time of getting notification
     * @param filter
     *            indicate which subset of all possible events are of interest
     * @return {@link URI} of location
     */
    private URI dataSubs(final String identifier, final UriInfo uriInfo, final Instant start, final Instant stop,
            final String filter, final boolean leafNodesOnly) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final ListenerAdapter listener = Notificator.getListenerFor(streamName);
        if (listener == null) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }
        listener.setQueryParams(start, java.util.Optional.ofNullable(stop),
                java.util.Optional.ofNullable(filter), leafNodesOnly);

        final Map<String, String> paramToValues = resolveValuesFromUri(identifier);
        final LogicalDatastoreType datastore =
                parserURIEnumParameter(LogicalDatastoreType.class, paramToValues.get(DATASTORE_PARAM_NAME));
        if (datastore == null) {
            throw new RestconfDocumentedException("Stream name doesn't contains datastore value (pattern /datastore=)",
                    ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }
        final DataChangeScope scope =
                parserURIEnumParameter(DataChangeScope.class, paramToValues.get(SCOPE_PARAM_NAME));
        if (scope == null) {
            throw new RestconfDocumentedException("Stream name doesn't contains datastore value (pattern /scope=)",
                    ErrorType.APPLICATION, ErrorTag.MISSING_ATTRIBUTE);
        }

        this.broker.registerToListenDataChanges(datastore, scope, listener);

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();

        final WebSocketServer webSocketServerInstance = WebSocketServer.getInstance(NOTIFICATION_PORT);
        final int notificationPort = webSocketServerInstance.getPort();

        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme("ws");

        return uriToWebsocketServerBuilder.replacePath(streamName).build();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public PatchStatusContext patchConfigurationData(final String identifier, final PatchContext context,
                                                     final UriInfo uriInfo) {
        if (context == null) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        try {
            return this.broker.patchConfigurationDataWithinTransaction(context);
        } catch (final Exception e) {
            LOG.debug("Patch transaction failed", e);
            throw new RestconfDocumentedException(e.getMessage());
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public PatchStatusContext patchConfigurationData(final PatchContext context, @Context final UriInfo uriInfo) {
        if (context == null) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        try {
            return this.broker.patchConfigurationDataWithinTransaction(context);
        } catch (final Exception e) {
            LOG.debug("Patch transaction failed", e);
            throw new RestconfDocumentedException(e.getMessage());
        }
    }

    /**
     * Load parameter for subscribing to stream from input composite node.
     *
     * @param value
     *            contains value
     * @return enum object if its string value is equal to {@code paramName}. In
     *         other cases null.
     */
    private static <T> T parseEnumTypeParameter(final ContainerNode value, final Class<T> classDescriptor,
            final String paramName) {
        final java.util.Optional<DataContainerChild<? extends PathArgument, ?>> optAugNode = value.getChild(
            SAL_REMOTE_AUG_IDENTIFIER);
        if (!optAugNode.isPresent()) {
            return null;
        }
        final DataContainerChild<? extends PathArgument, ?> augNode = optAugNode.get();
        if (!(augNode instanceof AugmentationNode)) {
            return null;
        }
        final java.util.Optional<DataContainerChild<? extends PathArgument, ?>> enumNode = ((AugmentationNode) augNode)
            .getChild(new NodeIdentifier(QName.create(SAL_REMOTE_AUGMENT, paramName)));
        if (!enumNode.isPresent()) {
            return null;
        }
        final Object rawValue = enumNode.get().getValue();
        if (!(rawValue instanceof String)) {
            return null;
        }

        return resolveAsEnum(classDescriptor, (String) rawValue);
    }

    /**
     * Checks whether {@code value} is one of the string representation of
     * enumeration {@code classDescriptor}.
     *
     * @return enum object if string value of {@code classDescriptor}
     *         enumeration is equal to {@code value}. Other cases null.
     */
    private static <T> T parserURIEnumParameter(final Class<T> classDescriptor, final String value) {
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }
        return resolveAsEnum(classDescriptor, value);
    }

    private static <T> T resolveAsEnum(final Class<T> classDescriptor, final String value) {
        final T[] enumConstants = classDescriptor.getEnumConstants();
        if (enumConstants != null) {
            for (final T enm : classDescriptor.getEnumConstants()) {
                if (((Enum<?>) enm).name().equals(value)) {
                    return enm;
                }
            }
        }
        return null;
    }

    private static Map<String, String> resolveValuesFromUri(final String uri) {
        final Map<String, String> result = new HashMap<>();
        final String[] tokens = uri.split("/");
        for (int i = 1; i < tokens.length; i++) {
            final String[] parameterTokens = tokens[i].split("=");
            if (parameterTokens.length == 2) {
                result.put(parameterTokens[0], parameterTokens[1]);
            }
        }
        return result;
    }

    private MapNode makeModuleMapNode(final Set<Module> modules) {
        Preconditions.checkNotNull(modules);
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode moduleSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(moduleSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listModuleBuilder =
                Builders.mapBuilder((ListSchemaNode) moduleSchemaNode);

        for (final Module module : modules) {
            listModuleBuilder.withChild(toModuleEntryNode(module, moduleSchemaNode));
        }
        return listModuleBuilder.build();
    }

    protected MapEntryNode toModuleEntryNode(final Module module, final DataSchemaNode moduleSchemaNode) {
        Preconditions.checkArgument(moduleSchemaNode instanceof ListSchemaNode,
                "moduleSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listModuleSchemaNode = (ListSchemaNode) moduleSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> moduleNodeValues =
                Builders.mapEntryBuilder(listModuleSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(nameSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(module.getName()).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "revision");
        final DataSchemaNode revisionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(revisionSchemaNode instanceof LeafSchemaNode);
        final String revision = module.getQNameModule().getFormattedRevision();
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) revisionSchemaNode).withValue(revision).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "namespace");
        final DataSchemaNode namespaceSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(namespaceSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) namespaceSchemaNode)
                .withValue(module.getNamespace().toString()).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "feature");
        final DataSchemaNode featureSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(featureSchemaNode instanceof LeafListSchemaNode);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> featuresBuilder =
                Builders.leafSetBuilder((LeafListSchemaNode) featureSchemaNode);
        for (final FeatureDefinition feature : module.getFeatures()) {
            featuresBuilder.withChild(Builders.leafSetEntryBuilder((LeafListSchemaNode) featureSchemaNode)
                    .withValue(feature.getQName().getLocalName()).build());
        }
        moduleNodeValues.withChild(featuresBuilder.build());

        return moduleNodeValues.build();
    }

    protected MapEntryNode toStreamEntryNode(final String streamName, final DataSchemaNode streamSchemaNode) {
        Preconditions.checkArgument(streamSchemaNode instanceof ListSchemaNode,
                "streamSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) streamSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues =
                Builders.mapEntryBuilder(listStreamSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(nameSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(streamName).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "description");
        final DataSchemaNode descriptionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(descriptionSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue("DESCRIPTION_PLACEHOLDER").build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-support");
        final DataSchemaNode replaySupportSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(replaySupportSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) replaySupportSchemaNode)
                .withValue(Boolean.valueOf(true)).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-log-creation-time");
        final DataSchemaNode replayLogCreationTimeSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(replayLogCreationTimeSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) replayLogCreationTimeSchemaNode).withValue("").build());

        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "events");
        final DataSchemaNode eventsSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(eventsSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) eventsSchemaNode).build());

        return streamNodeValues.build();
    }

    /**
     * Prepare stream for notification.
     *
     * @param payload
     *            contains list of qnames of notifications
     * @return - checked future object
     */
    private static CheckedFuture<DOMRpcResult, DOMRpcException> invokeSalRemoteRpcNotifiStrRPC(
            final NormalizedNodeContext payload) {
        final ContainerNode data = (ContainerNode) payload.getData();
        LeafSetNode leafSet = null;
        String outputType = "XML";
        for (final DataContainerChild<? extends PathArgument, ?> dataChild : data.getValue()) {
            if (dataChild instanceof LeafSetNode) {
                leafSet = (LeafSetNode) dataChild;
            } else if (dataChild instanceof AugmentationNode) {
                outputType = (String) ((AugmentationNode) dataChild).getValue().iterator().next().getValue();
            }
        }

        final Collection<LeafSetEntryNode> entryNodes = leafSet.getValue();
        final List<SchemaPath> paths = new ArrayList<>();
        String streamName = CREATE_NOTIFICATION_STREAM + "/";

        final Iterator<LeafSetEntryNode> iterator = entryNodes.iterator();
        while (iterator.hasNext()) {
            final QName valueQName = QName.create((String) iterator.next().getValue());
            final Module module =
                    ControllerContext.getInstance().findModuleByNamespace(valueQName.getModule().getNamespace());
            Preconditions.checkNotNull(module,
                    "Module for namespace " + valueQName.getModule().getNamespace() + " does not exist");
            NotificationDefinition notifiDef = null;
            for (final NotificationDefinition notification : module.getNotifications()) {
                if (notification.getQName().equals(valueQName)) {
                    notifiDef = notification;
                    break;
                }
            }
            final String moduleName = module.getName();
            Preconditions.checkNotNull(notifiDef,
                    "Notification " + valueQName + "doesn't exist in module " + moduleName);
            paths.add(notifiDef.getPath());
            streamName = streamName + moduleName + ":" + valueQName.getLocalName();
            if (iterator.hasNext()) {
                streamName = streamName + ",";
            }
        }

        final QName rpcQName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final QName outputQname = QName.create(rpcQName, "output");
        final QName streamNameQname = QName.create(rpcQName, "notification-stream-identifier");

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existNotificationListenerFor(streamName)) {
            Notificator.createNotificationListener(paths, streamName, outputType);
        }

        final DOMRpcResult defaultDOMRpcResult = new DefaultDOMRpcResult(output);

        return Futures.immediateCheckedFuture(defaultDOMRpcResult);
    }
}
