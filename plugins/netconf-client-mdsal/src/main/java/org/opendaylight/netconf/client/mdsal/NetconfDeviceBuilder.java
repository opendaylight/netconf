/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;
import org.opendaylight.netconf.client.mdsal.NetconfDevice.SchemaResourcesDTO;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;

public class NetconfDeviceBuilder {
    private boolean reconnectOnSchemasChange;
    private SchemaResourcesDTO schemaResourcesDTO;
    private RemoteDeviceId id;
    private RemoteDeviceHandler salFacade;
    private Executor globalProcessingExecutor;
    private DeviceActionFactory deviceActionFactory;
    private BaseNetconfSchemas baseSchemas;

    public NetconfDeviceBuilder setReconnectOnSchemasChange(final boolean reconnectOnSchemasChange) {
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        return this;
    }

    public NetconfDeviceBuilder setId(final RemoteDeviceId id) {
        this.id = id;
        return this;
    }

    public NetconfDeviceBuilder setSchemaResourcesDTO(final SchemaResourcesDTO schemaResourcesDTO) {
        this.schemaResourcesDTO = schemaResourcesDTO;
        return this;
    }

    public NetconfDeviceBuilder setSalFacade(final RemoteDeviceHandler salFacade) {
        this.salFacade = salFacade;
        return this;
    }

    public NetconfDeviceBuilder setGlobalProcessingExecutor(final Executor globalProcessingExecutor) {
        this.globalProcessingExecutor = globalProcessingExecutor;
        return this;
    }

    public NetconfDeviceBuilder setDeviceActionFactory(final DeviceActionFactory deviceActionFactory) {
        this.deviceActionFactory = deviceActionFactory;
        return this;
    }

    public NetconfDeviceBuilder setBaseSchemas(final BaseNetconfSchemas baseSchemas) {
        this.baseSchemas = requireNonNull(baseSchemas);
        return this;
    }

    public NetconfDevice build() {
        validation();
        return new NetconfDevice(schemaResourcesDTO, baseSchemas, id, salFacade,
            globalProcessingExecutor, reconnectOnSchemasChange, deviceActionFactory);
    }

    private void validation() {
        requireNonNull(baseSchemas, "BaseSchemas is not initialized");
        requireNonNull(id, "RemoteDeviceId is not initialized");
        requireNonNull(salFacade, "RemoteDeviceHandler is not initialized");
        requireNonNull(globalProcessingExecutor, "ExecutorService is not initialized");
        requireNonNull(schemaResourcesDTO, "SchemaResourceDTO is not initialized");
    }
}
