/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 */
@NonNullByDefault
public abstract sealed class OperationsContent {
    private static final class Single extends OperationsContent {
        private final QName rpc;

        private Single(final EffectiveModelContext modelContext, final QName rpc) {
            super(modelContext);
            this.rpc = requireNonNull(rpc);
        }

        @Override
        public String toJSON() {
            // https://www.rfc-editor.org/rfc/rfc8040#page-84:
            //
            //            In JSON, the YANG module name identifies the module:
            //
            //              { 'ietf-system:system-restart' : [null] }
            return appendJSON(new StringBuilder("{ "), jsonPrefix(rpc.getModule()), rpc).append(" }").toString();
        }

        @Override
        public String toXML() {
            // https://www.rfc-editor.org/rfc/rfc8040#page-84:
            //
            //            In XML, the YANG module namespace identifies the module:
            //
            //              <system-restart
            //                 xmlns='urn:ietf:params:xml:ns:yang:ietf-system'/>
            return appendXML(new StringBuilder(), rpc).toString();
        }
    }

    private static final class Multiple extends OperationsContent {
        private final ImmutableSetMultimap<QNameModule, QName> rpcs;

        Multiple(final EffectiveModelContext modelContext,
                final ImmutableSetMultimap<QNameModule, QName> rpcs) {
            super(modelContext);
            this.rpcs = requireNonNull(rpcs);
        }

        @Override
        public String toJSON() {
            final var sb = new StringBuilder("""
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
                    appendJSON(sb.append("\n    "), entry.getKey(), nameIt.next());
                    if (nameIt.hasNext()) {
                        sb.append(',');
                        continue;
                    }

                    if (entryIt.hasNext()) {
                        sb.append(',');
                        entry = entryIt.next();
                        nameIt = entry.getValue().iterator();
                        continue;
                    }

                    break;
                }
            }

            return sb.append("\n  }\n}").toString();
        }

        @Override
        public String toXML() {
            // Header with namespace declarations for each module
            final var sb = new StringBuilder("<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"");
            if (rpcs.isEmpty()) {
                return sb.append("/>").toString();
            }

            sb.append('>');
            rpcs.asMap().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey))
                .flatMap(entry -> entry.getValue().stream())
                .forEach(rpc -> appendXML(sb.append("\n  "), rpc));
            return sb.append("\n</operations>").toString();
        }
    }

    private final EffectiveModelContext modelContext;

    private OperationsContent(final EffectiveModelContext modelContext) {
        this.modelContext = requireNonNull(modelContext);
    }

    public static OperationsContent of(final EffectiveModelContext modelContext, final QName rpc) {
        return new Single(modelContext, rpc);
    }

    public static OperationsContent of(final EffectiveModelContext modelContext,
            final Multimap<QNameModule, QName> rpcs) {
        return new Multiple(modelContext, ImmutableSetMultimap.copyOf(rpcs));
    }

    public abstract String toJSON();

    public abstract String toXML();

    final String jsonPrefix(final QNameModule namespace) {
        return modelContext.findModuleStatement(namespace).orElseThrow().argument().getLocalName();
    }

    private static StringBuilder appendJSON(final StringBuilder sb, final String prefix, final QName rpc) {
        return sb.append('"').append(prefix).append(':').append(rpc.getLocalName()).append("\" : [null]");
    }

    private static StringBuilder appendXML(final StringBuilder sb, final QName rpc) {
        return sb.append('<').append(rpc.getLocalName()).append(' ').append("xmlns=\"").append(rpc.getNamespace())
            .append("\"/>");
    }
}
