/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.util.AsciiString;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ModulesGetResult;

/**
 * A GET or HEAD request to the /modules resource.
 */
@NonNullByDefault
final class PendingModulesGetYin extends AbstractPendingModulesGet {
    PendingModulesGetYin(final EndpointInvariants invariants, final URI targetUri, final ApiPath mountPath,
            final String fileName) {
        super(invariants, targetUri, mountPath, fileName);
    }

    @Override
    void execute(final NettyServerRequest<ModulesGetResult> request, final String fileName,
            final @Nullable String revision) {
        server().modulesYinGET(request, fileName, revision);
    }

    @Override
    void execute(final NettyServerRequest<ModulesGetResult> request, final ApiPath mountPath, final String fileName,
            final @Nullable String revision) {
        server().modulesYinGET(request, mountPath, fileName, revision);
    }

    @Override
    AsciiString mediaType() {
        return NettyMediaTypes.APPLICATION_YIN_XML;
    }
}
