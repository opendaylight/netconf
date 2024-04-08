/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import java.io.Writer;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class AllOperations extends OperationsBody {
    private final ImmutableSetMultimap<QNameModule, QName> rpcs;

    AllOperations(final EffectiveModelContext modelContext, final ImmutableSetMultimap<QNameModule, QName> rpcs) {
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

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("rpcs", rpcs);
    }
}