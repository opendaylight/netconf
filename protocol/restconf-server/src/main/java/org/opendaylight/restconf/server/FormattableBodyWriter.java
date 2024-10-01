/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * An intermediary for writing a {@link FormattableBody} into a {@link ByteBuf}. There are only 4 possibilities, so we
 * use singletons.
 */
@NonNullByDefault
enum FormattableBodyWriter {
    COMPACT_JSON {
        @Override
        void writeBodyTo(final FormattableBody body, final ByteBufOutputStream out) throws IOException {
            body.formatToJSON(PrettyPrintParam.FALSE, out);
        }
    },
    COMPACT_XML {
        @Override
        void writeBodyTo(final FormattableBody body, final ByteBufOutputStream out) throws IOException {
            body.formatToXML(PrettyPrintParam.FALSE, out);
        }
    },
    PRETTY_JSON {
        @Override
        void writeBodyTo(final FormattableBody body, final ByteBufOutputStream out) throws IOException {
            body.formatToJSON(PrettyPrintParam.TRUE, out);
        }
    },
    PRETTY_XML {
        @Override
        void writeBodyTo(final FormattableBody body, final ByteBufOutputStream out) throws IOException {
            body.formatToXML(PrettyPrintParam.TRUE, out);
        }
    };

    void writeBodyTo(final FormattableBody body, final ByteBuf out) throws IOException {
        try (var os = new ByteBufOutputStream(out)) {
            writeBodyTo(body, out);
        }
    }

    abstract void writeBodyTo(FormattableBody body, ByteBufOutputStream out) throws IOException;
}
