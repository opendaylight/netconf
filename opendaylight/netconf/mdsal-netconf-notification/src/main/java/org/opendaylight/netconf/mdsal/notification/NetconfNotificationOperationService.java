/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.notification;

import com.google.common.collect.Sets;
import java.util.Set;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.notifications.impl.ops.CreateSubscription;

public class NetconfNotificationOperationService implements NetconfOperationService {
    private final String netconfSessionIdForReporting;
    private final NetconfNotificationRegistry netconfNotificationRegistry;

    public NetconfNotificationOperationService(String netconfSessionIdForReporting, NetconfNotificationRegistry netconfNotificationRegistry) {
        this.netconfSessionIdForReporting = netconfSessionIdForReporting;
        this.netconfNotificationRegistry = netconfNotificationRegistry;
    }


    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Sets.<NetconfOperation>newHashSet(new CreateSubscription(netconfSessionIdForReporting, netconfNotificationRegistry));
    }

    @Override
    public void close() {

    }
}
