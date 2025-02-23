/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import javax.xml.stream.XMLStreamReader;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.DatabindContext;

final class SubtreeFilterStreamReader {
    private final @NonNull DatabindContext databind;
    private final @NonNull XMLStreamReader reader;

    @NonNullByDefault
    SubtreeFilterStreamReader(final DatabindContext databind, final XMLStreamReader reader) {
        this.databind = requireNonNull(databind);
        this.reader = requireNonNull(reader);
    }

    @NonNull SubtreeFilter readFilter() {


        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
