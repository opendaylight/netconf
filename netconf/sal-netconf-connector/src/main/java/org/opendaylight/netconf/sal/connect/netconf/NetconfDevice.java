/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_NODEID;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.util.concurrent.EventExecutor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.rfc8528.model.api.SchemaMountConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This is a mediator between NetconfDeviceCommunicator and NetconfDeviceSalFacade.
 */
public class NetconfDevice
        implements RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> {

    @SuppressFBWarnings(value = "SLF4J_LOGGER_SHOULD_BE_PRIVATE",
            justification = "Needed for common logging of related classes")
    static final Logger LOG = LoggerFactory.getLogger(NetconfDevice.class);

    private static final QName RFC8528_SCHEMA_MOUNTS_QNAME = QName.create(
        SchemaMountConstants.RFC8528_MODULE, "schema-mounts").intern();
    private static final YangInstanceIdentifier RFC8528_SCHEMA_MOUNTS = YangInstanceIdentifier.create(
        NodeIdentifier.create(RFC8528_SCHEMA_MOUNTS_QNAME));

    protected final RemoteDeviceId id;
    protected final EffectiveModelContextFactory schemaContextFactory;
    protected final SchemaSourceRegistry schemaRegistry;
    protected final SchemaRepository schemaRepository;

    protected final List<SchemaSourceRegistration<?>> sourceRegistrations = new ArrayList<>();

    private final RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private final ListeningExecutorService processingExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfDeviceSchemasResolver stateSchemasResolver;
    private final NotificationHandler notificationHandler;
    private final boolean reconnectOnSchemasChange;
    private final BaseNetconfSchemas baseSchemas;
    private final NetconfNode node;
    private final EventExecutor eventExecutor;
    private final NetconfNodeAugmentedOptional nodeOptional;

    @GuardedBy("this")
    private boolean connected = false;

    // Message transformer is constructed once the schemas are available
    private MessageTransformer<NetconfMessage> messageTransformer;

    public NetconfDevice(final SchemaResourcesDTO schemaResourcesDTO, final BaseNetconfSchemas baseSchemas,
            final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
            final ListeningExecutorService globalProcessingExecutor, final boolean reconnectOnSchemasChange) {
        this(schemaResourcesDTO, baseSchemas, id, salFacade, globalProcessingExecutor, reconnectOnSchemasChange, null,
            null, null, null);
    }

    public NetconfDevice(final SchemaResourcesDTO schemaResourcesDTO, final BaseNetconfSchemas baseSchemas,
            final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
            final ListeningExecutorService globalProcessingExecutor, final boolean reconnectOnSchemasChange,
            final DeviceActionFactory deviceActionFactory, final NetconfNode node, final EventExecutor eventExecutor,
            final NetconfNodeAugmentedOptional nodeOptional) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        this.deviceActionFactory = deviceActionFactory;
        this.node = node;
        this.eventExecutor = eventExecutor;
        this.nodeOptional = nodeOptional;
        this.schemaRegistry = schemaResourcesDTO.getSchemaRegistry();
        this.schemaRepository = schemaResourcesDTO.getSchemaRepository();
        this.schemaContextFactory = schemaResourcesDTO.getSchemaContextFactory();
        this.salFacade = salFacade;
        this.stateSchemasResolver = schemaResourcesDTO.getStateSchemasResolver();
        this.processingExecutor = requireNonNull(globalProcessingExecutor);
        this.notificationHandler = new NotificationHandler(salFacade, id);
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
                                  final NetconfDeviceCommunicator listener) {
        // SchemaContext setup has to be performed in a dedicated thread since
        // we are in a netty thread in this method
        // Yang models are being downloaded in this method and it would cause a
        // deadlock if we used the netty thread
        // http://netty.io/wiki/thread-model.html
        setConnected(true);
        LOG.debug("{}: Session to remote device established with {}", id, remoteSessionCapabilities);

        final BaseSchema baseSchema = resolveBaseSchema(remoteSessionCapabilities.isNotificationsSupported());
        final NetconfDeviceRpc initRpc = new NetconfDeviceRpc(baseSchema.getEffectiveModelContext(), listener,
            new NetconfMessageTransformer(baseSchema.getMountPointContext(), false, baseSchema));
        final ListenableFuture<DeviceSources> sourceResolverFuture = processingExecutor.submit(
            new DeviceSourcesResolver(id, baseSchema, initRpc, remoteSessionCapabilities, stateSchemasResolver));

        if (shouldListenOnSchemaChange(remoteSessionCapabilities)) {
            registerToBaseNetconfStream(initRpc, listener);
        }

        // Set up the SchemaContext for the device
        final ListenableFuture<EffectiveModelContext> futureSchema = Futures.transformAsync(sourceResolverFuture,
            deviceSources -> assembleSchemaContext(deviceSources, remoteSessionCapabilities), processingExecutor);

        // Potentially acquire mount point list and interpret it
        final ListenableFuture<MountPointContext> futureContext = Futures.transformAsync(futureSchema,
            schemaContext -> createMountPointContext(schemaContext, baseSchema, listener), processingExecutor);

        Futures.addCallback(futureContext, new FutureCallback<MountPointContext>() {
            @Override
            public void onSuccess(final MountPointContext result) {
                handleSalInitializationSuccess(result, remoteSessionCapabilities,
                    getDeviceSpecificRpc(result, listener), listener);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("{}: Unexpected error resolving device sources", id, cause);

                // No more sources, fail or try to reconnect
                if (cause instanceof EmptySchemaContextException) {
                    if (nodeOptional != null && nodeOptional.getIgnoreMissingSchemaSources().isAllowed()) {
                        eventExecutor.schedule(() -> {
                            LOG.warn("Reconnection is allowed! This can lead to unexpected errors at runtime.");
                            LOG.warn("{} : No more sources for schema context.", id);
                            LOG.info("{} : Try to remount device.", id);
                            onRemoteSessionDown();
                            salFacade.onDeviceReconnected(remoteSessionCapabilities, node);
                        }, nodeOptional.getIgnoreMissingSchemaSources().getReconnectTime().toJava(),
                            TimeUnit.MILLISECONDS);
                        return;
                    }
                }

                handleSalInitializationFailure(cause, listener);
                salFacade.onDeviceFailed(cause);
            }
        }, MoreExecutors.directExecutor());
    }

    private void registerToBaseNetconfStream(final NetconfDeviceRpc deviceRpc,
                                             final NetconfDeviceCommunicator listener) {
        // TODO check whether the model describing create subscription is present in schema
        // Perhaps add a default schema context to support create-subscription if the model was not provided
        // (same as what we do for base netconf operations in transformer)
        final ListenableFuture<DOMRpcResult> rpcResultListenableFuture = deviceRpc.invokeRpc(
                NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME,
                NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_CONTENT);

        Futures.addCallback(rpcResultListenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult domRpcResult) {
                notificationHandler.addNotificationFilter(notification -> {
                    if (NetconfCapabilityChange.QNAME.equals(notification.getBody().getNodeType())) {
                        LOG.info("{}: Schemas change detected, reconnecting", id);
                        // Only disconnect is enough,
                        // the reconnecting nature of the connector will take care of reconnecting
                        listener.disconnect();
                        return Optional.empty();
                    }
                    return Optional.of(notification);
                });
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to subscribe to base notification stream. Schemas will not be reloaded on the fly",
                        throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private boolean shouldListenOnSchemaChange(final NetconfSessionPreferences remoteSessionCapabilities) {
        return remoteSessionCapabilities.isNotificationsSupported() && reconnectOnSchemasChange;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private synchronized void handleSalInitializationSuccess(final MountPointContext result,
                                        final NetconfSessionPreferences remoteSessionCapabilities,
                                        final DOMRpcService deviceRpc,
                                        final RemoteDeviceCommunicator<NetconfMessage> listener) {
        //NetconfDevice.SchemaSetup can complete after NetconfDeviceCommunicator was closed. In that case do nothing,
        //since salFacade.onDeviceDisconnected was already called.
        if (connected) {
            this.messageTransformer = new NetconfMessageTransformer(result, true,
                resolveBaseSchema(remoteSessionCapabilities.isNotificationsSupported()));

            // salFacade.onDeviceConnected has to be called before the notification handler is initialized
            this.salFacade.onDeviceConnected(result, remoteSessionCapabilities, deviceRpc,
                    this.deviceActionFactory == null ? null : this.deviceActionFactory.createDeviceAction(
                            this.messageTransformer, listener, result.getEffectiveModelContext()));
            this.notificationHandler.onRemoteSchemaUp(this.messageTransformer);

            LOG.info("{}: Netconf connector initialized successfully", id);
        } else {
            LOG.warn("{}: Device communicator was closed before schema setup finished.", id);
        }
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void handleSalInitializationFailure(final Throwable throwable,
                                                final RemoteDeviceCommunicator<NetconfMessage> listener) {
        LOG.error("{}: Initialization in sal failed, disconnecting from device", id, throwable);
        listener.close();
        onRemoteSessionDown();
        resetMessageTransformer();
    }

    /**
     * Set the transformer to null as is in initial state.
     */
    private void resetMessageTransformer() {
        updateTransformer(null);
    }

    private synchronized void updateTransformer(final MessageTransformer<NetconfMessage> transformer) {
        messageTransformer = transformer;
    }

    private synchronized void setConnected(final boolean connected) {
        this.connected = connected;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private ListenableFuture<EffectiveModelContext> assembleSchemaContext(final DeviceSources deviceSources,
            final NetconfSessionPreferences remoteSessionCapabilities) {
        LOG.debug("{}: Resolved device sources to {}", id, deviceSources);
        final SchemaSourceProvider<YangTextSchemaSource> yangProvider = deviceSources.getSourceProvider();
        for (final SourceIdentifier sourceId : deviceSources.getProvidedSources()) {
            sourceRegistrations.add(schemaRegistry.registerSchemaSource(yangProvider,
                PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class,
                    PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
        }

        return new SchemaSetup(deviceSources, remoteSessionCapabilities).startResolution();
    }

    private ListenableFuture<MountPointContext> createMountPointContext(final EffectiveModelContext schemaContext,
            final BaseSchema baseSchema, final NetconfDeviceCommunicator listener) {
        final MountPointContext emptyContext = new EmptyMountPointContext(schemaContext);
        if (!schemaContext.findModule(SchemaMountConstants.RFC8528_MODULE).isPresent()) {
            return Futures.immediateFuture(emptyContext);
        }

        // Create a temporary RPC invoker and acquire the mount point tree
        LOG.debug("{}: Acquiring available mount points", id);
        final NetconfDeviceRpc deviceRpc = new NetconfDeviceRpc(schemaContext, listener,
            new NetconfMessageTransformer(emptyContext, false, baseSchema));

        return Futures.transform(deviceRpc.invokeRpc(NetconfMessageTransformUtil.NETCONF_GET_QNAME,
            Builders.containerBuilder().withNodeIdentifier(NETCONF_GET_NODEID)
                .withChild(NetconfMessageTransformUtil.toFilterStructure(RFC8528_SCHEMA_MOUNTS, schemaContext))
                .build()), rpcResult -> processSchemaMounts(rpcResult, emptyContext), MoreExecutors.directExecutor());
    }

    private MountPointContext processSchemaMounts(final DOMRpcResult rpcResult, final MountPointContext emptyContext) {
        final Collection<? extends RpcError> errors = rpcResult.getErrors();
        if (!errors.isEmpty()) {
            LOG.warn("{}: Schema-mounts acquisition resulted in errors {}", id, errors);
        }
        final NormalizedNode<?, ?> schemaMounts = rpcResult.getResult();
        if (schemaMounts == null) {
            LOG.debug("{}: device does not define any schema mounts", id);
            return emptyContext;
        }
        if (!(schemaMounts instanceof ContainerNode)) {
            LOG.warn("{}: ignoring non-container schema mounts {}", id, schemaMounts);
            return emptyContext;
        }

        return DeviceMountPointContext.create(emptyContext, (ContainerNode) schemaMounts);
    }

    @Override
    public void onRemoteSessionDown() {
        setConnected(false);
        notificationHandler.onRemoteSchemaDown();

        salFacade.onDeviceDisconnected();
        sourceRegistrations.forEach(SchemaSourceRegistration::close);
        sourceRegistrations.clear();
        resetMessageTransformer();
    }

    @Override
    public void onRemoteSessionFailed(final Throwable throwable) {
        setConnected(false);
        salFacade.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final NetconfMessage notification) {
        notificationHandler.handleNotification(notification);
    }

    private BaseSchema resolveBaseSchema(final boolean notificationSupport) {
        return notificationSupport ? baseSchemas.getBaseSchemaWithNotifications() : baseSchemas.getBaseSchema();
    }

    protected NetconfDeviceRpc getDeviceSpecificRpc(final MountPointContext result,
            final RemoteDeviceCommunicator<NetconfMessage> listener) {
        return new NetconfDeviceRpc(result.getEffectiveModelContext(), listener,
            new NetconfMessageTransformer(result, true, baseSchemas.getBaseSchema()));
    }

    /**
     * Just a transfer object containing schema related dependencies. Injected in constructor.
     */
    public static class SchemaResourcesDTO {
        private final SchemaSourceRegistry schemaRegistry;
        private final SchemaRepository schemaRepository;
        private final EffectiveModelContextFactory schemaContextFactory;
        private final NetconfDeviceSchemasResolver stateSchemasResolver;

        public SchemaResourcesDTO(final SchemaSourceRegistry schemaRegistry,
                                  final SchemaRepository schemaRepository,
                                  final EffectiveModelContextFactory schemaContextFactory,
                                  final NetconfDeviceSchemasResolver deviceSchemasResolver) {
            this.schemaRegistry = requireNonNull(schemaRegistry);
            this.schemaRepository = requireNonNull(schemaRepository);
            this.schemaContextFactory = requireNonNull(schemaContextFactory);
            this.stateSchemasResolver = requireNonNull(deviceSchemasResolver);
        }

        public SchemaSourceRegistry getSchemaRegistry() {
            return schemaRegistry;
        }

        public SchemaRepository getSchemaRepository() {
            return schemaRepository;
        }

        public EffectiveModelContextFactory getSchemaContextFactory() {
            return schemaContextFactory;
        }

        public NetconfDeviceSchemasResolver getStateSchemasResolver() {
            return stateSchemasResolver;
        }
    }

    /**
     * A dedicated exception to indicate when we fail to setup a SchemaContext.
     *
     * @author Robert Varga
     */
    private static final class EmptySchemaContextException extends Exception {
        private static final long serialVersionUID = 1L;

        EmptySchemaContextException(final String message) {
            super(message);
        }
    }

    /**
     * Schema builder that tries to build schema context from provided sources or biggest subset of it.
     */
    private final class SchemaSetup implements FutureCallback<EffectiveModelContext> {
        private final SettableFuture<EffectiveModelContext> resultFuture = SettableFuture.create();

        private final DeviceSources deviceSources;
        private final NetconfSessionPreferences remoteSessionCapabilities;
        private final NetconfDeviceCapabilities capabilities;

        private Collection<SourceIdentifier> requiredSources;

        SchemaSetup(final DeviceSources deviceSources, final NetconfSessionPreferences remoteSessionCapabilities) {
            this.deviceSources = deviceSources;
            this.remoteSessionCapabilities = remoteSessionCapabilities;
            this.capabilities = remoteSessionCapabilities.getNetconfDeviceCapabilities();

            // If device supports notifications and does not contain necessary modules, add them automatically
            if (remoteSessionCapabilities.containsNonModuleCapability(
                    XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_CAPABILITY_NOTIFICATION_1_0)) {
                deviceSources.getRequiredSourcesQName().addAll(
                        Arrays.asList(
                                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                                        .$YangModuleInfoImpl.getInstance().getName(),
                                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                                        .$YangModuleInfoImpl.getInstance().getName()
                        )
                );
            }

            requiredSources = deviceSources.getRequiredSources();
            final Collection<SourceIdentifier> missingSources = filterMissingSources(requiredSources);

            capabilities.addUnresolvedCapabilities(getQNameFromSourceIdentifiers(missingSources),
                    UnavailableCapability.FailureReason.MissingSource);
            requiredSources.removeAll(missingSources);
        }

        ListenableFuture<EffectiveModelContext> startResolution() {
            trySetupSchema();
            return resultFuture;
        }

        @Override
        public void onSuccess(final EffectiveModelContext result) {
            LOG.debug("{}: Schema context built successfully from {}", id, requiredSources);

            final Collection<QName> filteredQNames = Sets.difference(deviceSources.getRequiredSourcesQName(),
                    capabilities.getUnresolvedCapabilites().keySet());
            capabilities.addCapabilities(filteredQNames.stream().map(entry -> new AvailableCapabilityBuilder()
                    .setCapability(entry.toString()).setCapabilityOrigin(
                            remoteSessionCapabilities.getModuleBasedCapsOrigin().get(entry)).build())
                    .collect(Collectors.toList()));

            capabilities.addNonModuleBasedCapabilities(remoteSessionCapabilities
                    .getNonModuleCaps().stream().map(entry -> new AvailableCapabilityBuilder()
                            .setCapability(entry).setCapabilityOrigin(
                                    remoteSessionCapabilities.getNonModuleBasedCapsOrigin().get(entry)).build())
                    .collect(Collectors.toList()));

            resultFuture.set(result);
        }

        @Override
        public void onFailure(final Throwable cause) {
            // schemaBuilderFuture.checkedGet() throws only SchemaResolutionException
            // that might be wrapping a MissingSchemaSourceException so we need to look
            // at the cause of the exception to make sure we don't misinterpret it.
            if (cause instanceof MissingSchemaSourceException) {
                requiredSources = handleMissingSchemaSourceException((MissingSchemaSourceException) cause);
            } else if (cause instanceof SchemaResolutionException) {
                requiredSources = handleSchemaResolutionException((SchemaResolutionException) cause);
            } else {
                LOG.debug("Unhandled failure", cause);
                resultFuture.setException(cause);
                // No more trying...
                return;
            }

            trySetupSchema();
        }

        private void trySetupSchema() {
            if (!requiredSources.isEmpty()) {
                // Initiate async resolution, drive it back based on the result
                LOG.trace("{}: Trying to build schema context from {}", id, requiredSources);
                Futures.addCallback(schemaContextFactory.createEffectiveModelContext(requiredSources), this,
                    MoreExecutors.directExecutor());
            } else {
                LOG.debug("{}: no more sources for schema context", id);
                resultFuture.setException(new EmptySchemaContextException(id + ": No more sources for schema context"));
            }
        }

        private Collection<SourceIdentifier> filterMissingSources(final Collection<SourceIdentifier> origSources) {
            return origSources.parallelStream().filter(sourceIdentifier -> {
                try {
                    schemaRepository.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class).get();
                    return false;
                } catch (InterruptedException | ExecutionException e) {
                    return true;
                }
            }).collect(Collectors.toList());
        }

        private Collection<SourceIdentifier> handleMissingSchemaSourceException(
                final MissingSchemaSourceException exception) {
            // In case source missing, try without it
            final SourceIdentifier missingSource = exception.getSourceId();
            LOG.warn("{}: Unable to build schema context, missing source {}, will reattempt without it",
                id, missingSource);
            LOG.debug("{}: Unable to build schema context, missing source {}, will reattempt without it",
                id, missingSource, exception);
            final Collection<QName> qNameOfMissingSource =
                getQNameFromSourceIdentifiers(Sets.newHashSet(missingSource));
            if (!qNameOfMissingSource.isEmpty()) {
                capabilities.addUnresolvedCapabilities(
                        qNameOfMissingSource, UnavailableCapability.FailureReason.MissingSource);
            }
            return stripUnavailableSource(missingSource);
        }

        private Collection<SourceIdentifier> handleSchemaResolutionException(
                final SchemaResolutionException resolutionException) {
            // In case resolution error, try only with resolved sources
            // There are two options why schema resolution exception occurred : unsatisfied imports or flawed model
            // FIXME Do we really have assurance that these two cases cannot happen at once?
            if (resolutionException.getFailedSource() != null) {
                // flawed model - exclude it
                final SourceIdentifier failedSourceId = resolutionException.getFailedSource();
                LOG.warn("{}: Unable to build schema context, failed to resolve source {}, will reattempt without it",
                    id, failedSourceId);
                LOG.warn("{}: Unable to build schema context, failed to resolve source {}, will reattempt without it",
                    id, failedSourceId, resolutionException);
                capabilities.addUnresolvedCapabilities(
                        getQNameFromSourceIdentifiers(Collections.singleton(failedSourceId)),
                        UnavailableCapability.FailureReason.UnableToResolve);
                return stripUnavailableSource(resolutionException.getFailedSource());
            }
            // unsatisfied imports
            final Set<SourceIdentifier> unresolvedSources = resolutionException.getUnsatisfiedImports().keySet();
            capabilities.addUnresolvedCapabilities(getQNameFromSourceIdentifiers(unresolvedSources),
                UnavailableCapability.FailureReason.UnableToResolve);
            LOG.warn("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
                id, resolutionException.getUnsatisfiedImports());
            LOG.debug("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
                id, resolutionException.getUnsatisfiedImports(), resolutionException);
            return resolutionException.getResolvedSources();
        }

        private Collection<SourceIdentifier> stripUnavailableSource(final SourceIdentifier sourceIdToRemove) {
            final LinkedList<SourceIdentifier> sourceIdentifiers = new LinkedList<>(requiredSources);
            checkState(sourceIdentifiers.remove(sourceIdToRemove),
                    "%s: Trying to remove %s from %s failed", id, sourceIdToRemove, requiredSources);
            return sourceIdentifiers;
        }

        private Collection<QName> getQNameFromSourceIdentifiers(final Collection<SourceIdentifier> identifiers) {
            final Collection<QName> qNames = Collections2.transform(identifiers, this::getQNameFromSourceIdentifier);

            if (qNames.isEmpty()) {
                LOG.debug("{}: Unable to map any source identifiers to a capability reported by device : {}", id,
                        identifiers);
            }
            return Collections2.filter(qNames, Predicates.notNull());
        }

        private QName getQNameFromSourceIdentifier(final SourceIdentifier identifier) {
            // Required sources are all required and provided merged in DeviceSourcesResolver
            for (final QName qname : deviceSources.getRequiredSourcesQName()) {
                if (!qname.getLocalName().equals(identifier.getName())) {
                    continue;
                }

                if (identifier.getRevision().equals(qname.getRevision())) {
                    return qname;
                }
            }
            LOG.warn("Unable to map identifier to a devices reported capability: {} Available: {}",identifier,
                    deviceSources.getRequiredSourcesQName());
            // return null since we cannot find the QName,
            // this capability will be removed from required sources and not reported as unresolved-capability
            return null;
        }
    }
}
