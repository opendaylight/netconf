/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.PrincipalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenApiRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OpenApiRequestDispatcher.class);

    private final PrincipalService principalService;
    private final URI baseUri;
    private final URI defaultURI;
    private final URI restconfServerUri;

    public OpenApiRequestDispatcher(final @NonNull PrincipalService principalService, final @NonNull URI baseUri,
            final @NonNull URI restconfServerUri) {
        this.principalService = requireNonNull(principalService);
        this.baseUri = requireNonNull(baseUri);
        this.restconfServerUri = requireNonNull(restconfServerUri);
        defaultURI = baseUri.resolve("explorer/index.html");
    }

    public void dispatch(final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        final var principal = principalService.acquirePrincipal(request);
        LOG.debug("Dispatching {} {} / username: {}", request.method(), request.uri(),
            principal == null ? null : principal.getName());

        final var params = new OpenApiRequestParameters(baseUri.getPath(), request.uri());
        switch (params.requestType()) {

        }



    }
}
