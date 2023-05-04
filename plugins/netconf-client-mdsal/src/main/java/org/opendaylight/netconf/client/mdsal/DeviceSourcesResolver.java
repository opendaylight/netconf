/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.concurrent.Callable;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.BaseSchema;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema building callable.
 */
final class DeviceSourcesResolver implements Callable<DeviceSources> {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceSourcesResolver.class);

    private final NetconfSessionPreferences remoteSessionCapabilities;
    private final NetconfDeviceSchemasResolver stateSchemasResolver;
    private final NetconfDeviceRpc deviceRpc;
    private final BaseSchema baseSchema;
    private final RemoteDeviceId id;

    DeviceSourcesResolver(final RemoteDeviceId id, final BaseSchema baseSchema, final NetconfDeviceRpc deviceRpc,
            final NetconfSessionPreferences remoteSessionCapabilities,
            final NetconfDeviceSchemasResolver stateSchemasResolver) {
        this.id = requireNonNull(id);
        this.baseSchema = requireNonNull(baseSchema);
        this.deviceRpc = requireNonNull(deviceRpc);
        this.remoteSessionCapabilities = requireNonNull(remoteSessionCapabilities);
        this.stateSchemasResolver = requireNonNull(stateSchemasResolver);
    }

    @Override
    public DeviceSources call() {
        final var availableSchemas = stateSchemasResolver.resolve(deviceRpc, remoteSessionCapabilities, id,
            baseSchema.getEffectiveModelContext());
        LOG.debug("{}: Schemas exposed by ietf-netconf-monitoring: {}", id,
            availableSchemas.getAvailableYangSchemasQNames());

        final var requiredSources = new HashSet<>(remoteSessionCapabilities.moduleBasedCaps().keySet());
        final var providedSources = availableSchemas.getAvailableYangSchemasQNames();
        final var requiredSourcesNotProvided = Sets.difference(requiredSources, providedSources);
        if (!requiredSourcesNotProvided.isEmpty()) {
            LOG.warn("{}: Netconf device does not provide all yang models reported in hello message capabilities,"
                    + " required but not provided: {}", id, requiredSourcesNotProvided);
            LOG.warn("{}: Attempting to build schema context from required sources", id);
        }

        // Here all the sources reported in netconf monitoring are merged with those reported in hello.
        // It is necessary to perform this since submodules are not mentioned in hello but still required.
        // This clashes with the option of a user to specify supported yang models manually in configuration
        // for netconf-connector and as a result one is not able to fully override yang models of a device.
        // It is only possible to add additional models.
        final var providedSourcesNotRequired = Sets.difference(providedSources, requiredSources);
        if (!providedSourcesNotRequired.isEmpty()) {
            LOG.warn("{}: Netconf device provides additional yang models not reported in "
                    + "hello message capabilities: {}", id, providedSourcesNotRequired);
            LOG.warn("{}: Adding provided but not required sources as required to prevent failures", id);
            LOG.debug("{}: Netconf device reported in hello: {}", id, requiredSources);
            requiredSources.addAll(providedSourcesNotRequired);
        }

        final var sourceProvider = availableSchemas instanceof LibraryModulesSchemas libraryModule
            ? new LibrarySchemaSourceProvider(id, libraryModule.getAvailableModels())
                : new MonitoringSchemaSourceProvider(id, deviceRpc);
        return new DeviceSources(requiredSources, providedSources, sourceProvider);
    }
}