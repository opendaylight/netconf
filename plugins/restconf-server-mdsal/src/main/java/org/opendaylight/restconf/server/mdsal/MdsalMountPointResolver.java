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
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.mdsal.spi.DOMServerActionOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerModulesOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerRpcOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.mdsal.spi.data.MdsalRestconfStrategy;
import org.opendaylight.restconf.server.spi.ErrorTags;
import org.opendaylight.restconf.server.spi.ExportingServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerDataOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerMountPointResolver;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy;
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
    public ServerStrategy resolveMountPoint(final Data mountPath) throws RequestException {
        final var mountPoint = mountPointService.getMountPoint(mountPath.instance())
            .orElseThrow(() -> new RequestException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point does not exist", new ErrorPath(mountPath), null));
        final var serverStrategy = mountPoint.getService(DOMServerStrategy.class);
        if (serverStrategy.isPresent()) {
            return serverStrategy.orElseThrow().serverStrategy();
        }

        final var mountSchemaService = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new RequestException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point does not expose DOMSchemaService", new ErrorPath(mountPath), null));
        final var mountModelContext = mountSchemaService.getGlobalContext();
        if (mountModelContext == null) {
            throw new RequestException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point does not have any models", new ErrorPath(mountPath), null);
        }
        final var sourceExporter = new ExportingServerModulesOperations(mountModelContext);
        final var sourceProvider = mountSchemaService.extension(YangTextSourceExtension.class);
        final var modules = sourceProvider == null ? sourceExporter
            : new DOMServerModulesOperations(sourceProvider, sourceExporter);

        final var mountDatabind = DatabindContext.ofModel(mountModelContext);

        return new MdsalServerStrategy(mountDatabind, mountPointResolverOf(mountPoint), actionOperationsOf(mountPoint),
            dataOperationsOf(mountDatabind, mountPoint), modules, rpcOperationsOf(mountPoint));
    }

    private static ServerActionOperations actionOperationsOf(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMActionService.class)
            .<ServerActionOperations>map(DOMServerActionOperations::new)
            .orElse(NotSupportedServerActionOperations.INSTANCE);
    }

    private static ServerDataOperations dataOperationsOf(final DatabindContext mountDatabind,
            final DOMMountPoint mountPoint) {
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
            .<ServerRpcOperations>map(DOMServerRpcOperations::new)
            .orElse(NotSupportedServerRpcOperations.INSTANCE);
    }
}
