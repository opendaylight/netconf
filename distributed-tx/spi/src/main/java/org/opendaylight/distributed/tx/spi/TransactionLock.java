/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.spi;

import org.opendaylight.distributed.tx.api.DTXLogicalTXProviderType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.Map;
import java.util.Set;

/**
 * Generic APIs to lock devices. TX providers can re-use these API and corresponding implementations.
 */
public interface TransactionLock {
    /**
     * Indicating node lock state. It is thread-safe.
     *
     * @param type DTX logical TX provider type.
     * @param device device instatance idetifier.
     *
     * @return true if the device is locked. False otherwise.
     */
    boolean isLocked(DTXLogicalTXProviderType type, InstanceIdentifier<?> device);
    /**
     * Lock a set of devices. It is thread-safe
     *
     * @param type DTX logical TX provider type.
     * @param deviceSet set of device to lock.
     *
     * @return true if the device are locked. False otherwise.
     */
    boolean lockDevices(DTXLogicalTXProviderType type, Set<InstanceIdentifier<?>> deviceSet);
    /**
     * Lock sets of devices of each DTX provider. It is thread-safe
     *
     * @param deviceMap Map of set of devices to lock of each DTX provider type.
     *
     * @return true if the devices are locked. False otherwise.
     * <ul>
     * <li> true if successfully locking all the devices. </li>
     * <li> false if failing to lock any device. And unlock all the devices which has been locked in the function.</li>
     * </ul>
     */
    boolean lockDevices(Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> deviceMap);
    /**
     * Unlock a set of devices. It is thread-safe
     *
     * @param type DTX logical TX provider type.
     * @param deviceSet set of device to unlock.
     *
     */
    void releaseDevices(DTXLogicalTXProviderType type, Set<InstanceIdentifier<?>> deviceSet);
    /**
     * Unlock sets of devices of each DTX provider. It is thread-safe
     *
     * @param deviceMap Map of set of devices to unlock of each DTX provider type.
     */
    void releaseDevices(Map<DTXLogicalTXProviderType, Set<InstanceIdentifier<?>>> deviceMap);
}
