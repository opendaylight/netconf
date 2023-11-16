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
public record OperationsContent(
        EffectiveModelContext modelContext,
        ImmutableSetMultimap<QNameModule, QName> operations) {
    public OperationsContent {
        requireNonNull(modelContext);
        requireNonNull(operations);
    }

    public String toJSON() {
        final var sb = new StringBuilder("""
            {
              "ietf-restconf:operations" : {\
            """);

        if (!operations.isEmpty()) {
            final var entryIt = operations.asMap().entrySet().stream()
                .map(entry -> Map.entry(
                    modelContext.findModuleStatement(entry.getKey()).orElseThrow().argument().getLocalName(),
                    entry.getValue()))
                .sorted(Comparator.comparing(Entry::getKey))
                .iterator();
            var entry = entryIt.next();
            var nameIt = entry.getValue().iterator();
            while (true) {
                final var rpcName = nameIt.next().getLocalName();
                sb.append("\n    \"").append(entry.getKey()).append(':').append(rpcName).append("\": [null]");
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

    public String toXML() {
        // Header with namespace declarations for each module
        final var sb = new StringBuilder("""
            <?xml version="1.0" encoding="UTF-8"?>
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"\
            """);
        if (operations.isEmpty()) {
            return sb.append("/>").toString();
        }

        // We perform two passes:
        // - first we emit namespace declarations
        // - then we emit individual leaves
        final var entries = operations.asMap().entrySet().stream()
            .sorted(Comparator.comparing(Entry::getKey))
            .toList();

        for (int i = 0; i < entries.size(); ++i) {
            sb.append("\n            xmlns:ns").append(i).append("=\"").append(entries.get(i).getKey().getNamespace())
                .append('"');
        }
        sb.append('>');

        for (int i = 0; i < entries.size(); ++i) {
            for (var rpc : entries.get(i).getValue()) {
                sb.append("\n  <ns").append(i).append(':').append(rpc.getLocalName()).append("/>");
            }
        }

        return sb.append("\n</operations>").toString();
    }
}
