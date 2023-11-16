/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.2">RFC8040</a>.
 */
public enum OperationsContent {
    JSON("{ \"ietf-restconf:operations\" : { } }") {
        @Override
        String createBody(final List<Entry<String, List<String>>> rpcsByPrefix) {
            final var sb = new StringBuilder("{\n"
                + "  \"ietf-restconf:operations\" : {\n");
            var entryIt = rpcsByPrefix.iterator();
            var entry = entryIt.next();
            var nameIt = entry.getValue().iterator();
            while (true) {
                sb.append("    \"").append(entry.getKey()).append(':').append(nameIt.next()).append("\": [null]");
                if (nameIt.hasNext()) {
                    sb.append(",\n");
                    continue;
                }

                if (entryIt.hasNext()) {
                    sb.append(",\n");
                    entry = entryIt.next();
                    nameIt = entry.getValue().iterator();
                    continue;
                }

                break;
            }

            return sb.append("\n  }\n}").toString();
        }

        @Override
        String prefix(final ModuleEffectiveStatement module) {
            return module.argument().getLocalName();
        }
    },

    XML("<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"/>") {
        @Override
        String createBody(final List<Entry<String, List<String>>> rpcsByPrefix) {
            // Header with namespace declarations for each module
            final var sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"");
            for (int i = 0; i < rpcsByPrefix.size(); ++i) {
                final var prefix = "ns" + i;
                sb.append("\n            xmlns:").append(prefix).append("=\"").append(rpcsByPrefix.get(i).getKey())
                    .append("\"");
            }
            sb.append(" >");

            // Second pass: emit all leaves
            for (int i = 0; i < rpcsByPrefix.size(); ++i) {
                final var prefix = "ns" + i;
                for (var localName : rpcsByPrefix.get(i).getValue()) {
                    sb.append("\n  <").append(prefix).append(':').append(localName).append("/>");
                }
            }

            return sb.append("\n</operations>").toString();
        }

        @Override
        String prefix(final ModuleEffectiveStatement module) {
            return module.localQNameModule().getNamespace().toString();
        }
    };

    public final @NonNull String emptyBody;

    OperationsContent(final String emptyBody) {
        this.emptyBody = requireNonNull(emptyBody);
    }

    /**
     * Returns a list of entries, where each entry contains a module prefix and a list of RPC names.
     *
     * @param context the effective model context
     * @param modules the map of QNameModule to ModuleEffectiveStatement
     * @return a list of entries, where each entry contains a module prefix and a list of RPC names
     */
    public List<Entry<@NonNull String, List<String>>> getModuleRpcs(final EffectiveModelContext context,
            final Map<QNameModule, ModuleEffectiveStatement> modules) {
        return modules.values().stream()
                // Extract XMLNamespaces
                .map(module -> module.localQNameModule().getNamespace())
                // Make sure each is XMLNamespace unique
                .distinct()
                // Find the most recent module with that namespace. This needed so we expose the right set of RPCs,
                // as we always pick the latest revision to resolve prefix (or module name).
                .map(namespace -> context.findModuleStatements(namespace).iterator().next())
                // Convert to module prefix + List<String> with RPC names
                .map(module -> Map.entry(prefix(module),
                        module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                        .map(rpc -> rpc.argument().getLocalName())
                        .toList()))
                // Skip prefixes which do not have any RPCs
                .filter(entry -> !entry.getValue().isEmpty())
                // Ensure stability: sort by prefix
                .sorted(Entry.comparingByKey())
                .toList();
    }

    abstract @NonNull String createBody(List<Entry<String, List<String>>> rpcsByPrefix);

    abstract @NonNull String prefix(ModuleEffectiveStatement module);
}
