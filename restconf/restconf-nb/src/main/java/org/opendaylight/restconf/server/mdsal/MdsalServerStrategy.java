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
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerStrategy;
import org.opendaylight.restconf.server.spi.HttpGetResource;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy;
import org.opendaylight.restconf.server.spi.YangLibraryVersionResource;

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
    /**
     * Result of a partial {@link ApiPath} lookup for the purposes of supporting {@code yang-ext:mount}-delimited mount
     * points with possible nesting.
     *
     * @param strategy the strategy to use
     * @param tail the {@link ApiPath} tail to use with the strategy
     */
    public record StrategyAndTail(AbstractServerStrategy strategy, ApiPath tail) {
        public StrategyAndTail {
            requireNonNull(strategy);
            requireNonNull(tail);
        }
    }

    private final HttpGetResource yangLibraryVersion;
    private final MountPointResolver mountPointResolver;
    private final ServerActionOperations action;
    private final ServerDataOperations data;
    private final ServerRpcOperations rpc;

    public MdsalServerStrategy(final DatabindContext databind, final MountPointResolver mountPointResolver,
            final ServerActionOperations action, final ServerDataOperations data, final ServerRpcOperations rpc) {
        super(databind);
        this.mountPointResolver = requireNonNull(mountPointResolver);
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
    StrategyAndTail resolveStrategy(final ApiPath path) throws ServerException {
        var mount = path.indexOf("yang-ext", "mount");
        if (mount == -1) {
            return new StrategyAndTail(this, path);
        }
        return mountPointResolver.resolveMountPoint(pathNormalizer.normalizeDataPath(path.subPath(0, mount)))
            .resolveStrategy(path.subPath(mount + 1));
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
