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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.ProvidedSources;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.YangModuleInfoImpl;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactoryConfiguration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource.Costs;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.opendaylight.yangtools.yang.model.spi.source.DelegatedYangTextSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@VisibleForTesting
public final class DefaultDeviceNetconfSchemaProvider implements DeviceNetconfSchemaProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultDeviceNetconfSchemaProvider.class);

    /**
     * The RFC6241-standard QName of {@code ietf-netconf-yang}.
     */
    private static final @NonNull QName RFC6241_IETF_NETCONF = YangModuleInfoImpl.getInstance().getName();
    /**
     * The QName of {@code ietf-netconf.yang} as revision used by libnetconf2/sysrepon/IOS-XR and perhaps others.
     * The delta is just addition of NACM extension instantiations. The data semantics remains the same.
    */
    private static final @NonNull QName RFC6536_IETF_NETCONF = RFC6241_IETF_NETCONF.unbind()
        .bindTo(QNameModule.of(RFC6241_IETF_NETCONF.getNamespace(), Revision.of("2013-09-29")))
        .intern();

    private static final @NonNull SourceIdentifier RFC6241_SOURCEID = SourceIdentifier.ofQName(RFC6241_IETF_NETCONF);
    private static final @NonNull ListenableFuture<@NonNull YangTextSource> RFC6241_SOURCE =
        Futures.immediateFuture(new DelegatedYangTextSource(RFC6241_SOURCEID,
            YangModuleInfoImpl.getInstance().getYangTextCharSource()));

    private static final @NonNull ProvidedSources<?> RFC6241_PROVIDED_SOURCES =
        new ProvidedSources<>(YangTextSource.class, sourceId -> {
            if (RFC6241_SOURCEID.name().equals(sourceId.name())) {
                final var revision = sourceId.revision();
                if (revision == null || revision.equals(RFC6241_SOURCEID.revision())) {
                    return RFC6241_SOURCE;
                }
            }
            return Futures.immediateFailedFuture(new MissingSchemaSourceException(sourceId, "Source is not available"));
        }, Set.of(RFC6241_IETF_NETCONF));

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
        return Futures.transformAsync(
            // Acquire sources
            resolver.resolve(deviceId, sessionPreferences, deviceRpc, baseSchema.modelContext()),
            // Set up the EffectiveModelContext for the device
            deviceSources -> deviceNetconfSchemaFor(deviceId, sessionPreferences, deviceSources, processingExecutor),
            processingExecutor);
    }

    private ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(final RemoteDeviceId deviceId,
            final NetconfSessionPreferences sessionPreferences, final NetconfDeviceSchemas origDeviceSources,
            final Executor processingExecutor) {
        LOG.debug("{}: Resolved device sources to {}", deviceId, origDeviceSources);

        final var deviceSources = applyQuirks(deviceId, origDeviceSources);

        var requiredSources = deviceSources.requiredSources();

        // If device supports notifications and does not contain necessary modules, add them automatically
        if (sessionPreferences.containsNonModuleCapability(CapabilityURN.NOTIFICATION)) {
            requiredSources = new HashSet<>(requiredSources);
            requiredSources.add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                    .YangModuleInfoImpl.getInstance().getName());
            requiredSources.add(org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                    .YangModuleInfoImpl.getInstance().getName());
        }

        // Register all sources with repository and start resolution
        final var registrations = deviceSources.providedSources().stream()
            .flatMap(sources -> sources.registerWith(registry, Costs.REMOTE_IO.getValue()))
            .collect(Collectors.toUnmodifiableList());
        final var future = SchemaSetup.resolve(repository, contextFactory, deviceId, requiredSources);

        // Unregister sources once resolution is complete
        future.addListener(() -> registrations.forEach(Registration::close), processingExecutor);

        final var finalRequiredSources = requiredSources;
        return Futures.transform(future,
            result -> new DeviceNetconfSchema(result.extractCapabilities(finalRequiredSources, sessionPreferences),
                result.modelContext()),
            processingExecutor);
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

    @NonNullByDefault
    private static NetconfDeviceSchemas applyQuirks(final RemoteDeviceId deviceId,
            final NetconfDeviceSchemas deviceSchemas) {
        return deviceSchemas.requiredSources().contains(RFC6536_IETF_NETCONF)
            ? quirkIetfNetconf20130929(deviceId, deviceSchemas)
            : deviceSchemas;
    }

    // Apply the 2013-09-29 -> 2011-06-01 schema downgrade. The data semantics is the same and this quirk allows us
    // to simplify a lot of other logic just by relying on RFC6241.
    @NonNullByDefault
    private static NetconfDeviceSchemas quirkIetfNetconf20130929(final RemoteDeviceId deviceId,
            final NetconfDeviceSchemas deviceSchemas) {
        LOG.debug("{}: applying ietf-netconf@2013-09-29 quirk", deviceId);

        return new NetconfDeviceSchemas(
            deviceSchemas.requiredSources().stream()
                .map(qname -> RFC6536_IETF_NETCONF.equals(qname) ? RFC6241_IETF_NETCONF : qname)
                .collect(Collectors.toUnmodifiableSet()),
            deviceSchemas.features(),
            deviceSchemas.librarySources(),
            Stream.concat(Stream.of(RFC6241_PROVIDED_SOURCES), deviceSchemas.providedSources().stream())
                .collect(Collectors.toUnmodifiableList()));
    }
}
