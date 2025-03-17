/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
final class SubtreeFilterWriter {
    private SubtreeFilterWriter() {
        // Hidden on purpose
    }

    static void writeSubtreeFilter(final XMLStreamWriter writer, final SubtreeFilter filter) throws XMLStreamException {
        for (final var containment : filter.containments()) {
            final var selections = containment.selections();
            for (final var selection : selections) {
                final var namespaceSelection = selection.selection();
                if (namespaceSelection instanceof NamespaceSelection.Exact exact) {
                    // writer.write()
                }
                if (namespaceSelection instanceof NamespaceSelection.Wildcard) {
                    // writer.write()
                }
            }
        }
    }
}
