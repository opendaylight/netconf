/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.OptionsResult;

/**
 * An OPTIONS request to the /data resource.
 */
@NonNullByDefault
final class PendingDataOptions extends AbstractPendingOptions {
    private final ApiPath apiPath;

    PendingDataOptions(final EndpointInvariants invariants, final ApiPath apiPath) {
        super(invariants);
        this.apiPath = requireNonNull(apiPath);
    }

    @Override
    void execute(final NettyServerRequest<OptionsResult> request, final InputStream body) {
        final var server = server();
        if (apiPath.isEmpty()) {
            server.dataOPTIONS(request);
        } else {
            server.dataOPTIONS(request, apiPath);
        }
    }
}
