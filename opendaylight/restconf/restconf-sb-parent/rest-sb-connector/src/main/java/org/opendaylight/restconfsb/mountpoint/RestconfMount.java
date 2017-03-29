/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import com.google.common.base.Preconditions;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfMount implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfMount.class);

    private final RestconfDevice device;
    private ObjectRegistration<DOMMountPoint> registration;


    public RestconfMount(final RestconfDevice device) {
        this.device = Preconditions.checkNotNull(device);
    }

    /**
     * Registers device mountpoint to {@link DOMMountPointService}
     *
     * @param mountPointService mount point service
     */
    public void register(final DOMMountPointService mountPointService) {
        final YangInstanceIdentifier topologyPath = device.getDeviceId().getTopologyPath();
        registration = mountPointService
                .createMountPoint(topologyPath)
                .addInitialSchemaContext(device.getSchemaContext())
                .addService(DOMDataBroker.class, device.getDataBroker())
                .addService(DOMRpcService.class, device.getRpcService())
                .addService(DOMNotificationService.class, device.getNotificationService())
                .register();
        LOG.info("Restconf mount point {} registered", topologyPath);
    }

    /**
     * Deregisters device mount point
     */
    public void deregister() {
        try {
            if (registration != null) {
                registration.close();
                registration = null;
            }
            device.close();
        } catch (final Exception e) {
            LOG.error("Can't deregister mountpoint", e);
        }

    }

    @Override
    public void close() throws Exception {
        this.deregister();
    }
}