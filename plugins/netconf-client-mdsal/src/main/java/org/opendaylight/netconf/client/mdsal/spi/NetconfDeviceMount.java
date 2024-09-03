/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.client.mdsal.api.SchemalessRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for mocking
public class NetconfDeviceMount implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceMount.class);

    private final DOMMountPointService mountService;
    private final YangInstanceIdentifier mountPath;
    private final RemoteDeviceId id;

    private NetconfDeviceNotificationService notificationService;
    private ObjectRegistration<DOMMountPoint> topologyRegistration;

    public NetconfDeviceMount(final RemoteDeviceId id, final DOMMountPointService mountService,
            final YangInstanceIdentifier mountPath) {
        this.id = requireNonNull(id);
        this.mountService = requireNonNull(mountService);
        this.mountPath = requireNonNull(mountPath);
    }

    public void onDeviceConnected(final EffectiveModelContext initialCtx,
            final RemoteDeviceServices services, final DOMDataBroker broker,
            final NetconfDataTreeService dataTreeService) {
        onDeviceConnected(initialCtx, services, new NetconfDeviceNotificationService(), broker, dataTreeService);
    }

    public synchronized void onDeviceConnected(final EffectiveModelContext initialCtx,
            final RemoteDeviceServices services, final NetconfDeviceNotificationService newNotificationService,
            final DOMDataBroker broker, final NetconfDataTreeService dataTreeService) {
        requireNonNull(mountService, "Closed");
        checkState(topologyRegistration == null, "Already initialized");

        final var mountBuilder = mountService.createMountPoint(mountPath);
        mountBuilder.addService(DOMSchemaService.class, FixedDOMSchemaService.of(() -> initialCtx));

        final var rpcs = services.rpcs();
        mountBuilder.addService(NetconfRpcService.class, rpcs);
        if (rpcs instanceof Rpcs.Normalized normalized) {
            mountBuilder.addService(DOMRpcService.class, normalized);
        } else if (rpcs instanceof Rpcs.Schemaless schemaless) {
            mountBuilder.addService(SchemalessRpcService.class, schemaless);
        }
        if (services.actions() instanceof Actions.Normalized normalized) {
            mountBuilder.addService(DOMActionService.class, normalized);
        }

        if (broker != null) {
            mountBuilder.addService(DOMDataBroker.class, broker);
        }
        if (dataTreeService != null) {
            mountBuilder.addService(NetconfDataTreeService.class, dataTreeService);
        }
        mountBuilder.addService(DOMNotificationService.class, newNotificationService);
        notificationService = newNotificationService;

        topologyRegistration = mountBuilder.register();
        LOG.debug("{}: Mountpoint exposed into MD-SAL {}", id, topologyRegistration);
    }

    public synchronized void onDeviceDisconnected() {
        if (topologyRegistration == null) {
            LOG.trace("{}: Not removing mountpoint from MD-SAL, mountpoint was not registered yet", id);
            return;
        }

        try {
            topologyRegistration.close();
        } finally {
            LOG.debug("{}: Mountpoint removed from MD-SAL {}", id, topologyRegistration);
            topologyRegistration = null;
        }
    }

    public synchronized void publish(final DOMNotification domNotification) {
        checkNotNull(notificationService, "Device not set up yet, cannot handle notification %s", domNotification)
            .publishNotification(domNotification);
    }

    @Override
    public synchronized void close() {
        onDeviceDisconnected();
    }
}