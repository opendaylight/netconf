/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.DatabindContext;

@NonNullByDefault
final class SubtreeFilterStreamWriter {
    private final DatabindContext databind;
    private final XMLStreamWriter writer;

    SubtreeFilterStreamWriter(final DatabindContext databind, final XMLStreamWriter writer) {
        this.databind = requireNonNull(databind);
        this.writer = requireNonNull(writer);
    }

    void writeFilter(final SubtreeFilter filter) throws XMLStreamException {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
