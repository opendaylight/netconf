/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.connect.netconf.NetconfDevice.LOG;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.Callable;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.NetconfRemoteSchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.YangLibrarySchemaYangSourceProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

/**
 * Schema building callable.
 */
final class DeviceSourcesResolver implements Callable<DeviceSources> {
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
        final NetconfDeviceSchemas availableSchemas = stateSchemasResolver.resolve(deviceRpc, remoteSessionCapabilities,
            id, baseSchema.getSchemaContext());
        LOG.debug("{}: Schemas exposed by ietf-netconf-monitoring: {}", id,
            availableSchemas.getAvailableYangSchemasQNames());

        final Set<QName> requiredSources = Sets.newHashSet(remoteSessionCapabilities.getModuleBasedCaps());
        final Set<QName> providedSources = availableSchemas.getAvailableYangSchemasQNames();

        final Set<QName> requiredSourcesNotProvided = Sets.difference(requiredSources, providedSources);
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
        final Set<QName> providedSourcesNotRequired = Sets.difference(providedSources, requiredSources);
        if (!providedSourcesNotRequired.isEmpty()) {
            LOG.warn("{}: Netconf device provides additional yang models not reported in "
                    + "hello message capabilities: {}", id, providedSourcesNotRequired);
            LOG.warn("{}: Adding provided but not required sources as required to prevent failures", id);
            LOG.debug("{}: Netconf device reported in hello: {}", id, requiredSources);
            requiredSources.addAll(providedSourcesNotRequired);
        }

        final SchemaSourceProvider<YangTextSchemaSource> sourceProvider;
        if (availableSchemas instanceof LibraryModulesSchemas) {
            sourceProvider = new YangLibrarySchemaYangSourceProvider(id,
                    ((LibraryModulesSchemas) availableSchemas).getAvailableModels());
        } else {
            sourceProvider = new NetconfRemoteSchemaYangSourceProvider(id, deviceRpc);
        }

        return new DeviceSources(requiredSources, providedSources, sourceProvider);
    }
}