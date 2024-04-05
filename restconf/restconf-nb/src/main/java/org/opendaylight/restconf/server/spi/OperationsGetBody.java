/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSetMultimap;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.FormatParameters;
import org.opendaylight.restconf.server.api.FormattableBody;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 */
public abstract sealed class OperationsGetBody extends FormattableBody {
    public static final class Leaf extends OperationsGetBody {
        private final QName rpc;

        public Leaf(final EffectiveModelContext modelContext, final QName rpc) {
            super(modelContext);
            this.rpc = requireNonNull(rpc);
        }

        @Override
        void formatToJSON(final Writer out) throws IOException {
            // https://www.rfc-editor.org/rfc/rfc8040#page-84:
            //
            //            In JSON, the YANG module name identifies the module:
            //
            //              { 'ietf-system:system-restart' : [null] }
            out.write("{ ");
            appendJSON(out, jsonPrefix(rpc.getModule()), rpc);
            out.write(" }");
        }

        @Override
        void formatToXML(final Writer out) throws IOException {
            // https://www.rfc-editor.org/rfc/rfc8040#page-84:
            //
            //            In XML, the YANG module namespace identifies the module:
            //
            //              <system-restart
            //                 xmlns='urn:ietf:params:xml:ns:yang:ietf-system'/>
            appendXML(out, rpc);
        }

        @Override
        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return super.addToStringAttributes(helper.add("rpc", rpc));
        }
    }

    public static final class Container extends OperationsGetBody {
        private final ImmutableSetMultimap<QNameModule, QName> rpcs;

        public Container(final EffectiveModelContext modelContext,
                final ImmutableSetMultimap<QNameModule, QName> rpcs) {
            super(modelContext);
            this.rpcs = requireNonNull(rpcs);
        }

        @Override
        void formatToJSON(final Writer out) throws IOException {
            out.write("""
                {
                  "ietf-restconf:operations" : {\
                """);

            if (!rpcs.isEmpty()) {
                final var entryIt = rpcs.asMap().entrySet().stream()
                    .map(entry -> Map.entry(jsonPrefix(entry.getKey()), entry.getValue()))
                    .sorted(Comparator.comparing(Entry::getKey))
                    .iterator();
                var entry = entryIt.next();
                var nameIt = entry.getValue().iterator();
                while (true) {
                    out.write("\n    ");
                    appendJSON(out, entry.getKey(), nameIt.next());
                    if (nameIt.hasNext()) {
                        out.write(',');
                        continue;
                    }

                    if (entryIt.hasNext()) {
                        out.write(',');
                        entry = entryIt.next();
                        nameIt = entry.getValue().iterator();
                        continue;
                    }

                    break;
                }
            }

            out.write("\n  }\n}");
        }

        @Override
        void formatToXML(final Writer out) throws IOException {
            // Header with namespace declarations for each module
            out.write("<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"");
            if (rpcs.isEmpty()) {
                out.write("/>");
                return;
            }

            out.write('>');
            for (var rpc : rpcs.asMap().entrySet().stream()
                    .sorted(Comparator.comparing(Entry::getKey))
                    .flatMap(entry -> entry.getValue().stream())
                    .toArray(QName[]::new)) {
                out.write("\n  ");
                appendXML(out, rpc);
            }
            out.write("\n</operations>");
        }
    }

    private final EffectiveModelContext modelContext;

    private OperationsGetBody(final EffectiveModelContext modelContext) {
        super(() -> PrettyPrintParam.TRUE);
        this.modelContext = requireNonNull(modelContext);
    }

    @Override
    protected final void formatToJSON(final OutputStream out, final FormatParameters format) throws IOException {
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            formatToJSON(writer);
        }
    }

    abstract void formatToJSON(@NonNull Writer out) throws IOException;

    @Override
    protected final void formatToXML(final OutputStream out, final FormatParameters format) throws IOException {
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            formatToXML(writer);
        }
    }

    abstract void formatToXML(@NonNull Writer out) throws IOException;

    final @NonNull String jsonPrefix(final QNameModule namespace) {
        return modelContext.findModuleStatement(namespace).orElseThrow().argument().getLocalName();
    }

    private static void appendJSON(final Writer out, final String prefix, final QName rpc) throws IOException {
        out.write('"');
        out.write(prefix);
        out.write(':');
        out.write(rpc.getLocalName());
        out.write("\" : [null]");
    }

    private static void appendXML(final Writer out, final QName rpc) throws IOException {
        out.write('<');
        out.write(rpc.getLocalName());
        out.write(' ');
        out.write("xmlns=\"");
        out.write(rpc.getNamespace().toString());
        out.write("\"/>");
    }
}
