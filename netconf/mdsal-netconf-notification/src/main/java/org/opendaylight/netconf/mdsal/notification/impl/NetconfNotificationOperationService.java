/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import java.util.Set;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.notifications.NetconfNotificationRegistry;

public final class NetconfNotificationOperationService implements NetconfOperationService {
    private final CreateSubscription createSubscription;

    public NetconfNotificationOperationService(final String netconfSessionIdForReporting,
            final NetconfNotificationRegistry netconfNotificationRegistry) {
        createSubscription = new CreateSubscription(netconfSessionIdForReporting, netconfNotificationRegistry);
    }

    @Override
    public Set<NetconfOperation> getNetconfOperations() {
        return Set.of(createSubscription);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void close() {
        createSubscription.close();
    }
}
