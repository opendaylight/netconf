/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.netty.ResponseUtils.notFoundResponse;
import static org.opendaylight.restconf.openapi.netty.ResponseUtils.optionsResponse;
import static org.opendaylight.restconf.openapi.netty.ResponseUtils.redirectResponse;
import static org.opendaylight.restconf.openapi.netty.ResponseUtils.responseWithContent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.server.PrincipalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenApiRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OpenApiRequestDispatcher.class);
    private static final ResourceData NO_RESOURCE = new ResourceData("", Unpooled.EMPTY_BUFFER);
    private static final String FALLBACK_MEDIA_TYPE = "application/octet-stream";

    private final PrincipalService principalService;
    private final OpenApiService openApiService;
    private final URI baseUri;
    private final URI homeURI;
    private final URI restconfServerUri;

    private final Cache<String, ResourceData> resourceCache;

    OpenApiRequestDispatcher(final @NonNull PrincipalService principalService,
            final @NonNull OpenApiService openApiService, final @NonNull URI baseUri,
            final @NonNull URI restconfServerUri) {
        this.principalService = requireNonNull(principalService);
        this.openApiService = requireNonNull(openApiService);
        this.baseUri = requireNonNull(baseUri);
        this.restconfServerUri = requireNonNull(restconfServerUri);
        this.homeURI = URI.create(baseUri.toString().concat("/explorer/index.html"));
        this.resourceCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();
    }

    public void dispatch(final @NonNull FullHttpRequest request,
            final @NonNull FutureCallback<FullHttpResponse> callback) {
        final var principal = principalService.acquirePrincipal(request);
        LOG.debug("Dispatching {} {} / username: {}", request.method(), request.uri(),
            principal == null ? null : principal.getName());
        callback.onSuccess(dispatchByMethod(request));
    }

    private FullHttpResponse dispatchByMethod(final FullHttpRequest request) {
        return switch (request.method().name()) {
            case "OPTIONS" -> optionsResponse(request.protocolVersion(), "GET, HEAD, OPTIONS");
            case "GET" -> dispatchByUri(request);
            case "HEAD" -> dispatchByUri(request).replace(Unpooled.EMPTY_BUFFER);
            default -> notFoundResponse(request.protocolVersion());
        };
    }

    private FullHttpResponse dispatchByUri(final  FullHttpRequest request) {
        final var params = new OpenApiRequestParameters(baseUri.getPath(), request);
        return switch (params.requestType()) {
            case UI, ALT_UI -> redirectResponse(request.protocolVersion(), homeURI.toString());
            case STATIC_CONTENT -> staticResource(params);
            default -> notFoundResponse(request.protocolVersion());
        };
    }

    private FullHttpResponse staticResource(final OpenApiRequestParameters params) {
        final ResourceData data = getResourceData(params.resource());
        return data.content().readableBytes() > 0
            ? responseWithContent(params.protocolVersion(), data.content().slice(), data.mediaType)
            : notFoundResponse(params.protocolVersion());
    }

    private ResourceData getResourceData(final String resource) {
        try {
            return resourceCache.get(resource, () -> loadResource(resource));
        } catch (ExecutionException e) {
            return NO_RESOURCE;
        }
    }

    private ResourceData loadResource(final String resource) {
        try (var in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                return NO_RESOURCE;
            }
            final var content = Unpooled.wrappedBuffer(in.readAllBytes()).asReadOnly();
            final var mediaType = URLConnection.guessContentTypeFromName(resource);
            return new ResourceData(mediaType == null ? FALLBACK_MEDIA_TYPE : mediaType, content);
        } catch (IOException e) {
            return NO_RESOURCE;
        }
    }

    private record ResourceData(String mediaType, ByteBuf content) {
    }
}
