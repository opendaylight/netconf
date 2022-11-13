/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;

public class NetconfDeviceBuilder {

    private boolean reconnectOnSchemasChange;
    private NetconfDevice.SchemaResourcesDTO schemaResourcesDTO;
    private RemoteDeviceId id;
    private RemoteDeviceHandler salFacade;
    private ListeningExecutorService globalProcessingExecutor;
    private DeviceActionFactory deviceActionFactory;
    private NetconfNode node;
    private EventExecutor eventExecutor;
    private NetconfNodeAugmentedOptional nodeOptional;
    private BaseNetconfSchemas baseSchemas;

    public NetconfDeviceBuilder() {
    }

    public NetconfDeviceBuilder setReconnectOnSchemasChange(final boolean reconnectOnSchemasChange) {
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        return this;
    }

    public NetconfDeviceBuilder setId(final RemoteDeviceId id) {
        this.id = id;
        return this;
    }

    public NetconfDeviceBuilder setSchemaResourcesDTO(final NetconfDevice.SchemaResourcesDTO schemaResourcesDTO) {
        this.schemaResourcesDTO = schemaResourcesDTO;
        return this;
    }

    public NetconfDeviceBuilder setSalFacade(final RemoteDeviceHandler salFacade) {
        this.salFacade = salFacade;
        return this;
    }

    public NetconfDeviceBuilder setGlobalProcessingExecutor(final ListeningExecutorService globalProcessingExecutor) {
        this.globalProcessingExecutor = globalProcessingExecutor;
        return this;
    }

    public NetconfDeviceBuilder setDeviceActionFactory(final DeviceActionFactory deviceActionFactory) {
        this.deviceActionFactory = deviceActionFactory;
        return this;
    }

    public NetconfDeviceBuilder setNode(final NetconfNode node) {
        this.node = node;
        return this;
    }

    public NetconfDeviceBuilder setEventExecutor(final EventExecutor eventExecutor) {
        this.eventExecutor = eventExecutor;
        return this;
    }

    public NetconfDeviceBuilder setNodeOptional(final NetconfNodeAugmentedOptional nodeOptional) {
        this.nodeOptional = nodeOptional;
        return this;
    }

    public NetconfDeviceBuilder setBaseSchemas(final BaseNetconfSchemas baseSchemas) {
        this.baseSchemas = requireNonNull(baseSchemas);
        return this;
    }

    public NetconfDevice build() {
        validation();
        return new NetconfDevice(schemaResourcesDTO, baseSchemas, id, salFacade,
            globalProcessingExecutor, reconnectOnSchemasChange, deviceActionFactory, node,
            eventExecutor, nodeOptional);
    }

    private void validation() {
        requireNonNull(baseSchemas, "BaseSchemas is not initialized");
        requireNonNull(id, "RemoteDeviceId is not initialized");
        requireNonNull(salFacade, "RemoteDeviceHandler is not initialized");
        requireNonNull(globalProcessingExecutor, "ExecutorService is not initialized");
        requireNonNull(schemaResourcesDTO, "SchemaResourceDTO is not initialized");
    }
}
