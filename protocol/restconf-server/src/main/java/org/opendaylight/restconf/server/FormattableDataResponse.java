/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.AbstractFiniteResponse;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.netconf.transport.http.ResponseOutput;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link Response} containing a YANG Data as its content.
 */
@NonNullByDefault
final class FormattableDataResponse extends AbstractFiniteResponse {
    private final FormattableBody body;
    private final MessageEncoding encoding;
    private final PrettyPrintParam prettyPrint;
    private final ReadOnlyHttpHeaders headers;

    FormattableDataResponse(final HttpResponseStatus status, final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint, final List<CharSequence> headers) {
        super(status);
        this.body = requireNonNull(body);
        this.encoding = requireNonNull(encoding);
        this.prettyPrint = requireNonNull(prettyPrint);

        headers.add(HttpHeaderNames.CONTENT_TYPE);
        headers.add(encoding.dataMediaType());
        this.headers = new ReadOnlyHttpHeaders(false, headers.toArray(CharSequence[]::new));
    }

    FormattableDataResponse(final HttpResponseStatus status, final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(status, body, encoding, prettyPrint, new ArrayList<>(2));
    }

    FormattableDataResponse(final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(HttpResponseStatus.OK, body, encoding, prettyPrint);
    }

    @Override
    public void writeTo(final ResponseOutput output) throws IOException {
        try (var out = output.start(status(), headers)) {
            try {
                encoding.formatBody(body, prettyPrint, out);
            } catch (IllegalArgumentException | IOException e) {
                out.handleError(e);
                throw e;
            }
        }
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("headers", headers)
            .add("encoding", encoding)
            .add("prettyPrint", prettyPrint)
            .add("body", body);
    }
}
