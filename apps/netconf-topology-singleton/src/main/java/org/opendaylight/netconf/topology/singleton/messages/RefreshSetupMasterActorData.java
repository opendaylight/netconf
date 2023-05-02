/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;

/**
 * Master sends this message to the own actor to refresh setup data.
 */
public class RefreshSetupMasterActorData {
    private final NetconfTopologySetup netconfTopologyDeviceSetup;
    private final RemoteDeviceId remoteDeviceId;

    public RefreshSetupMasterActorData(final NetconfTopologySetup netconfTopologyDeviceSetup,
                                       final RemoteDeviceId remoteDeviceId) {
        this.netconfTopologyDeviceSetup = netconfTopologyDeviceSetup;
        this.remoteDeviceId = remoteDeviceId;
    }

    public NetconfTopologySetup getNetconfTopologyDeviceSetup() {
        return netconfTopologyDeviceSetup;
    }

    public RemoteDeviceId getRemoteDeviceId() {
        return remoteDeviceId;
    }
}
