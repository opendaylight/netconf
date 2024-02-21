/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashSet;
import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.LibraryModulesSchemas;
import org.opendaylight.netconf.client.mdsal.LibrarySchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.MonitoringSchemaSourceProvider;
import org.opendaylight.netconf.client.mdsal.NetconfStateSchemasResolverImpl;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@VisibleForTesting
public final class DefaultDeviceNetconfSchemaProvider implements DeviceNetconfSchemaProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceNetconfSchemaProvider.class);

    // FIXME: resolver seems to be a useless indirection
    private final NetconfDeviceSchemasResolver resolver;
    private final @NonNull EffectiveModelContextFactory contextFactory;
    private final @NonNull SchemaSourceRegistry registry;
    private final @NonNull SchemaRepository repository;
    // FIXME: private final Executor processingExecutor;

    DefaultDeviceNetconfSchemaProvider(final SharedSchemaRepository repository) {
        this(repository, repository,
            repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault()),
            new NetconfStateSchemasResolverImpl());
    }

    @VisibleForTesting
    public DefaultDeviceNetconfSchemaProvider(final SchemaSourceRegistry registry, final SchemaRepository repository,
            final EffectiveModelContextFactory contextFactory, final NetconfDeviceSchemasResolver resolver) {
        this.registry = requireNonNull(registry);
        this.repository = requireNonNull(repository);
        this.contextFactory = requireNonNull(contextFactory);
        this.resolver = requireNonNull(resolver);
    }

    @Override
    public ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(final RemoteDeviceId deviceId,
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
        return Futures.transformAsync(sourceResolverFuture, deviceSources -> {
            LOG.debug("{}: Resolved device sources to {}", deviceId, deviceSources);

            // Register all sources with repository and start resolution
            final var registrations = deviceSources.register(registry);
            final var future = new SchemaSetup(repository, contextFactory, deviceId, deviceSources, sessionPreferences)
                .startResolution();

            // Unregister sources once resolution is complete
            future.addListener(() -> registrations.forEach(Registration::close), processingExecutor);

            return future;
        }, processingExecutor);
    }

    @Deprecated
    @Override
    public SchemaRepository repository() {
        return repository;
    }

    @Deprecated
    @Override
    public SchemaSourceRegistry registry() {
        return registry;
    }

    @Deprecated
    @Override
    public EffectiveModelContextFactory contextFactory() {
        return contextFactory;
    }
}
