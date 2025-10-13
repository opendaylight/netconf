/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EmptyResponse;
import org.opendaylight.netconf.transport.http.ExceptionRequestResponse;
import org.opendaylight.netconf.transport.http.HeadersResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.LinkRelation;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.WebHostResourceInstance;
import org.opendaylight.netconf.transport.http.rfc6415.XRD;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main OpenAPI resource. Deals with dispatching HTTP requests to individual sub-resources as needed.
 */
@NonNullByDefault
final class OpenApiResourceInstance extends WebHostResourceInstance {
    private static final Logger LOG = LoggerFactory.getLogger(OpenApiResourceInstance.class);
    private static final HeadersResponse OPTIONS_ONLY_METHOD_NOT_ALLOWED =
        HeadersResponse.of(HttpResponseStatus.METHOD_NOT_ALLOWED,
            HttpHeaderNames.ALLOW, HttpMethod.OPTIONS.asciiName());
    private static final HeadersResponse OPTIONS_ONLY_OK =
        new HeadersResponse(HttpResponseStatus.OK, OPTIONS_ONLY_METHOD_NOT_ALLOWED.headers());
    private static final HeadersResponse GHO_METHOD_NOT_ALLOWED =
        HeadersResponse.of(HttpResponseStatus.METHOD_NOT_ALLOWED,
            HttpHeaderNames.ALLOW, AsciiString.cached("GET, HEAD, OPTIONS"));
    private static final HeadersResponse GHO_OK =
        new HeadersResponse(HttpResponseStatus.OK, GHO_METHOD_NOT_ALLOWED.headers());
    private static final HeadersResponse JSON_OK = HeadersResponse.of(HttpResponseStatus.OK,
        HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

    private final OpenApiService service;

    OpenApiResourceInstance(final String path, final OpenApiService service) {
        super(path);
        this.service = requireNonNull(service);
    }

    @Override
    protected void removeRegistration() {
        // No-op
    }

    @Override
    public Response prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final SegmentPeeler peeler, final XRD xrd) {
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var segment = peeler.next();
        return switch (segment) {
            case "api" -> api(method, targetUri, peeler, xrd);
            case "explorer" -> explorer(method, peeler);
            default -> EmptyResponse.NOT_FOUND;
        };
    }

    // the /api resource
    private Response api(final ImplementedMethod method, final URI targetUri, final SegmentPeeler peeler,
            final XRD xrd) {
        final var restconf = xrd.lookupLink(LinkRelation.RESTCONF);
        if (restconf == null) {
            return EmptyResponse.NOT_FOUND;
        }
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var first = peeler.next();
        if (!first.equals("v3")) {
            return EmptyResponse.NOT_FOUND;
        }

        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var next = peeler.next();
        return switch (next) {
            case "mounts" -> peeler.hasNext() ? apiMounts(method, targetUri, peeler)
                : switch (method) {
                    case GET -> new EntityRequestResponse(service.getListOfMounts());
                    case HEAD -> JSON_OK;
                    case OPTIONS -> GHO_OK;
                    default -> GHO_METHOD_NOT_ALLOWED;
                };
            case "single" -> single(method, targetUri, peeler);
            case "ui" -> HeadersResponse.of(HttpResponseStatus.SEE_OTHER,
                HttpHeaderNames.LOCATION, "/" + path() + "/explorer/index.html");
            default -> peeler.hasNext() ? EmptyResponse.NOT_FOUND
                : switch (method) {
                    case GET -> apiModule(targetUri, next, true);
                    case HEAD -> apiModule(targetUri, next, false);
                    case OPTIONS -> GHO_OK;
                    default -> GHO_METHOD_NOT_ALLOWED;
                };
        };
    }

