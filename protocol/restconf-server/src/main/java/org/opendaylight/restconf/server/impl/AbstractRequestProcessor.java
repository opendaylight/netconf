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
import java.util.regex.Pattern;

/**
 * Abstract implementation of {@link RequestProcessor#matches(RequestParameters)}.
 * Matches HTTP request by method first, then path pattern then content-type value (if limited by).
 */
abstract class AbstractRequestProcessor implements RequestProcessor {
    protected final Pattern pathPattern;
    private final HttpMethod method;
    private final Set<AsciiString> contentTypes;

    AbstractRequestProcessor(final String pattern, final HttpMethod method,
            final Set<AsciiString> contentTypes) {
        this.pathPattern = Pattern.compile(requireNonNull(pattern));
        this.method = requireNonNull(method);
        this.contentTypes = requireNonNull(contentTypes);
    }

    @Override
    public final boolean matches(final RequestParameters params) {
        return method.equals(params.method())
            && pathPattern.matcher(params.contextPath()).matches()
            && (contentTypes.isEmpty() || contentTypes.contains(params.contentType()));
    }
}
