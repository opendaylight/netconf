/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.base.Preconditions;
import java.util.concurrent.ExecutorService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class NetconfDeviceBuilder {

    private boolean reconnectOnSchemasChange;
    private NetconfDevice.SchemaResourcesDTO schemaResourcesDTO;
    private RemoteDeviceId id;
    private RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private ExecutorService globalProcessingExecutor;

    public NetconfDeviceBuilder() {
    }

    public NetconfDeviceBuilder setReconnectOnSchemasChange(boolean reconnectOnSchemasChange) {
        this.reconnectOnSchemasChange = reconnectOnSchemasChange;
        return this;
    }

    public NetconfDeviceBuilder setId(RemoteDeviceId id) {
        this.id = id;
        return this;
    }

    public NetconfDeviceBuilder setSchemaResourcesDTO(NetconfDevice.SchemaResourcesDTO schemaResourcesDTO) {
        this.schemaResourcesDTO = schemaResourcesDTO;
        return this;
    }

    public NetconfDeviceBuilder setSalFacade(RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        this.salFacade = salFacade;
        return this;
    }

    public NetconfDeviceBuilder setGlobalProcessingExecutor(ExecutorService globalProcessingExecutor) {
        this.globalProcessingExecutor = globalProcessingExecutor;
        return this;
    }

    public NetconfDevice build() {
        validation();
        return new NetconfDevice(schemaResourcesDTO, id, salFacade, globalProcessingExecutor, reconnectOnSchemasChange);
    }

    private void validation() {
        Preconditions.checkNotNull(id, "RemoteDeviceId is not initialized");
        Preconditions.checkNotNull(salFacade, "RemoteDeviceHandler is not initialized");
        Preconditions.checkNotNull(globalProcessingExecutor, "ExecutorService is not initialized");
        Preconditions.checkNotNull(schemaResourcesDTO, "SchemaResourceDTO is not initialized");
    }
}
