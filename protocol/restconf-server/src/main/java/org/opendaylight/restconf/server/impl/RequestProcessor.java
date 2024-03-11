/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import java.util.Set;
import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * Request processor.
 */
class RequestProcessor {
    private final String apiResource;
    private final HttpMethod method;
    private final Set<AsciiString> contentTypes;
    private final Processor processor;

    RequestProcessor(final String apiResource, final HttpMethod method, final Processor processor) {
        this(apiResource, method, Set.of(), processor);
    }

    RequestProcessor(final String apiResource, final HttpMethod method,
            final Set<AsciiString> contentTypes, final Processor processor) {
        this.apiResource = requireNonNull(apiResource);
        this.method = requireNonNull(method);
        this.contentTypes = requireNonNull(contentTypes);
        this.processor = requireNonNull(processor);
    }

    /**
     * Indicates if current processor is suitable for request.
     *
     * @param params request parameters
     * @return true if current processor can process the request, false otherwise
     */
    public final boolean matches(final RequestParameters params) {
        return method.equals(params.method())
            && apiResource.equals(params.pathParameters().apiResource())
            && (contentTypes.isEmpty() || contentTypes.contains(params.contentType()));
    }

    /**
     * Performs processing of the request.
     *
     * @param service service instance
     * @param params request parameters
     */
    void process(final RestconfServer service, final RequestParameters params) {
        processor.process(service, params);
    }

    @FunctionalInterface
    interface Processor {
        void process(RestconfServer service, RequestParameters params);
    }
}
