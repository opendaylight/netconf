/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.CompositeServerStrategy;
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
public final class MdsalServerStrategy extends CompositeServerStrategy {
    private final HttpGetResource yangLibraryVersion;

    public MdsalServerStrategy(final DatabindContext databind, final ServerMountPointResolver resolver,
            final ServerActionOperations action, final ServerDataOperations data, final ServerModulesOperations modules,
            final ServerRpcOperations rpc) {
        super(databind, resolver, action, data, modules, rpc);
        yangLibraryVersion = YangLibraryVersionResource.of(databind);
    }

    public void yangLibraryVersionGET(final ServerRequest<FormattableBody> request) {
        yangLibraryVersion.httpGET(request);
    }
}
