/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.api.OperationsGetResultHelper.appendJSON;
import static org.opendaylight.restconf.server.api.OperationsGetResultHelper.appendXML;
import static org.opendaylight.restconf.server.api.OperationsGetResultHelper.jsonPrefix;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 */
public sealed interface OperationsGetResult {
    record Leaf(EffectiveModelContext modelContext, QName rpc) implements OperationsGetResult {
        public Leaf {
            requireNonNull(modelContext);
            requireNonNull(rpc);
        }

        @Override
        public String toJSON() {
            // https://www.rfc-editor.org/rfc/rfc8040#page-84:
            //
            //            In JSON, the YANG module name identifies the module:
            //
            //              { 'ietf-system:system-restart' : [null] }
            return appendJSON(new StringBuilder("{ "), jsonPrefix(modelContext, rpc.getModule()), rpc).append(" }")
                .toString();
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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("rpc", rpc).toString();
        }
    }

    record Container(EffectiveModelContext modelContext, ImmutableSetMultimap<QNameModule, QName> rpcs)
            implements OperationsGetResult {
        public Container {
            requireNonNull(modelContext);
            requireNonNull(rpcs);
        }

        @Override
        public String toJSON() {
            final var sb = new StringBuilder("""
                {
                  "ietf-restconf:operations" : {\
                """);

            if (!rpcs.isEmpty()) {
                final var entryIt = rpcs.asMap().entrySet().stream()
                    .map(entry -> Map.entry(jsonPrefix(modelContext, entry.getKey()), entry.getValue()))
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

    @NonNull String toJSON();

    @NonNull String toXML();
}
