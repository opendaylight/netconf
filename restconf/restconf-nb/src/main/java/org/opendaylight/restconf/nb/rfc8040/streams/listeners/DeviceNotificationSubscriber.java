/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.CreateStreamUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeviceNotificationSubscriber implements DOMMountPointListener {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationSubscriber.class);

    private final ConcurrentMap<YangInstanceIdentifier, DeviceNotificationListenerAdaptor> listener =
            new ConcurrentHashMap<>();
    private ListenerRegistration<DOMMountPointListener> reg = null;

    private final DOMMountPointService mountPointService;
    private final DOMDataBroker dataBroker;

    @Inject
    public DeviceNotificationSubscriber(final DOMMountPointService mountPointService, final DOMDataBroker dataBroker) {
        this.mountPointService = mountPointService;
        this.dataBroker = dataBroker;
    }

    public void init() {
        mountPointService.registerProvisionListener(this);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        CreateStreamUtil.createDeviceNotificationListenerOnMountPoint(mountPointService, dataBroker, path);
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
    }

}
