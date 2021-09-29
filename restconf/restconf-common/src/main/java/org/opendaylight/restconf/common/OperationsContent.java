/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ArrayListMultimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;

/**
 * RESTCONF {@code /operations} content for a {@code GET} operation as per
 * <a href="https://datatracker.ietf.org/doc/html/rfc8040#section-3.3.2">RFC8040</a>.
 */
// FIXME: when bierman02 is gone, this should be folded to nb-rfc8040, as it is a server-side thing.
public enum OperationsContent {
    JSON("{ \"ietf-restconf:operations\" : { } }") {
        @Override
        String createBody(final Map<String, Collection<String>> prefixToLocalNames) {
            final var sb = new StringBuilder("{\n"
                + "  \"ietf-restconf:operations\" : {\n");
            var entryIt =prefixToLocalNames.entrySet().iterator();
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
        public String createBody(final Map<String, Collection<String>> nsToLocalNames) {
            // First pass: allocate a prefix for each namespace first, retaining what names go into that prefix. While
            // we are doing that, we might as well be preparing the string with the correspoding declarations
            final var prefixToLocalNames = new HashMap<String, Collection<String>>();
            final var sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"");
            int i = 0;
            for (var entry : nsToLocalNames.entrySet()) {
                final var prefix = "ns" + i++;
                sb.append("\n            xmlns:").append(prefix).append("=\"").append(entry.getKey()).append("\"");
                prefixToLocalNames.put(prefix, entry.getValue());
            }
            sb.append(" >");

            // Second pass: emit all leaves
            for (var entry : prefixToLocalNames.entrySet()) {
                for (var localName : entry.getValue()) {
                    sb.append("\n  <").append(entry.getKey()).append(':').append(localName).append("/>");
                }
            }

            return sb.append("\n</operations>").toString();
        }

        @Override
        String prefix(final ModuleEffectiveStatement module) {
            return module.localQNameModule().getNamespace().toString();
        }
    };

    private final @NonNull String emptyBody;

    OperationsContent(final String emptyBody) {
        this.emptyBody = requireNonNull(emptyBody);
    }

    /**
     * Return the content for a particular {@link EffectiveModelContext}.
     *
     * @param context Context to use
     * @return Content of HTTP GET operation as a String
     */
    public final @NonNull String bodyFor(final @Nullable EffectiveModelContext context) {
        if (context == null) {
            return emptyBody;
        }
        final var modules = context.getModuleStatements();
        if (modules.isEmpty()) {
            return emptyBody;
        }

        final var prefixToLocalNames = ArrayListMultimap.<String, String>create();
        for (var module : modules.values()) {
            final var name = module.argument().getLocalName();
            module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                .forEach(rpc -> prefixToLocalNames.put(name, rpc.argument().getLocalName()));
        }

        if (prefixToLocalNames.isEmpty()) {
            // No RPCs, return empty content
            return emptyBody;
        }

        return modules.isEmpty() ? emptyBody : createBody(prefixToLocalNames.asMap());
    }

    // FIXME: this should be List<Entry<String, List<String>>> and should be lex-ordered to maintain predictable output
    //        which makes testing easier
    abstract @NonNull String createBody(Map<String, Collection<String>> prefixToLocalNames);

    abstract @NonNull String prefix(ModuleEffectiveStatement module);
}
