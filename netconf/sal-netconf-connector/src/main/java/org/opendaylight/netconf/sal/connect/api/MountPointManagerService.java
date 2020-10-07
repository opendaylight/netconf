/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.api;

import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfSalFacadeType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

/**
 * This interface provides methods for creating RemoteDeviceHandler instance, maintaining common MountPointContext
 *  and Message transformer according to same capabilities of devices.
 */
public interface MountPointManagerService<PREF> {

    void updateNetconfMountPointHandler(String nodeId, MountPointContext mountPointContext,
            PREF netconfSessionPreferences, BaseSchema baseSchema);

    NetconfMessageTransformer getNetconfMessageTransformer(MountPointContext mountPointContext);

    RemoteDeviceHandler getInstance(RemoteDeviceId id, DOMMountPointService mountPointService, DataBroker dataBroker,
            String topologyId, NetconfSalFacadeType netconfSalFacadeType, ScheduledExecutorService executor,
            Long keepaliveDelaySeconds, Long defaultRequestTimeoutMillis);

    MountPointContext getMountPointContextByNodeId(String nodeId);
}
