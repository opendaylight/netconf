/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.monitoring.YangModuleCapability;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: this is a very bad name, come up with something better
public final class CurrentSchemaContext implements DatabindProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CurrentSchemaContext.class);

    private final AtomicReference<NetconfServerStrategy> currentStrategy = new AtomicReference<>();
    private final Set<CapabilityListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final YangTextSourceExtension yangTextSourceExtension;

    private final Registration registration;

    public CurrentSchemaContext(final DOMSchemaService schemaService) {
        yangTextSourceExtension = schemaService.extension(YangTextSourceExtension.class);
        registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    @NonNull NetconfServerStrategy currentStrategy() {
        return verifyNotNull(currentStrategy.get(), "Current context not received");
    }

    @Override
    public DatabindContext currentDatabind() {
        return currentStrategy().databind();
    }

    private void onModelContextUpdated(final EffectiveModelContext modelContext) {
        final var newDatabind = DatabindContext.ofModel(modelContext);
        final var newStrategy = new NetconfServerStrategy(newDatabind,
            createCapabilities(newDatabind.modelContext()));
        currentStrategy.set(newStrategy);

        // FIXME: is notifying all the listeners from this callback wise ?
        for (var listener : listeners) {
            // FIXME: so how to we handle refresh? because we should be running a delta against previous...
            listener.onCapabilitiesChanged(newStrategy.capabilities(), Set.of());
        }
    }

    @NonNullByDefault
    private ImmutableSet<Capability> createCapabilities(final EffectiveModelContext modelContext) {
        final var capabilities = new HashSet<Capability>();

        // Added by netconf-impl by default
        // capabilities.add(new BasicCapability(CapabilityURN.CANDIDATE));

        // FIXME: rework in terms of ModuleEffectiveStatement
        for (var module : modelContext.getModules()) {
            moduleToCapability(module, yangTextSourceExtension).ifPresent(capabilities::add);
            for (var submodule : module.getSubmodules()) {
                moduleToCapability(submodule, yangTextSourceExtension).ifPresent(capabilities::add);
            }
        }

        return capabilities.stream().sorted(Comparator.comparing(Capability::getCapabilityUri))
            .collect(ImmutableSet.toImmutableSet());
    }

    private static Optional<YangModuleCapability> moduleToCapability(final ModuleLike module,
            final YangTextSourceExtension yangTextSourceExtension) {
        final String moduleNamespace = module.getNamespace().toString();
        final String moduleName = module.getName();
        final String revision = module.getRevision().map(Revision::toString).orElse(null);
        final SourceIdentifier moduleSourceIdentifier = new SourceIdentifier(moduleName, revision);

        String source;
        try {
            source = yangTextSourceExtension.getYangTexttSource(moduleSourceIdentifier).get().read();
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOG.warn("Ignoring source for module {}. Unable to read content", moduleSourceIdentifier, e);
            source = null;
        }

        if (source != null) {
            return Optional.of(new YangModuleCapability(moduleNamespace, moduleName, revision, source));
        }

        LOG.warn("Missing source for module {}. This module will not be available from netconf server",
            moduleSourceIdentifier);
        return Optional.empty();
    }

    @Override
    public void close() {
        registration.close();
        listeners.clear();
        currentStrategy.set(null);
    }

    public Registration registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(currentStrategy().capabilities(), Set.of());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
