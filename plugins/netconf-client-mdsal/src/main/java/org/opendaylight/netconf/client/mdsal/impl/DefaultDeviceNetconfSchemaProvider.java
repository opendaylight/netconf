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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.spi.source.URLYangTextSource;
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
    // FIXME: private final Executor processingExecutor;

    DefaultDeviceNetconfSchemaProvider(final SharedSchemaRepository repository) {
        this(repository, repository,
            repository.createEffectiveModelContextFactory(SchemaContextFactoryConfiguration.getDefault()));
    }

    @VisibleForTesting
    public DefaultDeviceNetconfSchemaProvider(final SchemaSourceRegistry registry, final SchemaRepository repository,
            final EffectiveModelContextFactory contextFactory) {
        this.registry = requireNonNull(registry);
        this.repository = requireNonNull(repository);
        this.contextFactory = requireNonNull(contextFactory);
    }

    @Override
    public ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfRpcService deviceRpc,
            final BaseNetconfSchema baseSchema, final Executor processingExecutor) {
        registerNetconfBase2011(registry, repository);
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

    private static void registerNetconfBase2011(final SchemaSourceRegistry registry, final SchemaRepository repo) {
        final var id2011 = new SourceIdentifier("ietf-netconf", Revision.of("2011-06-01"));
        if (hasTextSource(repo, id2011)) {
            // already available (cached/registered)
            return;
        }

        // anchor lookup on the module’s own class to cross the OSGi bundle boundary
        final var anchor = org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601
            .YangModuleInfoImpl.class;
        final var url = anchor.getResource("/META-INF/yang/ietf-netconf@2011-06-01.yang");
        if (url == null) {
            LOG.debug("Builtin YANG not found in {} bundle: {}", anchor.getPackageName(),
                "ietf-netconf@2011-06-01.yang");
            return;
        }

        final var source = new URLYangTextSource(url);

        final SchemaSourceProvider<YangTextSource> provider = requested -> id2011.equals(requested)
                ? Futures.immediateFuture(source)
                : Futures.immediateFailedFuture(new IllegalArgumentException("Unsupported id " + requested));

        registry.registerSchemaSource(provider, PotentialSchemaSource.create(id2011, YangTextSource.class,
            PotentialSchemaSource.Costs.LOCAL_IO.getValue()));

        LOG.info("Registered builtin ietf-netconf@2011-06-01 from {}", url);
    }

    private static boolean hasTextSource(final SchemaRepository repo, final SourceIdentifier id) {
        try {
            final var future = repo.getSchemaSource(id, YangTextSource.class);
            return future.isDone() && !future.isCancelled() && future.get(1, TimeUnit.MILLISECONDS) != null;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
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
