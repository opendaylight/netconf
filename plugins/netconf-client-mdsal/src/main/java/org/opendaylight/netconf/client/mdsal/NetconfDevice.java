/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.impl.BaseSchema;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformer;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.rfc8528.model.api.SchemaMountConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This is a mediator between NetconfDeviceCommunicator and NetconfDeviceSalFacade.
 */
public class NetconfDevice implements RemoteDevice<NetconfDeviceCommunicator> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDevice.class);

    private static final QName RFC8528_SCHEMA_MOUNTS_QNAME = QName.create(
        SchemaMountConstants.RFC8528_MODULE, "schema-mounts").intern();
    private static final YangInstanceIdentifier RFC8528_SCHEMA_MOUNTS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RFC8528_SCHEMA_MOUNTS_QNAME));

    protected final RemoteDeviceId id;
    protected final EffectiveModelContextFactory schemaContextFactory;
    protected final SchemaSourceRegistry schemaRegistry;
    protected final SchemaRepository schemaRepository;

    protected final List<Registration> sourceRegistrations = new ArrayList<>();

    private final RemoteDeviceHandler salFacade;
    private final Executor processingExecutor;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfDeviceSchemasResolver stateSchemasResolver;
    private final NotificationHandler notificationHandler;
    private final boolean reconnectOnSchemasChange;
    private final BaseNetconfSchemas baseSchemas;

    @GuardedBy("this")
    private boolean connected = false;

    public NetconfDevice(final SchemaResourcesDTO schemaResourcesDTO, final BaseNetconfSchemas baseSchemas,
            final RemoteDeviceId id, final RemoteDeviceHandler salFacade, final Executor globalProcessingExecutor,
            final boolean reconnectOnSchemasChange) {
        this(schemaResourcesDTO, baseSchemas, id, salFacade, globalProcessingExecutor, reconnectOnSchemasChange, null);
    }

    public NetconfDevice(final SchemaResourcesDTO schemaResourcesDTO, final BaseNetconfSchemas baseSchemas,
            final RemoteDeviceId id, final RemoteDeviceHandler salFacade, final Executor globalProcessingExecutor,
            final boolean reconnectOnSchemasChange, final DeviceActionFactory deviceActionFactory) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        this.deviceActionFactory = deviceActionFactory;
        schemaRegistry = schemaResourcesDTO.getSchemaRegistry();
        schemaRepository = schemaResourcesDTO.getSchemaRepository();
        schemaContextFactory = schemaResourcesDTO.getSchemaContextFactory();
        this.salFacade = salFacade;
        stateSchemasResolver = schemaResourcesDTO.getStateSchemasResolver();
        processingExecutor = requireNonNull(globalProcessingExecutor);
        notificationHandler = new NotificationHandler(salFacade, id);
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
                                  final NetconfDeviceCommunicator listener) {
        // SchemaContext setup has to be performed in a dedicated thread since we are in a Netty thread in this method
        // YANG models are being downloaded in this method and it would cause a deadlock if we used the netty thread
        // https://netty.io/wiki/thread-model.html
        setConnected(true);
        LOG.debug("{}: Session to remote device established with {}", id, remoteSessionCapabilities);

        final BaseSchema baseSchema = resolveBaseSchema(remoteSessionCapabilities.isNotificationsSupported());
        final NetconfDeviceRpc initRpc = new NetconfDeviceRpc(baseSchema.getEffectiveModelContext(), listener,
            new NetconfMessageTransformer(baseSchema.getMountPointContext(), false, baseSchema));
        final ListenableFuture<DeviceSources> sourceResolverFuture = Futures.submit(
            new DeviceSourcesResolver(id, baseSchema, initRpc, remoteSessionCapabilities, stateSchemasResolver),
            processingExecutor);

        if (shouldListenOnSchemaChange(remoteSessionCapabilities)) {
            registerToBaseNetconfStream(initRpc, listener);
        }

        // Set up the EffectiveModelContext for the device
        final var futureSchema = Futures.transformAsync(sourceResolverFuture,
            deviceSources -> assembleSchemaContext(deviceSources, remoteSessionCapabilities), processingExecutor);

        Futures.addCallback(
            // Potentially acquire mount point list and interpret it
            Futures.transformAsync(futureSchema,
                result -> Futures.transform(createMountPointContext(result.modelContext(), baseSchema, listener),
                    mount -> new NetconfDeviceSchema(result.capabilities(), mount), processingExecutor),
                processingExecutor),
            new FutureCallback<>() {
                @Override
                public void onSuccess(final NetconfDeviceSchema result) {
                    handleSalInitializationSuccess(listener, result, remoteSessionCapabilities,
                        getDeviceSpecificRpc(result.mountContext(), listener, baseSchema));
                }

                @Override
                public void onFailure(final Throwable cause) {
                    handleSalInitializationFailure(listener, cause);
                }
            }, MoreExecutors.directExecutor());
    }

    private void registerToBaseNetconfStream(final NetconfDeviceRpc deviceRpc,
                                             final NetconfDeviceCommunicator listener) {
        // TODO check whether the model describing create subscription is present in schema
        // Perhaps add a default schema context to support create-subscription if the model was not provided
        // (same as what we do for base netconf operations in transformer)
        final var rpcResultListenableFuture = deviceRpc.invokeRpc(
                NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME,
                NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_CONTENT);

        Futures.addCallback(rpcResultListenableFuture, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult domRpcResult) {
                notificationHandler.addNotificationFilter(notification -> {
                    if (NetconfCapabilityChange.QNAME.equals(notification.getBody().name().getNodeType())) {
                        LOG.info("{}: Schemas change detected, reconnecting", id);
                        // Only disconnect is enough,
                        // the reconnecting nature of the connector will take care of reconnecting
                        listener.disconnect();
                        return false;
                    }
                    return true;
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

    private synchronized void handleSalInitializationSuccess(final RemoteDeviceCommunicator listener,
            final NetconfDeviceSchema deviceSchema, final NetconfSessionPreferences remoteSessionCapabilities,
            final Rpcs deviceRpc) {
        // NetconfDevice.SchemaSetup can complete after NetconfDeviceCommunicator was closed. In that case do nothing,
        // since salFacade.onDeviceDisconnected was already called.
        if (!connected) {
            LOG.warn("{}: Device communicator was closed before schema setup finished.", id);
            return;
        }

        final var messageTransformer = new NetconfMessageTransformer(deviceSchema.mountContext(), true,
            resolveBaseSchema(remoteSessionCapabilities.isNotificationsSupported()));

        // Order is important here: salFacade has to see the device come up and then the notificationHandler can deliver
        // whatever notifications have been held back
        salFacade.onDeviceConnected(deviceSchema, remoteSessionCapabilities,
            new RemoteDeviceServices(deviceRpc, deviceActionFactory == null ? null
                : deviceActionFactory.createDeviceAction(messageTransformer, listener)));
        notificationHandler.onRemoteSchemaUp(messageTransformer);

        LOG.info("{}: Netconf connector initialized successfully", id);
    }

    private void handleSalInitializationFailure(final RemoteDeviceCommunicator listener, final Throwable cause) {
        LOG.warn("{}: Unexpected error resolving device sources", id, cause);
        listener.close();
        cleanupInitialization();
        salFacade.onDeviceFailed(cause);
    }

    private synchronized void cleanupInitialization() {
        connected = false;
        notificationHandler.onRemoteSchemaDown();
        sourceRegistrations.forEach(Registration::close);
        sourceRegistrations.clear();
    }

    private synchronized void setConnected(final boolean connected) {
        this.connected = connected;
    }

    private ListenableFuture<SchemaResult> assembleSchemaContext(final DeviceSources deviceSources,
            final NetconfSessionPreferences remoteSessionCapabilities) {
        LOG.debug("{}: Resolved device sources to {}", id, deviceSources);

        sourceRegistrations.addAll(deviceSources.register(schemaRegistry));

        return new SchemaSetup(deviceSources, remoteSessionCapabilities).startResolution();
    }

    private ListenableFuture<@NonNull MountPointContext> createMountPointContext(
            final EffectiveModelContext schemaContext, final BaseSchema baseSchema,
            final NetconfDeviceCommunicator listener) {
        final MountPointContext emptyContext = MountPointContext.of(schemaContext);
        if (schemaContext.findModule(SchemaMountConstants.RFC8528_MODULE).isEmpty()) {
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
        final var errors = rpcResult.errors();
        if (!errors.isEmpty()) {
            LOG.warn("{}: Schema-mounts acquisition resulted in errors {}", id, errors);
        }
        final var schemaMounts = rpcResult.value();
        if (schemaMounts == null) {
            LOG.debug("{}: device does not define any schema mounts", id);
            return emptyContext;
        }

        return DeviceMountPointContext.create(emptyContext, schemaMounts);
    }

    @Override
    public void onRemoteSessionDown() {
        cleanupInitialization();
        salFacade.onDeviceDisconnected();
    }

    @Override
    public void onNotification(final NetconfMessage notification) {
        notificationHandler.handleNotification(notification);
    }

    private BaseSchema resolveBaseSchema(final boolean notificationSupport) {
        return notificationSupport ? baseSchemas.getBaseSchemaWithNotifications() : baseSchemas.getBaseSchema();
    }

    protected NetconfDeviceRpc getDeviceSpecificRpc(final MountPointContext result,
            final RemoteDeviceCommunicator listener, final BaseSchema schema) {
        return new NetconfDeviceRpc(result.getEffectiveModelContext(), listener,
            new NetconfMessageTransformer(result, true, schema));
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
            stateSchemasResolver = requireNonNull(deviceSchemasResolver);
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
     * A dedicated exception to indicate when we fail to setup an {@link EffectiveModelContext}.
     */
    public static final class EmptySchemaContextException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        public EmptySchemaContextException(final String message) {
            super(message);
        }
    }

    /**
     * {@link NetconfDeviceCapabilities} and {@link EffectiveModelContext}.
     */
    private record SchemaResult(
        @NonNull NetconfDeviceCapabilities capabilities,
        @NonNull EffectiveModelContext modelContext) {

        SchemaResult {
            requireNonNull(capabilities);
            requireNonNull(modelContext);
        }
    }

    /**
     * Schema builder that tries to build schema context from provided sources or biggest subset of it.
     */
    private final class SchemaSetup implements FutureCallback<EffectiveModelContext> {
        private final SettableFuture<SchemaResult> resultFuture = SettableFuture.create();

        private final Set<AvailableCapability> nonModuleBasedCapabilities = new HashSet<>();
        private final Map<QName, FailureReason> unresolvedCapabilites = new HashMap<>();
        private final Set<AvailableCapability> resolvedCapabilities = new HashSet<>();

        private final DeviceSources deviceSources;
        private final NetconfSessionPreferences remoteSessionCapabilities;

        private Collection<SourceIdentifier> requiredSources;

        SchemaSetup(final DeviceSources deviceSources, final NetconfSessionPreferences remoteSessionCapabilities) {
            this.deviceSources = deviceSources;
            this.remoteSessionCapabilities = remoteSessionCapabilities;

            // If device supports notifications and does not contain necessary modules, add them automatically
            if (remoteSessionCapabilities.containsNonModuleCapability(CapabilityURN.NOTIFICATION)) {
                // FIXME: mutable collection modification!
                deviceSources.getRequiredSourcesQName().addAll(List.of(
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                        .$YangModuleInfoImpl.getInstance().getName(),
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                        .$YangModuleInfoImpl.getInstance().getName())
                );
            }

            requiredSources = deviceSources.getRequiredSources();
            final Collection<SourceIdentifier> missingSources = filterMissingSources(requiredSources);

            addUnresolvedCapabilities(getQNameFromSourceIdentifiers(missingSources),
                UnavailableCapability.FailureReason.MissingSource);
            requiredSources.removeAll(missingSources);
        }

        ListenableFuture<SchemaResult> startResolution() {
            trySetupSchema();
            return resultFuture;
        }

        @Override
        public void onSuccess(final EffectiveModelContext result) {
            LOG.debug("{}: Schema context built successfully from {}", id, requiredSources);

            final Collection<QName> filteredQNames = Sets.difference(deviceSources.getRequiredSourcesQName(),
                    unresolvedCapabilites.keySet());
            resolvedCapabilities.addAll(filteredQNames.stream()
                .map(capability -> new AvailableCapabilityBuilder()
                    .setCapability(capability.toString())
                    .setCapabilityOrigin(remoteSessionCapabilities.capabilityOrigin(capability))
                    .build())
                .collect(Collectors.toList()));

            nonModuleBasedCapabilities.addAll(remoteSessionCapabilities.nonModuleCaps().keySet().stream()
                .map(capability -> new AvailableCapabilityBuilder()
                    .setCapability(capability)
                    .setCapabilityOrigin(remoteSessionCapabilities.capabilityOrigin(capability))
                    .build())
                .collect(Collectors.toList()));


            resultFuture.set(new SchemaResult(new NetconfDeviceCapabilities(ImmutableMap.copyOf(unresolvedCapabilites),
                ImmutableSet.copyOf(resolvedCapabilities), ImmutableSet.copyOf(nonModuleBasedCapabilities)), result));
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

        private List<SourceIdentifier> filterMissingSources(final Collection<SourceIdentifier> origSources) {
            return origSources.parallelStream().filter(sourceIdentifier -> {
                try {
                    schemaRepository.getSchemaSource(sourceIdentifier, YangTextSchemaSource.class).get();
                    return false;
                } catch (InterruptedException | ExecutionException e) {
                    return true;
                }
            }).collect(Collectors.toList());
        }

        private void addUnresolvedCapabilities(final Collection<QName> capabilities, final FailureReason reason) {
            for (QName s : capabilities) {
                unresolvedCapabilites.put(s, reason);
            }
        }

        private List<SourceIdentifier> handleMissingSchemaSourceException(
                final MissingSchemaSourceException exception) {
            // In case source missing, try without it
            final SourceIdentifier missingSource = exception.getSourceId();
            LOG.warn("{}: Unable to build schema context, missing source {}, will reattempt without it",
                id, missingSource);
            LOG.debug("{}: Unable to build schema context, missing source {}, will reattempt without it",
                id, missingSource, exception);
            final var qNameOfMissingSource = getQNameFromSourceIdentifiers(Sets.newHashSet(missingSource));
            if (!qNameOfMissingSource.isEmpty()) {
                addUnresolvedCapabilities(qNameOfMissingSource, UnavailableCapability.FailureReason.MissingSource);
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
                addUnresolvedCapabilities(getQNameFromSourceIdentifiers(List.of(failedSourceId)),
                        UnavailableCapability.FailureReason.UnableToResolve);
                return stripUnavailableSource(resolutionException.getFailedSource());
            }
            // unsatisfied imports
            addUnresolvedCapabilities(
                getQNameFromSourceIdentifiers(resolutionException.getUnsatisfiedImports().keySet()),
                UnavailableCapability.FailureReason.UnableToResolve);
            LOG.warn("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
                id, resolutionException.getUnsatisfiedImports());
            LOG.debug("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
                id, resolutionException.getUnsatisfiedImports(), resolutionException);
            return resolutionException.getResolvedSources();
        }

        private List<SourceIdentifier> stripUnavailableSource(final SourceIdentifier sourceIdToRemove) {
            final var tmp = new ArrayList<>(requiredSources);
            checkState(tmp.remove(sourceIdToRemove), "%s: Trying to remove %s from %s failed", id, sourceIdToRemove,
                requiredSources);
            return tmp;
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
                if (!qname.getLocalName().equals(identifier.name().getLocalName())) {
                    continue;
                }

                if (Objects.equals(identifier.revision(), qname.getRevision().orElse(null))) {
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
