/*
 * Copyright (c) 2014 - 2016 Brocade Communication Systems, Inc., Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.OptimisticLockFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
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
import org.opendaylight.restconf.common.util.DataChangeScope;
import org.opendaylight.restconf.common.util.OperationsResourceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.DateAndTime;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
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
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class RestconfImpl implements RestconfService {
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

    private static final QName NETCONF_BASE_QNAME = QName.create(QNameModule.create(URI.create(NETCONF_BASE)),
        NETCONF_BASE_PAYLOAD_NAME).intern();

    private static final QNameModule SAL_REMOTE_AUGMENT = QNameModule.create(NAMESPACE_EVENT_SUBSCRIPTION_AUGMENT,
        Revision.of("2014-07-08"));

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

    private final BrokerFacade broker;

    private final ControllerContext controllerContext;

    @Inject
    public RestconfImpl(final BrokerFacade broker, final ControllerContext controllerContext) {
        this.broker = broker;
        this.controllerContext = controllerContext;
    }

    /**
     * Factory method.
     *
     * @deprecated Just use {@link #RestconfImpl(BrokerFacade, ControllerContext)} constructor instead.
     */
    @Deprecated
    public static RestconfImpl newInstance(final BrokerFacade broker, final ControllerContext controllerContext) {
        return new RestconfImpl(broker, controllerContext);
    }

    @Override
    @Deprecated
    public NormalizedNodeContext getModules(final UriInfo uriInfo) {
        final MapNode allModuleMap = makeModuleMapNode(controllerContext.getAllModules());

        final EffectiveModelContext schemaContext = this.controllerContext.getGlobalSchema();

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(allModuleMap);

        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, modulesSchemaNode, null, schemaContext),
                moduleContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    /**
     * Valid only for mount point.
     */
    @Override
    @Deprecated
    public NormalizedNodeContext getModules(final String identifier, final UriInfo uriInfo) {
        if (!identifier.contains(ControllerContext.MOUNT)) {
            final String errMsg = "URI has bad format. If modules behind mount point should be showed,"
                    + " URI has to end with " + ControllerContext.MOUNT;
            LOG.debug("{} for {}", errMsg, identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext<?> mountPointIdentifier =
                this.controllerContext.toMountPointIdentifier(identifier);
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        final MapNode mountPointModulesMap = makeModuleMapNode(controllerContext.getAllModules(mountPoint));

        final Module restconfModule = getRestconfModule();
        final DataSchemaNode modulesSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE);
        checkState(modulesSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> moduleContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) modulesSchemaNode);
        moduleContainerBuilder.withChild(mountPointModulesMap);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, modulesSchemaNode, mountPoint,
                        this.controllerContext.getGlobalSchema()),
                moduleContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    @Deprecated
    public NormalizedNodeContext getModule(final String identifier, final UriInfo uriInfo) {
        final Entry<String, Revision> nameRev = getModuleNameAndRevision(requireNonNull(identifier));
        Module module = null;
        DOMMountPoint mountPoint = null;
        final EffectiveModelContext schemaContext;
        if (identifier.contains(ControllerContext.MOUNT)) {
            final InstanceIdentifierContext<?> mountPointIdentifier =
                    this.controllerContext.toMountPointIdentifier(identifier);
            mountPoint = mountPointIdentifier.getMountPoint();
            module = this.controllerContext.findModuleByNameAndRevision(mountPoint, nameRev.getKey(),
                nameRev.getValue());
            schemaContext = modelContext(mountPoint);
        } else {
            module = this.controllerContext.findModuleByNameAndRevision(nameRev.getKey(), nameRev.getValue());
            schemaContext = this.controllerContext.getGlobalSchema();
        }

        if (module == null) {
            LOG.debug("Module with name '{}' and revision '{}' was not found.", nameRev.getKey(), nameRev.getValue());
            throw new RestconfDocumentedException("Module with name '" + nameRev.getKey() + "' and revision '"
                    + nameRev.getValue() + "' was not found.", ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
        }

        final Module restconfModule = getRestconfModule();
        final Set<Module> modules = Collections.singleton(module);
        final MapNode moduleMap = makeModuleMapNode(modules);

        final DataSchemaNode moduleSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        checkState(moduleSchemaNode instanceof ListSchemaNode);

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, moduleSchemaNode, mountPoint, schemaContext), moduleMap,
                QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    @Deprecated
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        final EffectiveModelContext schemaContext = this.controllerContext.getGlobalSchema();
        final Set<String> availableStreams = Notificator.getStreamNames();
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode streamSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.STREAM_LIST_SCHEMA_NODE);
        checkState(streamSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listStreamsBuilder =
                Builders.mapBuilder((ListSchemaNode) streamSchemaNode);

        for (final String streamName : availableStreams) {
            listStreamsBuilder.withChild(toStreamEntryNode(streamName, streamSchemaNode));
        }

        final DataSchemaNode streamsContainerSchemaNode = this.controllerContext.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft02.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
        checkState(streamsContainerSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> streamsContainerBuilder =
                Builders.containerBuilder((ContainerSchemaNode) streamsContainerSchemaNode);
        streamsContainerBuilder.withChild(listStreamsBuilder.build());

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, streamsContainerSchemaNode, null, schemaContext),
                streamsContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    @Override
    @Deprecated
    public NormalizedNodeContext getOperations(final UriInfo uriInfo) {
        return OperationsResourceUtils.contextForModelContext(controllerContext.getGlobalSchema(), null);
    }

    @Override
    @Deprecated
    public NormalizedNodeContext getOperations(final String identifier, final UriInfo uriInfo) {
        if (!identifier.contains(ControllerContext.MOUNT)) {
            final String errMsg = "URI has bad format. If operations behind mount point should be showed, URI has to "
                    + " end with " +  ControllerContext.MOUNT;
            LOG.debug("{} for {}", errMsg, identifier);
            throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final InstanceIdentifierContext<?> mountPointIdentifier =
                this.controllerContext.toMountPointIdentifier(identifier);
        final DOMMountPoint mountPoint = mountPointIdentifier.getMountPoint();
        return OperationsResourceUtils.contextForModelContext(modelContext(mountPoint), mountPoint);
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

    private static Entry<String, Revision> getModuleNameAndRevision(final String identifier) {
        final int mountIndex = identifier.indexOf(ControllerContext.MOUNT);
        String moduleNameAndRevision = "";
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + ControllerContext.MOUNT.length());
        } else {
            moduleNameAndRevision = identifier;
        }

        final Splitter splitter = Splitter.on('/').omitEmptyStrings();
        final List<String> pathArgs = splitter.splitToList(moduleNameAndRevision);
        if (pathArgs.size() < 2) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' {}", identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        try {
            return new SimpleImmutableEntry<>(pathArgs.get(0), Revision.of(pathArgs.get(1)));
        } catch (final DateTimeParseException e) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' {}", identifier);
            throw new RestconfDocumentedException("URI has bad format. It should be \'moduleName/yyyy-MM-dd\'",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    @Override
    public Object getRoot() {
        return null;
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        if (payload == null) {
            // no payload specified, reroute this to no payload invokeRpc implementation
            return invokeRpc(identifier, uriInfo);
        }

        final SchemaNode schema = payload.getInstanceIdentifierContext().getSchemaNode();
        final ListenableFuture<? extends DOMRpcResult> response;
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final NormalizedNode<?, ?> input =  nonnullInput(schema, payload.getData());
        final EffectiveModelContext schemaContext;

        if (mountPoint != null) {
            final Optional<DOMRpcService> mountRpcServices = mountPoint.getService(DOMRpcService.class);
            if (mountRpcServices.isEmpty()) {
                LOG.debug("Error: Rpc service is missing.");
                throw new RestconfDocumentedException("Rpc service is missing.");
            }
            schemaContext = modelContext(mountPoint);
            response = mountRpcServices.get().invokeRpc(schema.getQName(), input);
        } else {
            final URI namespace = schema.getQName().getNamespace();
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
                response = this.broker.invokeRpc(schema.getQName(), input);
            }
            schemaContext = this.controllerContext.getGlobalSchema();
        }

        final DOMRpcResult result = checkRpcResponse(response);

        RpcDefinition resultNodeSchema = null;
        final NormalizedNode<?, ?> resultData;
        if (result != null && result.getResult() != null) {
            resultData = result.getResult();
            resultNodeSchema = (RpcDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        } else {
            resultData = null;
        }

        if (resultData != null && ((ContainerNode) resultData).getValue().isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        } else {
            return new NormalizedNodeContext(
                    new InstanceIdentifierContext<>(null, resultNodeSchema, mountPoint, schemaContext),
                    resultData, QueryParametersParser.parseWriterParameters(uriInfo));
        }
    }

    @SuppressFBWarnings(value = "NP_LOAD_OF_KNOWN_NULL_VALUE",
            justification = "Looks like a false positive, see below FIXME")
    private NormalizedNodeContext invokeRpc(final String identifier, final UriInfo uriInfo) {
        final DOMMountPoint mountPoint;
        final String identifierEncoded;
        final EffectiveModelContext schemaContext;
        if (identifier.contains(ControllerContext.MOUNT)) {
            // mounted RPC call - look up mount instance.
            final InstanceIdentifierContext<?> mountPointId = this.controllerContext.toMountPointIdentifier(identifier);
            mountPoint = mountPointId.getMountPoint();
            schemaContext = modelContext(mountPoint);
            final int startOfRemoteRpcName =
                    identifier.lastIndexOf(ControllerContext.MOUNT) + ControllerContext.MOUNT.length() + 1;
            final String remoteRpcName = identifier.substring(startOfRemoteRpcName);
            identifierEncoded = remoteRpcName;

        } else if (identifier.indexOf('/') == CHAR_NOT_FOUND) {
            identifierEncoded = identifier;
            mountPoint = null;
            schemaContext = this.controllerContext.getGlobalSchema();
        } else {
            LOG.debug("Identifier {} cannot contain slash character (/).", identifier);
            throw new RestconfDocumentedException(String.format("Identifier %n%s%ncan\'t contain slash character (/).%n"
                    + "If slash is part of identifier name then use %%2F placeholder.", identifier), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE);
        }

        final String identifierDecoded = ControllerContext.urlPathArgDecode(identifierEncoded);
        final RpcDefinition rpc;
        if (mountPoint == null) {
            rpc = this.controllerContext.getRpcDefinition(identifierDecoded);
        } else {
            rpc = findRpc(modelContext(mountPoint), identifierDecoded);
        }

        if (rpc == null) {
            LOG.debug("RPC {} does not exist.", identifierDecoded);
            throw new RestconfDocumentedException("RPC does not exist.", ErrorType.RPC, ErrorTag.UNKNOWN_ELEMENT);
        }

        if (!rpc.getInput().getChildNodes().isEmpty()) {
            LOG.debug("No input specified for RPC {} with an input section", rpc);
            throw new RestconfDocumentedException("No input specified for RPC " + rpc
                    + " with an input section defined", ErrorType.RPC, ErrorTag.MISSING_ELEMENT);
        }

        final ContainerNode input = defaultInput(rpc.getQName());
        final ListenableFuture<? extends DOMRpcResult> response;
        if (mountPoint != null) {
            final Optional<DOMRpcService> mountRpcServices = mountPoint.getService(DOMRpcService.class);
            if (mountRpcServices.isEmpty()) {
                throw new RestconfDocumentedException("Rpc service is missing.");
            }
            response = mountRpcServices.get().invokeRpc(rpc.getQName(), input);
        } else {
            response = this.broker.invokeRpc(rpc.getQName(), input);
        }

        final NormalizedNode<?, ?> result = checkRpcResponse(response).getResult();
        if (result != null && ((ContainerNode) result).getValue().isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }

        // FIXME: in reference to the above @SupressFBWarnings: "mountPoint" reference here trips up SpotBugs, as it
        //        thinks it can only ever be null. Except it can very much be non-null. The core problem is the horrible
        //        structure of this code where we have a sh*tload of checks for mountpoint above and all over the
        //        codebase where all that difference should have been properly encapsulated.
        //
        //        This is legacy code, so if anybody cares to do that refactor, feel free to contribute, but I am not
        //        doing that work.
        return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, rpc, mountPoint, schemaContext), result,
            QueryParametersParser.parseWriterParameters(uriInfo));
    }

    private static @NonNull NormalizedNode<?, ?> nonnullInput(final SchemaNode rpc, final NormalizedNode<?, ?> input) {
        return input != null ? input : defaultInput(rpc.getQName());
    }

    private static @NonNull ContainerNode defaultInput(final QName rpcName) {
        return ImmutableNodes.containerNode(YangConstants.operationInputQName(rpcName.getModule()));
    }

    @SuppressWarnings("checkstyle:avoidHidingCauseException")
    private static DOMRpcResult checkRpcResponse(final ListenableFuture<? extends DOMRpcResult> response) {
        if (response == null) {
            return null;
        }
        try {
            final DOMRpcResult retValue = response.get();
            if (retValue.getErrors().isEmpty()) {
                return retValue;
            }
            LOG.debug("RpcError message {}", retValue.getErrors());
            throw new RestconfDocumentedException("RpcError message", null, retValue.getErrors());
        } catch (final InterruptedException e) {
            final String errMsg = "The operation was interrupted while executing and did not complete.";
            LOG.debug("Rpc Interrupt - {}", errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, e);
        } catch (final ExecutionException e) {
            LOG.debug("Execution RpcError: ", e);
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new RestconfDocumentedException("The operation encountered an unexpected error while executing.",
                    e);
            }
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            if (cause instanceof IllegalArgumentException) {
                throw new RestconfDocumentedException(cause.getMessage(), ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            } else if (cause instanceof DOMRpcImplementationNotAvailableException) {
                throw new RestconfDocumentedException(cause.getMessage(), ErrorType.APPLICATION,
                    ErrorTag.OPERATION_NOT_SUPPORTED);
            }
            throw new RestconfDocumentedException("The operation encountered an unexpected error while executing.",
                cause);
        } catch (final CancellationException e) {
            final String errMsg = "The operation was cancelled while executing.";
            LOG.debug("Cancel RpcExecution: {}", errMsg, e);
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

    private ListenableFuture<DOMRpcResult> invokeSalRemoteRpcSubscribeRPC(final NormalizedNodeContext payload) {
        final ContainerNode value = (ContainerNode) payload.getData();
        final QName rpcQName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final Optional<DataContainerChild<? extends PathArgument, ?>> path = value.getChild(
            new NodeIdentifier(QName.create(payload.getInstanceIdentifierContext().getSchemaNode().getQName(),
                "path")));
        final Object pathValue = path.isPresent() ? path.get().getValue() : null;

        if (!(pathValue instanceof YangInstanceIdentifier)) {
            LOG.debug("Instance identifier {} was not normalized correctly", rpcQName);
            throw new RestconfDocumentedException("Instance identifier was not normalized correctly",
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
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
            LOG.debug("Path is empty or contains value node which is not Container or List built-in type at {}",
                pathIdentifier);
            throw new RestconfDocumentedException("Path is empty or contains value node which is not Container or List "
                    + "built-in type.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final QName outputQname = QName.create(rpcQName, "output");
        final QName streamNameQname = QName.create(rpcQName, "stream-name");

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existListenerFor(streamName)) {
            Notificator.createListener(pathIdentifier, streamName, outputType, controllerContext);
        }

        return Futures.immediateFuture(new DefaultDOMRpcResult(output));
    }

    private static RpcDefinition findRpc(final SchemaContext schemaContext, final String identifierDecoded) {
        final String[] splittedIdentifier = identifierDecoded.split(":");
        if (splittedIdentifier.length != 2) {
            LOG.debug("{} could not be split to 2 parts (module:rpc name)", identifierDecoded);
            throw new RestconfDocumentedException(identifierDecoded + " could not be split to 2 parts "
                    + "(module:rpc name)", ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
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
            if ("report-all-tagged".equals(withDefa)) {
                tagged = true;
                withDefa = null;
            }
            if ("report-all".equals(withDefa)) {
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
            throw dataMissing(identifier);
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
            throw dataMissing(identifier);
        }
        return new NormalizedNodeContext(iiWithData, data, QueryParametersParser.parseWriterParameters(uriInfo));
    }

    private static RestconfDocumentedException dataMissing(final String identifier) {
        LOG.debug("Request could not be completed because the relevant data model content does not exist {}",
            identifier);
        return new RestconfDocumentedException("Request could not be completed because the relevant data model content "
            + "does not exist", ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
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

        requireNonNull(identifier);

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
                result.getFutureOfPutData().get();
            } catch (final InterruptedException e) {
                LOG.debug("Update failed for {}", identifier, e);
                throw new RestconfDocumentedException(e.getMessage(), e);
            } catch (final ExecutionException e) {
                final TransactionCommitFailedException failure = Throwables.getCauseAs(e,
                    TransactionCommitFailedException.class);
                if (failure instanceof OptimisticLockFailedException) {
                    if (--tries <= 0) {
                        LOG.debug("Got OptimisticLockFailedException on last try - failing {}", identifier);
                        throw new RestconfDocumentedException(e.getMessage(), e, failure.getErrorList());
                    }

                    LOG.debug("Got OptimisticLockFailedException - trying again {}", identifier);
                    continue;
                }

                LOG.debug("Update failed for {}", identifier, e);
                throw RestconfDocumentedException.decodeAndThrow(e.getMessage(), failure);
            }

            return Response.status(result.getStatus()).build();
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
        checkArgument(payload != null);
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode<?, ?> data = payload.getData();
        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> keyDefinitions = ((ListSchemaNode) schemaNode).getKeyDefinition();
            if (lastPathArgument instanceof NodeIdentifierWithPredicates && data instanceof MapEntryNode) {
                final Map<QName, Object> uriKeyValues = ((NodeIdentifierWithPredicates) lastPathArgument).asMap();
                isEqualUriAndPayloadKeyValues(uriKeyValues, (MapEntryNode) data, keyDefinitions);
            }
        }
    }

    @VisibleForTesting
    public static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
            final List<QName> keyDefinitions) {

        final Map<QName, Object> mutableCopyUriKeyValues = new HashMap<>(uriKeyValues);
        for (final QName keyDefinition : keyDefinitions) {
            final Object uriKeyValue = RestconfDocumentedException.throwIfNull(
                // should be caught during parsing URI to InstanceIdentifier
                mutableCopyUriKeyValues.remove(keyDefinition), ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                "Missing key %s in URI.", keyDefinition);

            final Object dataKeyValue = payload.getIdentifier().getValue(keyDefinition);

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

        if (uriInfo != null) {
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
        }

        if (pointUsed && !insertUsed) {
            throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.");
        }
        if (pointUsed && (insert.equals("first") || insert.equals("last"))) {
            throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }

        FluentFuture<? extends CommitInfo> future;
        if (mountPoint != null) {
            future = this.broker.commitConfigurationDataPost(mountPoint, normalizedII, payload.getData(), insert,
                    point);
        } else {
            future = this.broker.commitConfigurationDataPost(this.controllerContext.getGlobalSchema(), normalizedII,
                    payload.getData(), insert, point);
        }

        try {
            future.get();
        } catch (final InterruptedException e) {
            LOG.info("Error creating data {}", uriInfo != null ? uriInfo.getPath() : "", e);
            throw new RestconfDocumentedException(e.getMessage(), e);
        } catch (final ExecutionException e) {
            LOG.info("Error creating data {}", uriInfo != null ? uriInfo.getPath() : "", e);
            throw RestconfDocumentedException.decodeAndThrow(e.getMessage(), Throwables.getCauseAs(e,
                TransactionCommitFailedException.class));
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
            LOG.info("Location for instance identifier {} was not created", normalizedII, e);
            return null;
        }
        return uriBuilder.build();
    }

    @Override
    public Response deleteConfigurationData(final String identifier) {
        final InstanceIdentifierContext<?> iiWithData = this.controllerContext.toInstanceIdentifier(identifier);
        final DOMMountPoint mountPoint = iiWithData.getMountPoint();
        final YangInstanceIdentifier normalizedII = iiWithData.getInstanceIdentifier();

        final FluentFuture<? extends CommitInfo> future;
        if (mountPoint != null) {
            future = this.broker.commitConfigurationDataDelete(mountPoint, normalizedII);
        } else {
            future = this.broker.commitConfigurationDataDelete(normalizedII);
        }

        try {
            future.get();
        } catch (final InterruptedException e) {
            throw new RestconfDocumentedException(e.getMessage(), e);
        } catch (final ExecutionException e) {
            final Optional<Throwable> searchedException = Iterables.tryFind(Throwables.getCausalChain(e),
                    Predicates.instanceOf(ModifiedNodeDoesNotExistException.class)).toJavaUtil();
            if (searchedException.isPresent()) {
                throw new RestconfDocumentedException("Data specified for delete doesn't exist.", ErrorType.APPLICATION,
                    ErrorTag.DATA_MISSING, e);
            }

            throw RestconfDocumentedException.decodeAndThrow(e.getMessage(), Throwables.getCauseAs(e,
                TransactionCommitFailedException.class));
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
        boolean skipNotificationDataUsed = false;
        boolean skipNotificationData = false;

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
                case "odl-skip-notification-data":
                    if (!skipNotificationDataUsed) {
                        skipNotificationDataUsed = true;
                        skipNotificationData = Boolean.parseBoolean(entry.getValue().iterator().next());
                    } else {
                        throw new RestconfDocumentedException(
                                "Odl-skip-notification-data parameter can be used only once.");
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
            response = dataSubs(identifier, uriInfo, start, stop, filter, leafNodesOnly, skipNotificationData);
        } else if (identifier.contains(NOTIFICATION_STREAM)) {
            response = notifStream(identifier, uriInfo, start, stop, filter);
        }

        if (response != null) {
            // prepare node with value of location
            final InstanceIdentifierContext<?> iid = prepareIIDSubsStreamOutput();
            final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> builder =
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
    private InstanceIdentifierContext<?> prepareIIDSubsStreamOutput() {
        final QName qnameBase = QName.create("subscribe:to:notification", "2016-10-28", "notifi");
        final EffectiveModelContext schemaCtx = controllerContext.getGlobalSchema();
        final DataSchemaNode location = ((ContainerSchemaNode) schemaCtx
                .findModule(qnameBase.getModule()).orElse(null)
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
            listener.setQueryParams(start, Optional.ofNullable(stop), Optional.ofNullable(filter), false, false);
        }

        final UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();

        final WebSocketServer webSocketServerInstance = WebSocketServer.getInstance(NOTIFICATION_PORT);
        final int notificationPort = webSocketServerInstance.getPort();


        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme(getWsScheme(uriInfo));

        return uriToWebsocketServerBuilder.replacePath(streamName).build();
    }

    private static String getWsScheme(final UriInfo uriInfo) {
        URI uri = uriInfo.getAbsolutePath();
        if (uri == null) {
            return "ws";
        }
        String subscriptionScheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return subscriptionScheme.equals("https") ? "wss" : "ws";
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
            final String filter, final boolean leafNodesOnly, final boolean skipNotificationData) {
        final String streamName = Notificator.createStreamNameFromUri(identifier);
        if (Strings.isNullOrEmpty(streamName)) {
            throw new RestconfDocumentedException("Stream name is empty.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final ListenerAdapter listener = Notificator.getListenerFor(streamName);
        if (listener == null) {
            throw new RestconfDocumentedException("Stream was not found.", ErrorType.PROTOCOL,
                    ErrorTag.UNKNOWN_ELEMENT);
        }
        listener.setQueryParams(start, Optional.ofNullable(stop), Optional.ofNullable(filter), leafNodesOnly,
                skipNotificationData);

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

        final UriBuilder uriToWebsocketServerBuilder = uriBuilder.port(notificationPort).scheme(getWsScheme(uriInfo));

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
            throw new RestconfDocumentedException(e.getMessage(), e);
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
            throw new RestconfDocumentedException(e.getMessage(), e);
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
        final Optional<DataContainerChild<? extends PathArgument, ?>> optAugNode = value.getChild(
            SAL_REMOTE_AUG_IDENTIFIER);
        if (optAugNode.isEmpty()) {
            return null;
        }
        final DataContainerChild<? extends PathArgument, ?> augNode = optAugNode.get();
        if (!(augNode instanceof AugmentationNode)) {
            return null;
        }
        final Optional<DataContainerChild<? extends PathArgument, ?>> enumNode = ((AugmentationNode) augNode).getChild(
                new NodeIdentifier(QName.create(SAL_REMOTE_AUGMENT, paramName)));
        if (enumNode.isEmpty()) {
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

    private MapNode makeModuleMapNode(final Collection<? extends Module> modules) {
        requireNonNull(modules);
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode moduleSchemaNode = this.controllerContext
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft02.RestConfModule.MODULE_LIST_SCHEMA_NODE);
        checkState(moduleSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listModuleBuilder =
                Builders.mapBuilder((ListSchemaNode) moduleSchemaNode);

        for (final Module module : modules) {
            listModuleBuilder.withChild(toModuleEntryNode(module, moduleSchemaNode));
        }
        return listModuleBuilder.build();
    }

    private MapEntryNode toModuleEntryNode(final Module module, final DataSchemaNode moduleSchemaNode) {
        checkArgument(moduleSchemaNode instanceof ListSchemaNode,
                "moduleSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listModuleSchemaNode = (ListSchemaNode) moduleSchemaNode;
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> moduleNodeValues =
                Builders.mapEntryBuilder(listModuleSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(nameSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues
                .withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(module.getName()).build());

        final QNameModule qNameModule = module.getQNameModule();

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "revision");
        final DataSchemaNode revisionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(revisionSchemaNode instanceof LeafSchemaNode);
        final Optional<Revision> revision = qNameModule.getRevision();
        moduleNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) revisionSchemaNode)
                .withValue(revision.map(Revision::toString).orElse("")).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "namespace");
        final DataSchemaNode namespaceSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(namespaceSchemaNode instanceof LeafSchemaNode);
        moduleNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) namespaceSchemaNode)
                .withValue(qNameModule.getNamespace().toString()).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listModuleSchemaNode, "feature");
        final DataSchemaNode featureSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(featureSchemaNode instanceof LeafListSchemaNode);
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
        checkArgument(streamSchemaNode instanceof ListSchemaNode,
                "streamSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) streamSchemaNode;
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues =
                Builders.mapEntryBuilder(listStreamSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(nameSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(streamName).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "description");
        final DataSchemaNode descriptionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(descriptionSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue("DESCRIPTION_PLACEHOLDER").build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-support");
        final DataSchemaNode replaySupportSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(replaySupportSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) replaySupportSchemaNode)
                .withValue(Boolean.TRUE).build());

        instanceDataChildrenByName =
                ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "replay-log-creation-time");
        final DataSchemaNode replayLogCreationTimeSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(replayLogCreationTimeSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) replayLogCreationTimeSchemaNode).withValue("").build());

        instanceDataChildrenByName = ControllerContext.findInstanceDataChildrenByName(listStreamSchemaNode, "events");
        final DataSchemaNode eventsSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        checkState(eventsSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) eventsSchemaNode).withValue(Empty.getInstance()).build());

        return streamNodeValues.build();
    }

    /**
     * Prepare stream for notification.
     *
     * @param payload
     *            contains list of qnames of notifications
     * @return - checked future object
     */
    private ListenableFuture<DOMRpcResult> invokeSalRemoteRpcNotifiStrRPC(final NormalizedNodeContext payload) {
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

        StringBuilder streamNameBuilder = new StringBuilder(streamName);
        final Iterator<LeafSetEntryNode> iterator = entryNodes.iterator();
        while (iterator.hasNext()) {
            final QName valueQName = QName.create((String) iterator.next().getValue());
            final URI namespace = valueQName.getModule().getNamespace();
            final Module module = controllerContext.findModuleByNamespace(namespace);
            checkNotNull(module, "Module for namespace %s does not exist", namespace);
            NotificationDefinition notifiDef = null;
            for (final NotificationDefinition notification : module.getNotifications()) {
                if (notification.getQName().equals(valueQName)) {
                    notifiDef = notification;
                    break;
                }
            }
            final String moduleName = module.getName();
            checkNotNull(notifiDef, "Notification %s does not exist in module %s", valueQName, moduleName);
            paths.add(notifiDef.getPath());
            streamNameBuilder.append(moduleName).append(':').append(valueQName.getLocalName());
            if (iterator.hasNext()) {
                streamNameBuilder.append(',');
            }
        }

        streamName = streamNameBuilder.toString();

        final QName rpcQName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final QName outputQname = QName.create(rpcQName, "output");
        final QName streamNameQname = QName.create(rpcQName, "notification-stream-identifier");

        final ContainerNode output =
                ImmutableContainerNodeBuilder.create().withNodeIdentifier(new NodeIdentifier(outputQname))
                        .withChild(ImmutableNodes.leafNode(streamNameQname, streamName)).build();

        if (!Notificator.existNotificationListenerFor(streamName)) {
            Notificator.createNotificationListener(paths, streamName, outputType, controllerContext);
        }

        return Futures.immediateFuture(new DefaultDOMRpcResult(output));
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
