/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractServerStrategy;
import org.opendaylight.restconf.server.spi.HttpGetResource;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.restconf.server.spi.ServerModulesOperations;
import org.opendaylight.restconf.server.spi.ServerMountPointResolver;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy;
import org.opendaylight.restconf.server.spi.YangLibraryVersionResource;

/**
 * A {@link ServerStrategy} associated with the root of a {@link RestconfServer}. It exposes
 * {@link #yangLibraryVersionGET(ServerRequest)} to provide the RFC8525 advertisement.
 */
@NonNullByDefault
public final class MdsalServerStrategy extends AbstractServerStrategy {
    private final HttpGetResource yangLibraryVersion;
    private final ServerMountPointResolver resolver;
    private final ServerActionOperations action;
    private final ServerDataOperations data;
    private final ServerModulesOperations modules;
    private final ServerRpcOperations rpc;

    public MdsalServerStrategy(final DatabindContext databind, final ServerMountPointResolver resolver,
            final ServerActionOperations action, final ServerDataOperations data, final ServerModulesOperations modules,
            final ServerRpcOperations rpc) {
        super(databind);
        this.resolver = requireNonNull(resolver);
        this.action = requireNonNull(action);
        this.data = requireNonNull(data);
        this.modules = requireNonNull(modules);
        this.rpc = requireNonNull(rpc);
        yangLibraryVersion = YangLibraryVersionResource.of(databind);
    }

    public DatabindContext databind() {
        return databind;
    }

    public void yangLibraryVersionGET(final ServerRequest<FormattableBody> request) {
        yangLibraryVersion.httpGET(request);
    }

    @Override
    protected ServerActionOperations action() {
        return action;
    }

    @VisibleForTesting
    @Override
    public ServerDataOperations data() {
        return data;
    }

    @Override
    protected ServerRpcOperations rpc() {
        return rpc;
    }

    @Override
    protected ServerMountPointResolver resolver() {
        return resolver;
    }

    @Override
    protected ServerModulesOperations modules() {
        return modules;
    }
}
