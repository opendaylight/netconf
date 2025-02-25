/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;

@NonNullByDefault
final class SubtreeFilterWriter {
    private final DatabindContext databind;
    private final XMLStreamWriter writer;

    SubtreeFilterWriter(final DatabindContext databind, final XMLStreamWriter writer) {
        this.databind = requireNonNull(databind);
        this.writer = requireNonNull(writer);
    }

    void writeSubtreeFilter(final SubtreeFilter subtreeFilter) throws XMLStreamException {
        // FIXME: implement this method
        throw new UnsupportedOperationException();
    }
}
