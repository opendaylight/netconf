/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.nb.rfc8040.ErrorTags;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.NetconfRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerErrorPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerStrategy;
import org.opendaylight.restconf.server.spi.HttpGetResource;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy;
import org.opendaylight.restconf.server.spi.YangLibraryVersionResource;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ServerStrategy} associated with the root of a {@link RestconfServer}. It exposes
 * {@link #yangLibraryVersionGET(ServerRequest)} to provide the RFC8525 advertisement.
 */
@NonNullByDefault
public final class MdsalServerStrategy extends AbstractServerStrategy {
    public record StrategyAndPath(AbstractServerStrategy strategy, Data path) {
        public StrategyAndPath {
            requireNonNull(strategy);
            requireNonNull(path);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(MdsalServerStrategy.class);

    private final HttpGetResource yangLibraryVersion;
    private final ServerActionOperations action;
    private final ServerDataOperations data;
    private final ServerRpcOperations rpc;

    public MdsalServerStrategy(final DatabindContext databind, final ServerActionOperations action,
            final ServerDataOperations data, final ServerRpcOperations rpc) {
        super(databind);
        this.action = requireNonNull(action);
        this.data = requireNonNull(data);
        this.rpc = requireNonNull(rpc);
        yangLibraryVersion = YangLibraryVersionResource.of(databind);
    }

    public DatabindContext databind() {
        return databind;
    }

    public void yangLibraryVersionGET(final ServerRequest<FormattableBody> request) {
        yangLibraryVersion.httpGET(request);
    }

    public StrategyAndPath resolveStrategyPath(final ApiPath path) throws ServerException {
        final var andTail = resolveStrategy(path);
        final var strategy = andTail.strategy();
        return new StrategyAndPath(strategy, strategy.pathNormalizer.normalizeDataPath(andTail.tail()));
    }

    /**
     * Resolve any and all {@code yang-ext:mount} to the target {@link StrategyAndTail}.
     *
     * @param path {@link ApiPath} to resolve
     * @return A strategy and the remaining path
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws ServerException if an error occurs
     */
    private StrategyAndTail resolveStrategy(final ApiPath path) throws ServerException {
        var mount = path.indexOf("yang-ext", "mount");
        if (mount == -1) {
            return new StrategyAndTail(this, path);
        }
        final var mountPath = path.subPath(0, mount);
        final var dataPath = pathNormalizer.normalizeDataPath(path.subPath(0, mount));
        if (mountPointService == null) {
            throw new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                "Mount point service is not available");
        }
        final var mountPoint = mountPointService.getMountPoint(dataPath.instance())
            .orElseThrow(() -> new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not exist", mountPath));

        return createStrategy(databind, mountPath, mountPoint).resolveStrategy(path.subPath(mount + 1));
    }

    private static RestconfStrategy createStrategy(final DatabindContext databind, final ApiPath mountPath,
            final DOMMountPoint mountPoint) throws ServerException {
        final var mountSchemaService = mountPoint.getService(DOMSchemaService.class)
            .orElseThrow(() -> new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not expose DOMSchemaService", mountPath));
        final var mountModelContext = mountSchemaService.getGlobalContext();
        if (mountModelContext == null) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT,
                "Mount point '%s' does not have any models", mountPath);
        }
        final var mountDatabind = DatabindContext.ofModel(mountModelContext);
        final var mountPointService = mountPoint.getService(DOMMountPointService.class).orElse(null);
        final var rpcService = mountPoint.getService(DOMRpcService.class).orElse(null);
        final var actionService = mountPoint.getService(DOMActionService.class).orElse(null);
        final var sourceProvider = mountPoint.getService(DOMSchemaService.class)
            .flatMap(schema -> Optional.ofNullable(schema.extension(YangTextSourceExtension.class)))
            .orElse(null);

        final var netconfService = mountPoint.getService(NetconfDataTreeService.class);
        if (netconfService.isPresent()) {
            return new NetconfRestconfStrategy(mountDatabind, netconfService.orElseThrow(), rpcService, actionService,
                sourceProvider, mountPointService);
        }
        final var dataBroker = mountPoint.getService(DOMDataBroker.class);
        if (dataBroker.isPresent()) {
            return new MdsalRestconfStrategy(mountDatabind, dataBroker.orElseThrow(), ImmutableMap.of(), rpcService,
                actionService, sourceProvider, mountPointService);
        }
        LOG.warn("Mount point {} does not expose a suitable access interface", mountPath);
        throw new ServerException(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
            "Could not find a supported access interface in mount point",
            new ServerErrorPath(databind, mountPoint.getIdentifier()));
    }

    @Override
    protected ServerActionOperations action() {
        return action;
    }

    @Override
    protected ServerDataOperations data() {
        return data;
    }

    @Override
    protected ServerRpcOperations rpc() {
        return rpc;
    }
}
