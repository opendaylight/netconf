/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class NetconfDeviceBuilder {

    private boolean reconnectOnSchemasChange;
    private NetconfDevice.SchemaResourcesDTO schemaResourcesDTO;
    private RemoteDeviceId id;
    private RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private ListeningExecutorService globalProcessingExecutor;
    private DeviceActionFactory deviceActionFactory;

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

    public NetconfDeviceBuilder setSalFacade(final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
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

    public NetconfDevice build() {
        validation();
        return new NetconfDevice(this.schemaResourcesDTO, this.id, this.salFacade, this.globalProcessingExecutor,
                this.reconnectOnSchemasChange, this.deviceActionFactory);
    }

    private void validation() {
        Preconditions.checkNotNull(this.id, "RemoteDeviceId is not initialized");
        Preconditions.checkNotNull(this.salFacade, "RemoteDeviceHandler is not initialized");
        Preconditions.checkNotNull(this.globalProcessingExecutor, "ExecutorService is not initialized");
        Preconditions.checkNotNull(this.schemaResourcesDTO, "SchemaResourceDTO is not initialized");
    }
}
