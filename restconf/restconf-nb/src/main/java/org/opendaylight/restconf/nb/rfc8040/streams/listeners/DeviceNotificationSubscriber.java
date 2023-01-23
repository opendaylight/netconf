/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.annotations.Beta;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.CreateStreamUtil;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;


@Beta
@Component(service = { })
public class DeviceNotificationSubscriber implements DOMMountPointListener, AutoCloseable {

    private ListenerRegistration<DOMMountPointListener> reg = null;

    private final DOMMountPointService mountPointService;
    private final DOMDataBroker dataBroker;

    @Inject
    @Activate
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
            justification = "we need to register this DOMMountPointListener in DOMMountPointService")
    public DeviceNotificationSubscriber(@Reference final DOMMountPointService mountPointService,
            @Reference final DOMDataBroker dataBroker) {
        reg = mountPointService.registerProvisionListener(this);
        this.mountPointService = mountPointService;
        this.dataBroker = dataBroker;
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        CreateStreamUtil.createDeviceNotificationListenerOnMountPoint(mountPointService, dataBroker, path);
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
    }
}
