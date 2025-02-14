/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;

/**
 * An {@link AbstractServerStrategy} delegating to {@link ServerMountPointResolver} and other backend services.
 */
@NonNullByDefault
public class CompositeServerStrategy extends AbstractServerStrategy {
    private final ServerMountPointResolver resolver;
    private final ServerActionOperations action;
    private final ServerDataOperations data;
    private final ServerModulesOperations modules;
    private final ServerRpcOperations rpc;

    public CompositeServerStrategy(final DatabindContext databind, final ServerMountPointResolver resolver,
            final ServerActionOperations action, final ServerDataOperations data, final ServerModulesOperations modules,
            final ServerRpcOperations rpc) {
        super(databind);
        this.resolver = requireNonNull(resolver);
        this.action = requireNonNull(action);
        this.data = requireNonNull(data);
        this.modules = requireNonNull(modules);
        this.rpc = requireNonNull(rpc);
    }

    @Override
    public final ServerActionOperations action() {
        return action;
    }

    @Override
    public final ServerDataOperations data() {
        return data;
    }

    @Override
    public final ServerRpcOperations rpc() {
        return rpc;
    }

    @Override
    public final ServerMountPointResolver resolver() {
        return resolver;
    }

    @Override
    public final ServerModulesOperations modules() {
        return modules;
    }
}
