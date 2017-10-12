/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.controller.config.util.capability.YangModuleCapability;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MdsalNetconfOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MdsalNetconfOperationServiceFactory.class);

    private final DOMDataBroker dataBroker;
    private final DOMRpcService rpcService;

    private final CurrentSchemaContext currentSchemaContext;
    private final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;

    public MdsalNetconfOperationServiceFactory(
            final SchemaService schemaService,
            final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency,
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener,
            final DOMDataBroker dataBroker,
            final DOMRpcService rpcService) {

        this.dataBroker = dataBroker;
        this.rpcService = rpcService;

        this.rootSchemaSourceProviderDependency = rootSchemaSourceProviderDependency;
        this.currentSchemaContext = new CurrentSchemaContext(Preconditions.checkNotNull(schemaService),
                rootSchemaSourceProviderDependency);
        this.netconfOperationServiceFactoryListener = netconfOperationServiceFactoryListener;
        this.netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Override
    public MdsalNetconfOperationService createService(final String netconfSessionIdForReporting) {
        Preconditions.checkState(dataBroker != null, "MD-SAL provider not yet initialized");
        return new MdsalNetconfOperationService(currentSchemaContext, netconfSessionIdForReporting, dataBroker,
                rpcService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void close() {
        try {
            currentSchemaContext.close();
            if (netconfOperationServiceFactoryListener != null) {
                netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
            }
        } catch (Exception e) {
            LOG.error("Failed to close resources correctly - ignore", e);
        }
    }

    @Override
    public Set<Capability> getCapabilities() {
        return transformCapabilities(currentSchemaContext.getCurrentContext(), rootSchemaSourceProviderDependency);
    }

    static Set<Capability> transformCapabilities(
            final SchemaContext currentContext,
            final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency) {
        final Set<Capability> capabilities = new HashSet<>();

        // Added by netconf-impl by default
        // capabilities.add(new BasicCapability("urn:ietf:params:netconf:capability:candidate:1.0"));

        final Set<Module> modules = currentContext.getModules();
        for (final Module module : modules) {
            Optional<YangModuleCapability> cap = moduleToCapability(module, rootSchemaSourceProviderDependency);
            if (cap.isPresent()) {
                capabilities.add(cap.get());
            }
            for (final Module submodule : module.getSubmodules()) {
                cap = moduleToCapability(submodule, rootSchemaSourceProviderDependency);
                if (cap.isPresent()) {
                    capabilities.add(cap.get());
                }
            }
        }

        return capabilities;
    }

    private static Optional<YangModuleCapability> moduleToCapability(
            final Module module, final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProviderDependency) {

        final SourceIdentifier moduleSourceIdentifier = RevisionSourceIdentifier.create(module.getName(),
                SimpleDateFormatUtil.DEFAULT_DATE_REV == module.getRevision() ? Optional.empty() :
                        Optional.of(module.getQNameModule().getFormattedRevision()));

        InputStream sourceStream = null;
        String source;
        try {
            sourceStream = rootSchemaSourceProviderDependency.getSource(moduleSourceIdentifier).get().openStream();
            source = CharStreams.toString(new InputStreamReader(sourceStream, StandardCharsets.UTF_8));
        } catch (ExecutionException | InterruptedException | IOException e) {
            LOG.warn("Ignoring source for module {}. Unable to read content", moduleSourceIdentifier, e);
            source = null;
        }

        try {
            if (sourceStream != null) {
                sourceStream.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing yang source stream {}. Ignoring", moduleSourceIdentifier, e);
        }

        if (source != null) {
            return Optional.of(new YangModuleCapability(module, source));
        }

        LOG.warn("Missing source for module {}. This module will not be available from netconf server",
            moduleSourceIdentifier);
        return Optional.empty();
    }

    @Override
    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        return currentSchemaContext.registerCapabilityListener(listener);
    }
}
