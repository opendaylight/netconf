/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.events.mdsal;

import java.util.Set;
import org.opendaylight.netconf.server.api.notifications.NetconfNotificationRegistry;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;

final class NetconfNotificationOperationService implements NetconfOperationService {
    private final CreateSubscription createSubscription;

    NetconfNotificationOperationService(final String netconfSessionIdForReporting,
            final NetconfNotificationRegistry netconfNotificationRegistry) {
        createSubscription = new CreateSubscription(netconfSessionIdForReporting, netconfNotificationRegistry);
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Set.of(createSubscription);
    }

    @Override
    public void close() {
        createSubscription.close();
    }
}
