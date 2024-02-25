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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
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
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@VisibleForTesting
public final class DefaultDeviceNetconfSchemaProvider implements DeviceNetconfSchemaProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceNetconfSchemaProvider.class);

    // FIXME: resolver seems to be a useless indirection
    private final NetconfDeviceSchemasResolver resolver = new NetconfStateSchemasResolverImpl();
    private final @NonNull EffectiveModelContextFactory contextFactory;
    private final @NonNull SchemaSourceRegistry registry;
    private final @NonNull SchemaRepository repository;
    private final Executor processingExecutor;

    DefaultDeviceNetconfSchemaProvider(final SharedSchemaRepository repository, final Executor processingExecutor) {
        this(processingExecutor, repository, repository,
            repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault()));
    }

    @VisibleForTesting
    public DefaultDeviceNetconfSchemaProvider(final Executor processingExecutor, final SchemaSourceRegistry registry,
            final SchemaRepository repository, final EffectiveModelContextFactory contextFactory) {
        this.processingExecutor = requireNonNull(processingExecutor);
        this.registry = requireNonNull(registry);
        this.repository = requireNonNull(repository);
        this.contextFactory = requireNonNull(contextFactory);
    }

    @Override
    public ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfRpcService deviceRpc,
            final BaseNetconfSchema baseSchema) {
        // Acquire sources
        final var sourceResolverFuture = resolver.resolve(deviceId, sessionPreferences, deviceRpc,
            baseSchema.modelContext());

        // Set up the EffectiveModelContext for the device
        return Futures.transformAsync(sourceResolverFuture, deviceSources -> {
            LOG.debug("{}: Resolved device sources to {}", deviceId, deviceSources);

            // Register all sources with repository and start resolution
            final var registrations = deviceSources.providedSources().stream()
                .flatMap(sources -> sources.registerWith(registry, Costs.REMOTE_IO.getValue()))
                .collect(Collectors.toUnmodifiableList());
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
