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
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.mdsal.spi.DOMServerActionOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerRpcOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.server.spi.CompositeServerStrategy;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
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

    public void onDeviceConnected(final EffectiveModelContext initialCtx, final ServerDataOperations dataOps,
            // FIXME: ServerActionOperations and ServerRpcOperations instead
            final RemoteDeviceServices services,
            // FIXME: not passed in: implemented on top of ServerDataOperations
            final DOMDataBroker broker, final NetconfDataTreeService dataTreeService) {
        onDeviceConnected(initialCtx, dataOps, services, new NetconfDeviceNotificationService(), broker,
            dataTreeService);
    }

    public synchronized void onDeviceConnected(final EffectiveModelContext initialCtx,
            final ServerDataOperations dataOps, final RemoteDeviceServices services,
            final NetconfDeviceNotificationService newNotificationService, final DOMDataBroker broker,
            final NetconfDataTreeService dataTreeService) {
        requireNonNull(mountService, "Closed");
        checkState(topologyRegistration == null, "Already initialized");

        final var mountBuilder = mountService.createMountPoint(mountPath);
        final var databind = DatabindContext.ofModel(initialCtx);

        mountBuilder.addService(DOMSchemaService.class, new FixedDOMSchemaService(initialCtx));

        final var rpcs = services.rpcs();
        mountBuilder.addService(NetconfRpcService.class, rpcs);

        final ServerRpcOperations rpcOps;
        if (rpcs instanceof Rpcs.Normalized normalized) {
            mountBuilder.addService(DOMRpcService.class, normalized.domRpcService());
            rpcOps = new DOMServerRpcOperations(normalized.domRpcService());
        } else if (rpcs instanceof Rpcs.Schemaless schemaless) {
            mountBuilder.addService(SchemalessRpcService.class, schemaless.schemalessRpcService());
            // FIXME: proper implementation
            rpcOps = NotSupportedServerRpcOperations.INSTANCE;
        } else {
            rpcOps = NotSupportedServerRpcOperations.INSTANCE;
        }

        final ServerActionOperations actionOps;
        if (services.actions() instanceof Actions.Normalized normalized) {
            mountBuilder.addService(DOMActionService.class, normalized);
            actionOps = new DOMServerActionOperations(normalized);
        } else {
            actionOps = NotSupportedServerActionOperations.INSTANCE;
        }

        mountBuilder.addService(DOMServerStrategy.class, new DOMServerStrategy(new CompositeServerStrategy(databind,
            NotSupportedServerMountPointResolver.INSTANCE, actionOps, dataOps,
            NotSupportedServerModulesOperations.INSTANCE, rpcOps)));

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