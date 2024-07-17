/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A {@link FormattableBody} representing either all of {@link OperationsResource} or a single RPC within it.
 */
abstract sealed class OperationsBody extends FormattableBody permits AllOperations, OneOperation {
    private final EffectiveModelContext modelContext;

    OperationsBody(final EffectiveModelContext modelContext) {
        this.modelContext = requireNonNull(modelContext);
    }

    @Override
    public final void formatToJSON(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            formatToJSON(writer);
        }
    }

    abstract void formatToJSON(@NonNull Writer out) throws IOException;

    @Override
    public final void formatToXML(final PrettyPrintParam prettyPrint, final OutputStream out) throws IOException {
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            formatToXML(writer);
        }
    }

    abstract void formatToXML(@NonNull Writer out) throws IOException;

    final @NonNull String jsonPrefix(final QNameModule namespace) {
        return modelContext.findModuleStatement(namespace).orElseThrow().argument().getLocalName();
    }

    static final void appendJSON(final Writer out, final String prefix, final QName rpc) throws IOException {
        out.write('"');
        out.write(prefix);
        out.write(':');
        out.write(rpc.getLocalName());
        out.write("\" : [null]");
    }

    static final void appendXML(final Writer out, final QName rpc) throws IOException {
        out.write('<');
        out.write(rpc.getLocalName());
        out.write(' ');
        out.write("xmlns=\"");
        out.write(rpc.getNamespace().toString());
        out.write("\"/>");
    }
}