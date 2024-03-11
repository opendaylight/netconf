/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static org.opendaylight.restconf.server.impl.ContentTypes.RESTCONF_TYPES;
import static org.opendaylight.restconf.server.impl.ContentTypes.YANG_PATCH_TYPES;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.opendaylight.restconf.server.api.RestconfServer;

enum RestconfRequestMapping implements RequestProcessor {

    DATA_GET("/data", "/data/(.+)", HttpMethod.GET, RESTCONF_TYPES) {
        @Override
        public void process(final RestconfServer restconfServer, final RequestContext context) {
            // TBD
        }
    },

    YANG_PATCH("/data", null, HttpMethod.PATCH, YANG_PATCH_TYPES) {
        @Override
        public void process(RestconfServer restconfServer, RequestContext context) {
            // TBD
        }
    };

    private final String uriStartsWith;
    private final Pattern pathApiPattern;
    private HttpMethod method;
    private final Set<AsciiString> contentTypes;

    RestconfRequestMapping(final String startsWith, final String pathApiRegex, final HttpMethod method,
            final Set<AsciiString> contentTypes) {
        this.uriStartsWith = startsWith;
        this.pathApiPattern = pathApiRegex == null ? null : Pattern.compile(pathApiRegex);
        this.method = method;
        this.contentTypes = contentTypes;
    }

    static RestconfRequestMapping mapping(final RequestContext context) {
        return Arrays.stream(values()).filter(
            mapping -> mapping.method.equals(context.method())
                && context.contextPath().startsWith(mapping.uriStartsWith)
                && mapping.contentTypes.contains(context.contentType())
        ).findFirst().orElse(null);
    }

    @Override
    public abstract void process(RestconfServer restconfServer, RequestContext context);
}
