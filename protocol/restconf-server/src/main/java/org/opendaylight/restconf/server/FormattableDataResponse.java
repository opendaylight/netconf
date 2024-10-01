/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link Response} containing a YANG Data as its content.
 */
@NonNullByDefault
record FormattableDataResponse(
        HttpResponseStatus status,
        @Nullable HttpHeaders headers,
        FormattableBody body,
        MessageEncoding encoding,
        PrettyPrintParam prettyPrint) implements Response {
    FormattableDataResponse {
        requireNonNull(status);
        requireNonNull(body);
        requireNonNull(encoding);
        requireNonNull(prettyPrint);
    }

    FormattableDataResponse(final HttpHeaders headers, final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(HttpResponseStatus.OK, requireNonNull(headers), body, encoding, prettyPrint);
    }

    FormattableDataResponse(final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(HttpResponseStatus.OK, null, body, encoding, prettyPrint);
    }

    void writeTo(final OutputStream out) throws IOException {
        encoding.formatBody(body, prettyPrint, out);
    }
}
