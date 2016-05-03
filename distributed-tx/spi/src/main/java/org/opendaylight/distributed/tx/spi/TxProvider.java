/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.util.Set;

/**
 * Per node transaction provider SPI. Distributed tx treats every node just as an instance of ReadWriteTransaction.
 * This provider interface hides the details of its creation, whether the per node transactions come from MountPoints or are app specific.
 *
 * To hook a new provider, this is the interface to be implemented.
 */
public interface TxProvider {

    /**
     * Initialize per node transaction.
     *
     * @param nodeId IID for particular node
     * @return per node tx
     * @throws TxException.TxInitiatizationFailedException thrown when unable to initialize the tx
     */
    ReadWriteTransaction newTx(InstanceIdentifier<?> nodeId)
        throws TxException.TxInitiatizationFailedException;

    /**
     * Check the lock status of a device.
     *
     * @param device iid for particular device.
     *
     * @return true if the devices is locked. False otherwise.
     */
    boolean isDeviceLocked(InstanceIdentifier<?> device);
    /**
     * Lock a set of nodes.
     *
     * @param deviceSet set of IID of devices.
     * @return true if the devices are locked. False otherwise.
     * <ul>
     * <li> true if successfully locking all the devices. </li>
     * <li> false if failing to lock any device. And unlock all the devices which has been locked in the function.</li>
     * </ul>
     */
    boolean lockTransactionDevices(Set<InstanceIdentifier<?>> deviceSet);
    /**
     * Lock a set of nodes.
     *
     * @param deviceSet iid set of devices.
     */
    void releaseTransactionDevices(Set<InstanceIdentifier<?>>deviceSet);
}
