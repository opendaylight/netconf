/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.EmptySchemaContextException;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.unavailable.capabilities.UnavailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema builder that tries to build schema context from provided sources or biggest subset of it.
 */
final class SchemaSetup implements FutureCallback<EffectiveModelContext> {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaSetup.class);

    private final SettableFuture<DeviceNetconfSchema> resultFuture = SettableFuture.create();
    private final Set<AvailableCapability> nonModuleBasedCapabilities = new HashSet<>();
    private final Map<QName, FailureReason> unresolvedCapabilites = new HashMap<>();
    private final Set<AvailableCapability> resolvedCapabilities = new HashSet<>();
    private final RemoteDeviceId deviceId;
    private final NetconfDeviceSchemas deviceSchemas;
    private final Set<QName> deviceRequiredSources;
    private final NetconfSessionPreferences sessionPreferences;
    private final SchemaRepository repository;
    private final EffectiveModelContextFactory contextFactory;

    private Collection<SourceIdentifier> requiredSources;

    SchemaSetup(final SchemaRepository repository, final EffectiveModelContextFactory contextFactory,
            final RemoteDeviceId deviceId, final NetconfDeviceSchemas deviceSchemas,
            final NetconfSessionPreferences sessionPreferences) {
        this.repository = requireNonNull(repository);
        this.contextFactory = requireNonNull(contextFactory);
        this.deviceId = requireNonNull(deviceId);
        this.deviceSchemas = requireNonNull(deviceSchemas);
        this.sessionPreferences = requireNonNull(sessionPreferences);

        // If device supports notifications and does not contain necessary modules, add them automatically
        deviceRequiredSources = new HashSet<>(deviceSchemas.requiredSources());
        if (sessionPreferences.containsNonModuleCapability(CapabilityURN.NOTIFICATION)) {
            deviceRequiredSources.add(
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714
                    .YangModuleInfoImpl.getInstance().getName());
            deviceRequiredSources.add(
                org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715
                    .YangModuleInfoImpl.getInstance().getName());
        }

        requiredSources = deviceRequiredSources.stream()
            .map(qname -> new SourceIdentifier(qname.getLocalName(), qname.getModule().revision()))
            .collect(Collectors.toList());

        final var missingSources = filterMissingSources(requiredSources);
        addUnresolvedCapabilities(getQNameFromSourceIdentifiers(missingSources),
            UnavailableCapability.FailureReason.MissingSource);
        requiredSources.removeAll(missingSources);
    }

    ListenableFuture<DeviceNetconfSchema> startResolution() {
        trySetupSchema();
        return resultFuture;
    }

    @Override
    public void onSuccess(final EffectiveModelContext result) {
        LOG.debug("{}: Schema context built successfully from {}", deviceId, requiredSources);

        final var filteredQNames = Sets.difference(deviceRequiredSources, unresolvedCapabilites.keySet());
        resolvedCapabilities.addAll(filteredQNames.stream()
            .map(capability -> new AvailableCapabilityBuilder()
                .setCapability(capability.toString())
                .setCapabilityOrigin(sessionPreferences.capabilityOrigin(capability))
                .build())
            .collect(Collectors.toList()));

        nonModuleBasedCapabilities.addAll(sessionPreferences.nonModuleCaps().keySet().stream()
            .map(capability -> new AvailableCapabilityBuilder()
                .setCapability(capability)
                .setCapabilityOrigin(sessionPreferences.capabilityOrigin(capability))
                .build())
            .collect(Collectors.toList()));

        resultFuture.set(new DeviceNetconfSchema(new NetconfDeviceCapabilities(
            ImmutableMap.copyOf(unresolvedCapabilites), ImmutableSet.copyOf(resolvedCapabilities),
            ImmutableSet.copyOf(nonModuleBasedCapabilities)), result));
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
            LOG.trace("{}: Trying to build schema context from {}", deviceId, requiredSources);
            Futures.addCallback(contextFactory.createEffectiveModelContext(requiredSources), this,
                MoreExecutors.directExecutor());
        } else {
            LOG.debug("{}: no more sources for schema context", deviceId);
            resultFuture.setException(
                new EmptySchemaContextException(deviceId + ": No more sources for schema context"));
        }
    }

    private List<SourceIdentifier> filterMissingSources(final Collection<SourceIdentifier> origSources) {
        return origSources.parallelStream()
            .filter(sourceId -> {
                try {
                    repository.getSchemaSource(sourceId, YangTextSource.class).get();
                    return false;
                } catch (InterruptedException | ExecutionException e) {
                    LOG.debug("Failed to acquire source {}", sourceId, e);
                    return true;
                }
            })
            .collect(Collectors.toList());
    }

    private void addUnresolvedCapabilities(final Collection<QName> capabilities, final FailureReason reason) {
        for (QName s : capabilities) {
            unresolvedCapabilites.put(s, reason);
        }
    }

    private List<SourceIdentifier> handleMissingSchemaSourceException(
            final MissingSchemaSourceException exception) {
        // In case source missing, try without it
        final SourceIdentifier missingSource = exception.sourceId();
        LOG.warn("{}: Unable to build schema context, missing source {}, will reattempt without it",
            deviceId, missingSource);
        LOG.debug("{}: Unable to build schema context, missing source {}, will reattempt without it",
            deviceId, missingSource, exception);
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
        final var failedSourceId = resolutionException.sourceId();
        if (failedSourceId != null) {
            // flawed model - exclude it
            LOG.warn("{}: Unable to build schema context, failed to resolve source {}, will reattempt without it",
                deviceId, failedSourceId);
            LOG.warn("{}: Unable to build schema context, failed to resolve source {}, will reattempt without it",
                deviceId, failedSourceId, resolutionException);
            addUnresolvedCapabilities(getQNameFromSourceIdentifiers(List.of(failedSourceId)),
                    UnavailableCapability.FailureReason.UnableToResolve);
            return stripUnavailableSource(failedSourceId);
        }
        // unsatisfied imports
        addUnresolvedCapabilities(
            getQNameFromSourceIdentifiers(resolutionException.getUnsatisfiedImports().keySet()),
            UnavailableCapability.FailureReason.UnableToResolve);
        LOG.warn("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
            deviceId, resolutionException.getUnsatisfiedImports());
        LOG.debug("{}: Unable to build schema context, unsatisfied imports {}, will reattempt with resolved only",
            deviceId, resolutionException.getUnsatisfiedImports(), resolutionException);
        return resolutionException.getResolvedSources();
    }

    private List<SourceIdentifier> stripUnavailableSource(final SourceIdentifier sourceIdToRemove) {
        final var tmp = new ArrayList<>(requiredSources);
        checkState(tmp.remove(sourceIdToRemove), "%s: Trying to remove %s from %s failed", deviceId, sourceIdToRemove,
            requiredSources);
        return tmp;
    }

    private Collection<QName> getQNameFromSourceIdentifiers(final Collection<SourceIdentifier> identifiers) {
        final Collection<QName> qNames = Collections2.transform(identifiers, this::getQNameFromSourceIdentifier);

        if (qNames.isEmpty()) {
            LOG.debug("{}: Unable to map any source identifiers to a capability reported by device : {}", deviceId,
                    identifiers);
        }
        return Collections2.filter(qNames, Predicates.notNull());
    }

    private QName getQNameFromSourceIdentifier(final SourceIdentifier identifier) {
        // Required sources are all required and provided merged in DeviceSourcesResolver
        for (final QName qname : deviceRequiredSources) {
            if (!qname.getLocalName().equals(identifier.name().getLocalName())) {
                continue;
            }

            if (Objects.equals(identifier.revision(), qname.getModule().revision())) {
                return qname;
            }
        }
        LOG.warn("Unable to map identifier to a devices reported capability: {} Available: {}", identifier,
            deviceRequiredSources);
        // return null since we cannot find the QName,
        // this capability will be removed from required sources and not reported as unresolved-capability
        return null;
    }
}
