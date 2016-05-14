/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spiimpl;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by cisco on 3/7/16.
 */
public class TxProviderLock {
    final private Set<InstanceIdentifier<?>> lockSet = new HashSet<>();

    public boolean isDeviceLocked(InstanceIdentifier<?> device) {
        boolean ret ;
        synchronized (TxProviderLock.this) {
            ret = lockSet.contains(device);
        }

        return ret;
    }

    public boolean lockDevices(Set<InstanceIdentifier<?>> deviceSet) {
        boolean ret = true;

        synchronized (TxProviderLock.this) {
            Set<InstanceIdentifier<?>> s = new HashSet<>();
            s.addAll(this.lockSet);

            s.retainAll(deviceSet);

            if(s.size() > 0)
                ret = false;
            else {
                lockSet.addAll(deviceSet);
            }
        }

        return ret;
    }

    public void releaseDevices(Set<InstanceIdentifier<?>> deviceSet) {
        synchronized (TxProviderLock.this) {
            this.lockSet.removeAll(deviceSet);
        }
    }
}
