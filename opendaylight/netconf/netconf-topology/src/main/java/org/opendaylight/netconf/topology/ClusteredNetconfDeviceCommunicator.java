/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ClusteredNetconfDeviceCommunicator extends NetconfDeviceCommunicator implements RoleChangeListener {
    //private NetconfSessionPreferences netconfSessionPreferences;

    public ClusteredNetconfDeviceCommunicator(RemoteDeviceId id, RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice, NetconfSessionPreferences NetconfSessionPreferences) {
        super(id, remoteDevice, NetconfSessionPreferences);
    }

    public ClusteredNetconfDeviceCommunicator(RemoteDeviceId id, RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> remoteDevice) {
        super(id, remoteDevice);
    }

    protected void initDevice(NetconfSessionPreferences netconfSessionPreferences) {
        //do nothing
    }

    public void notifySalFacade(RemoteDeviceHandler salFacade, SchemaContext schemaContext){
        salFacade.onDeviceConnected(schemaContext, netconfSessionPreferences, new NetconfDeviceRpc(schemaContext, this, new NetconfMessageTransformer(schemaContext, true)));
    }

    public void callOnDeviceUp() {
        remoteDevice.onRemoteSessionUp(netconfSessionPreferences, this);
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        if(roleChangeDTO.isOwner()) {
            remoteDevice.onRemoteSessionUp(netconfSessionPreferences, this);
        } else {

        }
    }


}
