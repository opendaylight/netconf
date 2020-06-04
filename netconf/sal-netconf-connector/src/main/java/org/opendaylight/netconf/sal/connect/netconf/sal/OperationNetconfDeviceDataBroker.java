/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.tx.NetconfDOMDataBrokerOperations;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteCandidateRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteCandidateTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.CreateOperationWriteRunningTx;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.ReadWriteTx;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

public final class OperationNetconfDeviceDataBroker extends NetconfDeviceDataBroker
        implements NetconfDOMDataBrokerOperations {

    public OperationNetconfDeviceDataBroker(RemoteDeviceId id, MountPointContext mountContext,
                                            DOMRpcService rpc, NetconfSessionPreferences netconfSessionPreferences) {
        super(id, mountContext, rpc, netconfSessionPreferences);
    }

    @Override
    public DOMDataTreeReadWriteTransaction newReadWriteTransaction() {
        return newCreateOperationReadWriteTransaction();
    }

    @Override
    public DOMDataTreeWriteTransaction newWriteOnlyTransaction() {
        return newCreateOperationWriteTransaction();
    }

    @Override
    public DOMDataTreeWriteTransaction newCreateOperationWriteTransaction() {
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

    @Override
    public DOMDataTreeReadWriteTransaction newCreateOperationReadWriteTransaction() {
        return new ReadWriteTx(newReadOnlyTransaction(), newCreateOperationWriteTransaction());
    }
}
