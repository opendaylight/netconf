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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.ByteStreamRequestResponse;
import org.opendaylight.netconf.transport.http.Response;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * A {@link Response} containing a YANG Data as its content.
 */
@NonNullByDefault
final class FormattableDataResponse extends ByteStreamRequestResponse {
    private final FormattableBody body;
    private final MessageEncoding encoding;
    private final PrettyPrintParam prettyPrint;

    FormattableDataResponse(final HttpResponseStatus status, final @Nullable HttpHeaders headers,
            final FormattableBody body, final MessageEncoding encoding, final PrettyPrintParam prettyPrint) {
        super(status, headers);
        this.body = requireNonNull(body);
        this.encoding = requireNonNull(encoding);
        this.prettyPrint = requireNonNull(prettyPrint);
    }

    FormattableDataResponse(final HttpHeaders headers, final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(HttpResponseStatus.OK, requireNonNull(headers), body, encoding, prettyPrint);
    }

    FormattableDataResponse(final FormattableBody body, final MessageEncoding encoding,
            final PrettyPrintParam prettyPrint) {
        this(HttpResponseStatus.OK, null, body, encoding, prettyPrint);
    }

    @Override
    protected FullHttpResponse toHttpResponse(final HttpVersion version, final ByteBuf content) {
        return toHttpResponse(version, status, headers, content, encoding.dataMediaType());
    }

    @Override
    protected void writeBody(final OutputStream out) throws IOException {
        encoding.formatBody(body, prettyPrint, out);
    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return super.addToStringAttributes(helper)
            .add("contentType", encoding.dataMediaType())
            .add("prettyPrint", prettyPrint)
            .add("body", body);
    }
}
