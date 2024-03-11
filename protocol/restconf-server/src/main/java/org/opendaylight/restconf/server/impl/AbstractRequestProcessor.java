/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import java.util.Set;
import java.util.regex.Pattern;

abstract class AbstractRequestProcessor implements RequestProcessor {
    protected final Pattern pathPattern;
    private final HttpMethod method;
    private final Set<AsciiString> contentTypes;

    AbstractRequestProcessor(final String pathMatchRegex, final HttpMethod method,
            final Set<AsciiString> contentTypes) {
        this.pathPattern = Pattern.compile(pathMatchRegex);
        this.method = method;
        this.contentTypes = contentTypes;
    }

    final boolean matches(final RequestContext context) {
        return method.equals(context.method())
            && pathPattern.matcher(context.contextPath()).matches()
            && (contentTypes.isEmpty() || contentTypes.contains(context.contentType()));
    }
}
