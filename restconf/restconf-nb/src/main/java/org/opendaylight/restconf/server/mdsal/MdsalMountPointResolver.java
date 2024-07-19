/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.ErrorTags;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerDataOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerMountPointResolver;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * A resolver of {@code yang-ext:mount} references based on {@link DOMMountPointService}.
 */
@NonNullByDefault
public record MdsalMountPointResolver(DOMMountPointService mountPointService) implements ServerMountPointResolver {
    public MdsalMountPointResolver {
        requireNonNull(mountPointService);
    }

    @Override
    public MdsalServerStrategy resolveMountPoint(final Data mountPath) throws ServerException {
        final var mountPoint = mountPointService.getMountPoint(mountPath.instance())
            .orElseThrow(() -> new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not exist", mountPath));
        final var mountSchemaService = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not expose DOMSchemaService", mountPath));
        final var mountModelContext = mountSchemaService.getGlobalContext();
        if (mountModelContext == null) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not have any models", mountPath);
        }
        final var mountDatabind = DatabindContext.ofModel(mountModelContext);

        return new MdsalServerStrategy(mountDatabind, mountPointResolverOf(mountPoint), actionOperationsOf(mountPoint),
            dataOperationsOf(mountDatabind, mountPoint), rpcOperationsOf(mountPoint));
    }

    private static ServerActionOperations actionOperationsOf(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMActionService.class)
            .<ServerActionOperations>map(MdsalServerActionOperations::new)
            .orElse(NotSupportedServerActionOperations.INSTANCE);
    }

    private static ServerDataOperations dataOperationsOf(final DatabindContext mountDatabind,
            final DOMMountPoint mountPoint) throws ServerException {
        final var netconfService = mountPoint.getService(NetconfDataTreeService.class);
        if (netconfService.isPresent()) {
            return new NetconfRestconfStrategy(mountDatabind, netconfService.orElseThrow());
        }
        final var dataBroker = mountPoint.getService(DOMDataBroker.class);
        if (dataBroker.isPresent()) {
            return new MdsalRestconfStrategy(mountDatabind, dataBroker.orElseThrow());
        }
        return NotSupportedServerDataOperations.INSTANCE;
    }

    private static ServerMountPointResolver mountPointResolverOf(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMMountPointService.class)
            .<ServerMountPointResolver>map(MdsalMountPointResolver::new)
            .orElse(NotSupportedServerMountPointResolver.INSTANCE);
    }

    private static ServerRpcOperations rpcOperationsOf(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMRpcService.class)
            .<ServerRpcOperations>map(MdsalServerRpcOperations::new)
            .orElse(NotSupportedServerRpcOperations.INSTANCE);
    }
}
