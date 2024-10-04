/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.AsciiString;
import java.net.URI;
import java.security.Principal;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * RESTCONF /data resource, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.1>RFC8040, section 3.3.1</a>.
 */
@NonNullByDefault
final class DataResource extends AbstractLeafResource {
    private static final CompletedRequest METHOD_NOT_ALLOWED_DATASTORE =
        new DefaultCompletedRequest(HttpResponseStatus.METHOD_NOT_ALLOWED, AbstractPendingOptions.HEADERS_DATASTORE);

    DataResource(final EndpointInvariants invariants) {
        super(invariants);
    }

    @Override
    PreparedRequest prepare(final ImplementedMethod method, final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (method) {
            case DELETE -> prepareDelete(targetUri, headers, principal, path);
            case GET -> prepareGet(targetUri, headers, principal, path, true);
            case HEAD -> prepareGet(targetUri, headers, principal, path, false);
            case OPTIONS -> prepareOptions(targetUri, principal, path);
            case PATCH -> preparePatch(targetUri, headers, principal, path);
            case POST -> preparePost(targetUri, headers, principal, path);
            case PUT -> preparePut(targetUri, headers, principal, path);
        };
    }

    // delete target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.7
    private PreparedRequest prepareDelete(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return path.isEmpty() ? METHOD_NOT_ALLOWED_DATASTORE : requiredApiPath(path,
            apiPath -> new PendingDataDelete(invariants, targetUri, principal, apiPath));
    }

    // retrieve data and metadata for a resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.3
    // HEAD is same as GET but without content -> https://www.rfc-editor.org/rfc/rfc8040#section-4.2
    private PreparedRequest prepareGet(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final boolean withContent) {
        // Attempt to choose an encoding based on user's preference. If we cannot pick one, responding with a 406 status
        // and list the encodings we support
        final var encoding = chooseOutputEncoding(headers);
        return encoding == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
            apiPath -> new PendingDataGet(invariants, targetUri, principal, withContent, encoding, apiPath));
    }

    // resource options -> https://www.rfc-editor.org/rfc/rfc8040#section-4.1
    private PreparedRequest prepareOptions(final URI targetUri, final @Nullable Principal principal,
            final String path) {
        return optionalApiPath(path, apiPath -> new PendingDataOptions(invariants, targetUri, principal, apiPath));
    }

    // PATCH -> https://www.rfc-editor.org/rfc/rfc8040#section-4.6
    private PreparedRequest preparePatch(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        final var contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            return UNSUPPORTED_MEDIA_TYPE_PATCH;
        }
        final var mimeType = HttpUtil.getMimeType(contentType);
        if (mimeType == null) {
            return UNSUPPORTED_MEDIA_TYPE_PATCH;
        }
        final var mediaType = AsciiString.of(mimeType);

        for (var encoding : MessageEncoding.values()) {
            // FIXME: tighten this check to just dataMediaType
            if (encoding.producesDataCompatibleWith(mediaType)) {
                // Plain RESTCONF patch = merge target resource content ->
                // https://www.rfc-editor.org/rfc/rfc8040#section-4.6.1
                return optionalApiPath(path,
                    apiPath -> new PendingDataPatchPlain(invariants, targetUri, principal, encoding, apiPath));
            }
            if (encoding.patchMediaType().equals(mediaType)) {
                // YANG Patch = ordered list of edits that are applied to the target datastore ->
                // https://www.rfc-editor.org/rfc/rfc8072#section-2
                final var accept = chooseOutputEncoding(headers);
                return accept == null ? NOT_ACCEPTABLE_DATA : optionalApiPath(path,
                    apiPath -> new PendingDataPatchYang(invariants, targetUri, principal, encoding, accept, apiPath));
            }
        }

        return UNSUPPORTED_MEDIA_TYPE_PATCH;
    }

    // create resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.4.1
    // or invoke an action -> https://www.rfc-editor.org/rfc/rfc8040#section-3.6
    private PreparedRequest preparePost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (chooseInputEncoding(headers)) {
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
            case NOT_PRESENT -> preparePost(targetUri, headers, principal, path, invariants.defaultEncoding());
            case JSON -> preparePost(targetUri, headers, principal, path, MessageEncoding.JSON);
            case XML -> preparePost(targetUri, headers, principal, path, MessageEncoding.XML);
        };
    }

    private PreparedRequest preparePost(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path, final MessageEncoding content) {
        if (path.isEmpty()) {
            return new PendingDataCreate(invariants, targetUri, principal, content);
        }

        final var accept = chooseOutputEncoding(headers);
        return accept == null ? NOT_ACCEPTABLE_DATA : requiredApiPath(path,
            apiPath -> new PendingDataPost(invariants, targetUri, principal, content, accept, apiPath));
    }

    // create or replace target resource -> https://www.rfc-editor.org/rfc/rfc8040#section-4.5
    private PreparedRequest preparePut(final URI targetUri, final HttpHeaders headers,
            final @Nullable Principal principal, final String path) {
        return switch (chooseInputEncoding(headers)) {
            case UNRECOGNIZED, UNSPECIFIED -> UNSUPPORTED_MEDIA_TYPE_DATA;
            case NOT_PRESENT -> preparePut(targetUri, principal, path, invariants.defaultEncoding());
            case JSON -> preparePut(targetUri, principal, path, MessageEncoding.JSON);
            case XML -> preparePut(targetUri, principal, path, MessageEncoding.XML);
        };
    }

    private PreparedRequest preparePut(final URI targetUri, final @Nullable Principal principal, final String path,
            final MessageEncoding encoding) {
        return optionalApiPath(path,
            apiPath -> new PendingDataPut(invariants, targetUri, principal, encoding, apiPath));
    }
}