    private Response apiModule(final URI targetUri, final String module, final boolean withContent) {
        final var params = queryParams(targetUri);
        final var revision = params.lookup("revision");
        final var width = params.lookup("width", Integer::valueOf);
        final var depth = params.lookup("depth", Integer::valueOf);

        final DocumentEntity entity;
        try {
            entity = service.getDocByModule(module, revision, targetUri, width, depth);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    private Response apiMounts(final ImplementedMethod method, final URI targetUri, final SegmentPeeler peeler) {
        final var instanceStr = peeler.next();
        final long instance;
        try {
            instance = Long.parseLong(instanceStr);
        } catch (NumberFormatException e) {
            return new ExceptionRequestResponse(e);
        }

        if (!peeler.hasNext()) {
            return switch (method) {
                case GET -> apiMount(targetUri, instance, true);
                case HEAD -> apiMount(targetUri, instance, false);
                case OPTIONS -> GHO_OK;
                default -> GHO_METHOD_NOT_ALLOWED;
            };
        }

        final var next = peeler.next();
        return peeler.hasNext() ? EmptyResponse.NOT_FOUND : switch (next) {
            case "meta" -> prepareMountsInstanceMeta(method, targetUri, instance);
            default -> prepareMountsInstanceModule(method, targetUri, instance, next);
        };
    }

    private Response apiMount(final URI targetUri, final long instance, final boolean withContent) {
        final var params = queryParams(targetUri);
        final var width = params.lookup("width", Integer::valueOf);
        final var depth = params.lookup("depth", Integer::valueOf);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final DocumentEntity entity;
        try {
            entity = service.getMountDoc(instance, targetUri, width, depth, offset, limit);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    private Response prepareMountsInstanceMeta(final ImplementedMethod method, final URI targetUri,
            final long instance) {
        return switch (method) {
            case GET -> prepareMountsInstanceMetaGet(targetUri, instance, true);
            case HEAD -> prepareMountsInstanceMetaGet(targetUri , instance, false);
            case OPTIONS -> GHO_OK;
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    private Response prepareMountsInstanceMetaGet(final URI targetUri, final long instance, final boolean withContent) {
        final var params = queryParams(targetUri);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final MetadataEntity entity;
        try {
            entity = service.getMountMeta(instance, offset, limit);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    private Response prepareMountsInstanceModule(final ImplementedMethod method, final URI targetUri,
            final long instance, final String module) {
        return switch (method) {
            case GET -> prepareMountsInstanceModuleGet(targetUri, instance, module, true);
            case HEAD -> prepareMountsInstanceModuleGet(targetUri, instance, module, false);
            case OPTIONS -> GHO_OK;
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    private Response prepareMountsInstanceModuleGet(final URI targetUri, final long instance, final String module,
            final boolean withContent) {
        final var params = queryParams(targetUri);
        final var revision = params.lookup("revision");
        final var width = params.lookup("width", Integer::valueOf);
        final var depth = params.lookup("depth", Integer::valueOf);

        final DocumentEntity entity;
        try {
            entity = service.getMountDocByModule(instance, module, revision, targetUri, width, depth);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    private Response single(final ImplementedMethod method, final URI targetUri, final SegmentPeeler peeler) {
        if (!peeler.hasNext()) {
            return switch (method) {
                case GET -> single(targetUri, true);
                case HEAD -> single(targetUri, false);
                case OPTIONS -> GHO_OK;
                default -> GHO_METHOD_NOT_ALLOWED;
            };
        }
        return !peeler.next().equals("meta") || peeler.hasNext() ? EmptyResponse.NOT_FOUND : switch (method) {
            case GET -> singleMeta(targetUri, true);
            case HEAD -> singleMeta(targetUri, false);
            case OPTIONS -> GHO_OK;
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    private Response single(final URI targetUri, final boolean withContent) {
        final var params = queryParams(targetUri);
        final var width = params.lookup("width", Integer::valueOf);
        final var depth = params.lookup("depth", Integer::valueOf);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final DocumentEntity entity;
        try {
            entity = service.getAllModulesDoc(targetUri, width, depth, offset, limit);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    private Response singleMeta(final URI targetUri, final boolean withContent) {
        final var params = queryParams(targetUri);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final MetadataEntity entity;
        try {
            entity = service.getAllModulesMeta(offset, limit);
        } catch (IOException e) {
            return new ExceptionRequestResponse(e);
        }
        return withContent ? new EntityRequestResponse(entity) : JSON_OK;
    }

    // the /explorer resource
    private static Response explorer(final ImplementedMethod method, final SegmentPeeler peeler) {
        var requested = QueryStringDecoder.decodeComponent(peeler.remaining());
        if (requested.isEmpty() || requested.equals("/")) {
            requested = "/index.html";
            LOG.debug("Adjusted request to {}", requested);
        }

        final var resourceName = "/explorer" + requested;
        final var resource = OpenApiResourceInstance.class.getResource(resourceName);
        if (resource == null) {
            LOG.debug("Resource '{}' not found", resourceName);
            return EmptyResponse.NOT_FOUND;
        }

        LOG.debug("Found resource {} for requested {}", resource, requested);
        return switch (method) {
            case GET -> new URLRequestResponse(resource, true);
            case HEAD -> new URLRequestResponse(resource, false);
            case OPTIONS -> GHO_OK;
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    private static HeadersResponse optionsOnlyResponse(final ImplementedMethod method) {
        return method == ImplementedMethod.OPTIONS ? OPTIONS_ONLY_OK :  OPTIONS_ONLY_METHOD_NOT_ALLOWED;
    }

    private static QueryParameters queryParams(final URI targetUri) {
        return QueryParameters.ofMultiValue(new QueryStringDecoder(targetUri).parameters());
    }
}
