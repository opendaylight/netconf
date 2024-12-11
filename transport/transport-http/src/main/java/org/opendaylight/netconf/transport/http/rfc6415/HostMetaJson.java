/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.WellKnownURI;

/**
 * The contents of the {@code host-meta.json} document, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc6415.html#appendix-A">RFC6415, appendix A</a>.
 */
@NonNullByDefault
public final class HostMetaJson extends AbstractHostMeta {
    // https://www.rfc-editor.org/rfc/rfc8259#section-7
    //
    //    All Unicode characters may be placed within the
    //    quotation marks, except for the characters that MUST be escaped:
    //    quotation mark, reverse solidus, and the control characters (U+0000
    //    through U+001F).
    private static final Escaper ESCAPER;

    static {
        final var builder = Escapers.builder().addEscape('"', "\\\"").addEscape('\\', "\\\\");
        final var hex = HexFormat.of().withUpperCase();

        // U+0000 ... U+0007
        for (char ch = 0; ch < 7; ++ch) {
            builder.addEscape(ch, "\\u" + hex.toHexDigits(ch));
        }

        builder
            // U+0008
            .addEscape('\b', "\\b")
            // U+0009
            .addEscape('\t', "\\t")
            // U+000A
            .addEscape('\n', "\\n")
            // U+000B
            .addEscape('\u000B', "\\u000B")
            // U+000C
            .addEscape('\f', "\\f")
            // U+000D
            .addEscape('\r', "\\r");

        // U+000E ... U+001F
        for (char ch = 0x0E; ch <= 0x1F; ++ch) {
            builder.addEscape(ch, "\\u" + hex.toHexDigits(ch));
        }

        ESCAPER = builder.build();
    }

    public HostMetaJson(final XRD xrd) {
        super(xrd);
    }

    @Override
    public WellKnownURI wellKnownUri() {
        return WellKnownURI.HOST_META_JSON;
    }

    @Override
    public AsciiString mediaType() {
        return HttpHeaderValues.APPLICATION_JSON;
    }

    @Override
    protected void writeBody(final OutputStream out) throws IOException {
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.append("{\n");

            final var links = xrd.links().toArray(Link[]::new);
            if (links.length != 0) {
                writer.append("  \"links\" : [\n");

                final int last = links.length - 1;
                for (int i = 0; i < last; ++i) {
                    writeLink(writer, links[i]);
                    writer.append(",\n");
                }
                writeLink(writer, links[last]);
                writer.append("\n  ]\n");
            }

            writer.append('}');
        }
    }

    private static void writeLink(final Writer writer, final Link link) throws IOException {
        writer.append("    {\n      \"rel\" : \"");
        writeString(writer, link.rel());
        switch (link) {
            case TargetUri targetUri -> {
                writer.append("\",\n      \"href\" : \"");
                writeString(writer, targetUri.href());
            }
            case Template template -> {
                writer.append("\",\n      \"template\" : \"");
                writeString(writer, template.template());
            }
        }
        writer.append("\"\n    }");
    }

    private static void writeString(final Writer writer, final URI uri) throws IOException {
        writeString(writer, uri.toString());
    }

    private static void writeString(final Writer writer, final String str) throws IOException {
        writer.append(ESCAPER.escape(str));
    }
}
