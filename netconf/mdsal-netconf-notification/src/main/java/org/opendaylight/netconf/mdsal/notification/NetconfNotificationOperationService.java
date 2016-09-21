/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.notification;

import java.util.Collections;
import java.util.Set;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.notifications.impl.ops.CreateSubscription;

public class NetconfNotificationOperationService implements NetconfOperationService {
    private final Set<NetconfOperation> netconfOperations;

    public NetconfNotificationOperationService(String netconfSessionIdForReporting, NetconfNotificationRegistry netconfNotificationRegistry) {
        this.netconfOperations = Collections.singleton(new CreateSubscription(netconfSessionIdForReporting, netconfNotificationRegistry));
    }


    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return netconfOperations;
    }

    @Override
    public void close() {
        for (NetconfOperation netconfOperation : netconfOperations) {
            if (netconfOperation instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) netconfOperation).close();
                } catch (Exception e) {
                    throw new IllegalStateException("Exception while closing " + netconfOperation, e);
                }
            }
        }
    }
}
