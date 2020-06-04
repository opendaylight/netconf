/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.netconf.RFC6241DataTreeService;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteRunningTx;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

public class RFC6241NetconfDeviceDataBroker extends NetconfDeviceDataBroker implements RFC6241DataTreeService {

    public RFC6241NetconfDeviceDataBroker(RemoteDeviceId id, MountPointContext mountContext, DOMRpcService rpc,
                                          NetconfSessionPreferences netconfSessionPreferences) {
        super(id, mountContext, rpc, netconfSessionPreferences);
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        if (candidateSupported) {
            if (runningWritable) {
                return new CreateOperationWriteCandidateRunningTx(id, netconfOps, rollbackSupport, isLockAllowed);
            } else {
                return new CreateOperationWriteCandidateTx(id, netconfOps, rollbackSupport, isLockAllowed);
            }
        } else {
            return new CreateOperationWriteRunningTx(id, netconfOps, rollbackSupport, isLockAllowed);
        }
    }
}
