/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.netty;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.EmptyRequestResponse;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.LinkRelation;
import org.opendaylight.netconf.transport.http.RequestResponse;
import org.opendaylight.netconf.transport.http.SegmentPeeler;
import org.opendaylight.netconf.transport.http.rfc6415.Link;
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
    private static final EmptyRequestResponse OPTIONS_ONLY_METHOD_NOT_ALLOWED;
    private static final EmptyRequestResponse OPTIONS_ONLY_OK;

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newHeaders()
            .set(HttpHeaderNames.ALLOW, "OPTIONS");
        OPTIONS_ONLY_METHOD_NOT_ALLOWED = new EmptyRequestResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        OPTIONS_ONLY_OK = new EmptyRequestResponse(HttpResponseStatus.OK, headers);
    }

    private static final EmptyRequestResponse GHO_METHOD_NOT_ALLOWED;
    private static final EmptyRequestResponse GHO_OK;

    static {
        final var headers = DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
            .set(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS");
        GHO_METHOD_NOT_ALLOWED = new EmptyRequestResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, headers);
        GHO_OK = new EmptyRequestResponse(HttpResponseStatus.OK, headers);
    }

    private final OpenApiService service;

    OpenApiResourceInstance(final String path, final OpenApiService service) {
        super(path);
        this.service = requireNonNull(service);
    }

    @Override
    public RequestResponse prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final SegmentPeeler peeler, final XRD xrd) {
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var segment = peeler.next();
        return switch (segment) {
            case "api" -> prepareApi(method, targetUri, headers, peeler, xrd);
            case "explorer" -> prepareExplorer(method, targetUri, headers, peeler);
            case "ui" -> prepareUi(method, peeler);
            default -> EmptyRequestResponse.NOT_FOUND;
        };
    }

    // the /api resource
    private RequestResponse prepareApi(final ImplementedMethod method,  final URI targetUri, final HttpHeaders headers,
            final SegmentPeeler peeler, final XRD xrd) {
        final var restconf = xrd.lookupLink(LinkRelation.RESTCONF);
        if (restconf == null) {
            return EmptyRequestResponse.NOT_FOUND;
        }
        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        final var first = peeler.next();
        if (!first.equals("v3")) {
            return EmptyRequestResponse.NOT_FOUND;
        }

        if (!peeler.hasNext()) {
            return optionsOnlyResponse(method);
        }

        return switch (peeler.next()) {
            case "mounts" -> prepareMounts(method, targetUri, headers, peeler, restconf);
            case "single" -> prepareSingle(method, targetUri, headers, peeler, restconf);
            case "ui" -> new EmptyRequestResponse(HttpResponseStatus.SEE_OTHER,
                DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                    .set(HttpHeaderNames.LOCATION, "../../../explorer" + peeler.remaining()));
            default -> prepareModule(method, headers, peeler);
        };
    }

    private RequestResponse prepareModule(final ImplementedMethod method, final HttpHeaders headers,
            final SegmentPeeler peeler) {
//        return invokeAndRespond(params, () -> openApiService.getDocByModule(params.module(), params.revision(),
//            restconfServerUri, params.width(), params.depth()));

        //  MODULE("/api/v3/([^/]+)", Param.MODULE),
        // FIXME: not found
        throw new UnsupportedOperationException();
    }

    private RequestResponse prepareMounts(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler, final Link restconf) {
        // FIXME: mounts
//        try {
//            final var bytes = OM.writer().writeValueAsBytes(openApiService.getListOfMounts());
//            return responseWithContent(params.protocolVersion(), Unpooled.wrappedBuffer(bytes),
//                HttpHeaderValues.APPLICATION_JSON);
//        } catch (IOException e) {
//            LOG.error("Exception getting mounts", e);
//            return internalErrorResponse(params.protocolVersion(), e);
//        }


        //  MOUNTS_INSTANCE("/api/v3/mounts/([^/]+)", Param.INSTANCE),
//        openApiService.getMountDoc(params.instance(), restconfServerUri,
//            params.width(), params.depth(), params.offset(), params.limit()));

        //  MOUNTS_INSTANCE_META("/api/v3/mounts/([^/]+)/meta", Param.INSTANCE),
//        () -> openApiService.getMountMeta(params.instance(), params.offset(), params.limit()));


        //  MOUNTS_INSTANCE_MODULE("/api/v3/mounts/([^/]+)/([^/]+)", Param.INSTANCE, Param.MODULE),
//        return invokeAndRespond(params, () -> openApiService.getMountDocByModule(params.instance(), params.module(),
//            params.revision(), restconfServerUri, params.width(), params.depth()));

        throw new UnsupportedOperationException();
    }

    private RequestResponse prepareSingle(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler, final Link restconf) {
        if (!peeler.hasNext()) {
            return switch (method) {
                case GET -> prepareSingleGet(targetUri, headers, restconf, true);
                case HEAD -> prepareSingleGet(targetUri, headers, restconf, false);
                case OPTIONS -> GHO_OK;
                default -> GHO_METHOD_NOT_ALLOWED;
            };
        }

        return peeler.next().equals("meta") ? prepareSingleMeta(method, targetUri, headers, peeler, restconf)
            : EmptyRequestResponse.NOT_FOUND;
    }

    private RequestResponse prepareSingleMeta(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler, final Link restconf) {
        final var params = queryParams(targetUri);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final MetadataEntity entity;
        try {
            entity = service.getAllModulesMeta(offset, limit);
        } catch (IOException e) {
            // FIXME: report a proper error
            throw new UnsupportedOperationException(e);
        }

        return new EntityReqeustResponse(entity);
    }

    private RequestResponse prepareSingleGet(final URI targetUri, final HttpHeaders headers, final Link restconf,
            final boolean withContent) {
        final var params = queryParams(targetUri);
        final var width = params.lookup("width", Integer::valueOf);
        final var depth = params.lookup("depth", Integer::valueOf);
        final var offset = params.lookup("offset", Integer::valueOf);
        final var limit = params.lookup("limit", Integer::valueOf);

        final DocumentEntity entity;
        try {
            entity = service.getAllModulesDoc(targetUri, width, depth, offset, limit);
        } catch (IOException e) {
            // FIXME: report a proper error
            throw new UnsupportedOperationException(e);
        }

        return new EntityReqeustResponse(entity);
    }

    // the /explorer resource
    private static RequestResponse prepareExplorer(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers, final SegmentPeeler peeler) {
        final var resourceName = QueryStringDecoder.decodeComponent(peeler.remaining());
        final var resource = OpenApiResourceInstance.class.getResource(resourceName);
        if (resource == null) {
            LOG.debug("Resource '{}' not found", resourceName);
            return EmptyRequestResponse.NOT_FOUND;
        }

        return switch (method) {
            case GET -> new URLRequestResponse(resource, true);
            case HEAD -> new URLRequestResponse(resource, false);
            case OPTIONS -> GHO_OK;
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    // the /ui resource.
    private static RequestResponse prepareUi(final ImplementedMethod method, final SegmentPeeler peeler) {
        return switch (method) {
            case GET, HEAD, OPTIONS -> new EmptyRequestResponse(HttpResponseStatus.SEE_OTHER,
                DefaultHttpHeadersFactory.headersFactory().newEmptyHeaders()
                    .set(HttpHeaderNames.LOCATION, "../explorer" + peeler.remaining()));
            default -> GHO_METHOD_NOT_ALLOWED;
        };
    }

    @Override
    protected void removeRegistration() {
        // No-op
    }

    private static EmptyRequestResponse optionsOnlyResponse(final ImplementedMethod method) {
        return switch (method) {
            case OPTIONS -> OPTIONS_ONLY_OK;
            default -> OPTIONS_ONLY_METHOD_NOT_ALLOWED;
        };
    }

    private static QueryParameters queryParams(final URI targetUri) {
        return QueryParameters.ofMultiValue(new QueryStringDecoder(targetUri).parameters());
    }
}
