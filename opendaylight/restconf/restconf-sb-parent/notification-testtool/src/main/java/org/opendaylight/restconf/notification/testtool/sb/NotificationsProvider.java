/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notification.testtool.sb;

import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;

public class NotificationsProvider implements Provider, AutoCloseable {

    private final NotificationStoreServiceImpl notificationStoreService;
    private DeviceNotificationCollector collector;

    public NotificationsProvider(NotificationStoreServiceImpl notificationStoreService) {
        this.notificationStoreService = notificationStoreService;
    }

    @Override
    public void onSessionInitiated(Broker.ProviderSession session) {
        DOMMountPointService service = session.getService(DOMMountPointService.class);
        final DOMDataBroker dataBroker = session.getService(DOMDataBroker.class);
        collector = new DeviceNotificationCollector(service, dataBroker, notificationStoreService);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {
        collector.close();
    }
}
