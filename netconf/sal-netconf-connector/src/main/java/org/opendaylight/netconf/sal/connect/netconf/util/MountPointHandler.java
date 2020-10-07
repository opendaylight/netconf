/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

public class MountPointHandler {

    private RemoteDeviceHandler remoteDeviceHandler;
    private MountPointContext cachedMountPointContext;

    public MountPointContext getCachedMountPointContext() {
        return cachedMountPointContext;
    }

    public void setCachedMountPointContext(MountPointContext cachedMountPointContext) {
        this.cachedMountPointContext = cachedMountPointContext;
    }

    public RemoteDeviceHandler getRemoteDeviceHandler() {
        return remoteDeviceHandler;
    }

    public void setRemoteDeviceHandler(RemoteDeviceHandler remoteDeviceHandler) {
        this.remoteDeviceHandler = remoteDeviceHandler;
    }
}

