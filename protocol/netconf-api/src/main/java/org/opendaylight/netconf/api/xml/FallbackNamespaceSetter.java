/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
final class FallbackNamespaceSetter extends NamespaceSetter {
    private final Throwable cause;
    private final String prefix;
    private final String uri;

    FallbackNamespaceSetter(final Throwable cause, final String prefix, final String uri) {
        this.cause = requireNonNull(cause);
        this.prefix = requireNonNull(prefix);
        this.uri = requireNonNull(uri);
    }

    @Override
    void initializeNamespace(final XMLStreamWriter writer) throws XMLStreamException {
        writer.setPrefix(prefix, uri);
    }

    @Override
    ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("prefix", prefix).add("uri", uri).add("cause", cause);
    }
}