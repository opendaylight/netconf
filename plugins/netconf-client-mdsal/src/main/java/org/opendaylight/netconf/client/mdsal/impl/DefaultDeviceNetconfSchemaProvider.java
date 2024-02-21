/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil.NETCONF_GET_NODEID;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashSet;
import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.LibraryModulesSchemas;
import org.opendaylight.netconf.client.mdsal.LibrarySchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.MonitoringSchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.Get;
import org.opendaylight.yangtools.rfc8528.model.api.SchemaMountConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultDeviceNetconfSchemaProvider implements NetconfDeviceSchemaProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceNetconfSchemaProvider.class);
    private static final QName RFC8528_SCHEMA_MOUNTS_QNAME = QName.create(
        SchemaMountConstants.RFC8528_MODULE, "schema-mounts").intern();
    private static final YangInstanceIdentifier RFC8528_SCHEMA_MOUNTS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RFC8528_SCHEMA_MOUNTS_QNAME));

    // FIXME: resolver seems to be a useless indirection
    private final NetconfDeviceSchemasResolver resolver = new NetconfStateSchemasResolverImpl();
    private final SchemaRepository repository;
    private final EffectiveModelContextFactory contextFactory;
    // FIXME: private final Executor processingExecutor;

    DefaultDeviceNetconfSchemaProvider(final SchemaRepository repository) {
        this.repository = requireNonNull(repository);
        contextFactory = repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault());
    }

    @Override
    public ListenableFuture<NetconfDeviceSchema> deviceNetconfSchemaFor(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfRpcService deviceRpc,
            final BaseNetconfSchema baseSchema, final Executor processingExecutor) {

        // Acquire schemas
        final var futureSchemas = resolver.resolve(deviceRpc, sessionPreferences, deviceId, baseSchema.modelContext());

        // Convert to sources
        final var sourceResolverFuture = Futures.transform(futureSchemas, availableSchemas -> {
            final var providedSources = availableSchemas.getAvailableYangSchemasQNames();
            LOG.debug("{}: Schemas exposed by ietf-netconf-monitoring: {}", deviceId, providedSources);

            final var requiredSources = new HashSet<>(sessionPreferences.moduleBasedCaps().keySet());
            final var requiredSourcesNotProvided = Sets.difference(requiredSources, providedSources);
            if (!requiredSourcesNotProvided.isEmpty()) {
                LOG.warn("{}: Netconf device does not provide all yang models reported in hello message capabilities,"
                        + " required but not provided: {}", deviceId, requiredSourcesNotProvided);
                LOG.warn("{}: Attempting to build schema context from required sources", deviceId);
            }

            // Here all the sources reported in netconf monitoring are merged with those reported in hello.
            // It is necessary to perform this since submodules are not mentioned in hello but still required.
            // This clashes with the option of a user to specify supported yang models manually in configuration
            // for netconf-connector and as a result one is not able to fully override yang models of a device.
            // It is only possible to add additional models.
            final var providedSourcesNotRequired = Sets.difference(providedSources, requiredSources);
            if (!providedSourcesNotRequired.isEmpty()) {
                LOG.warn("{}: Netconf device provides additional yang models not reported in "
                        + "hello message capabilities: {}", deviceId, providedSourcesNotRequired);
                LOG.warn("{}: Adding provided but not required sources as required to prevent failures", deviceId);
                LOG.debug("{}: Netconf device reported in hello: {}", deviceId, requiredSources);
                requiredSources.addAll(providedSourcesNotRequired);
            }

            // FIXME: this instanceof check is quite bad
            final var sourceProvider = availableSchemas instanceof LibraryModulesSchemas libraryModule
                ? new LibrarySchemaSourceProvider(deviceId, libraryModule.getAvailableModels())
                    : new MonitoringSchemaSourceProvider(deviceId, deviceRpc);
            return new DeviceSources(requiredSources, providedSources, sourceProvider);
        }, MoreExecutors.directExecutor());

        // Set up the EffectiveModelContext for the device
        final var futureSchema = Futures.transformAsync(sourceResolverFuture,
            deviceSources -> assembleSchemaContext(deviceSources, sessionPreferences), processingExecutor);

        // Potentially acquire mount point list and interpret it
        final var netconfDeviceSchemaFuture = Futures.transformAsync(futureSchema,
            result -> Futures.transform(createMountPointContext(result.modelContext(), baseSchema, listener),
                mount -> new NetconfDeviceSchema(result.capabilities(), mount), processingExecutor),
            processingExecutor);
        schemaFuturesList = Futures.allAsList(sourceResolverFuture, futureSchema, netconfDeviceSchemaFuture);






        // TODO Auto-generated method stub
        return null;
    }

    private ListenableFuture<SchemaResult> assembleSchemaContext(final DeviceSources deviceSources,
        final NetconfSessionPreferences remoteSessionCapabilities) {
        LOG.debug("{}: Resolved device sources to {}", id, deviceSources);

        sourceRegistrations.addAll(deviceSources.register(schemaRegistry));

        return new SchemaSetup(deviceSources, remoteSessionCapabilities).startResolution();
    }

    private ListenableFuture<@NonNull MountPointContext> createMountPointContext(
            final EffectiveModelContext schemaContext, final BaseNetconfSchema baseSchema,
            final NetconfDeviceCommunicator listener) {
        final MountPointContext emptyContext = MountPointContext.of(schemaContext);
        if (schemaContext.findModule(SchemaMountConstants.RFC8528_MODULE).isEmpty()) {
            return Futures.immediateFuture(emptyContext);
        }

        // Create a temporary RPC invoker and acquire the mount point tree
        LOG.debug("{}: Acquiring available mount points", id);
        final NetconfDeviceRpc deviceRpc = new NetconfDeviceRpc(schemaContext, listener,
            new NetconfMessageTransformer(emptyContext, false, baseSchema));

        return Futures.transform(deviceRpc.domRpcService().invokeRpc(Get.QNAME, ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NETCONF_GET_NODEID)
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
}
