/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.netconf.databind.subtree.SubtreeMatcher;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;

public class SubtreeEventStreamFilter implements AbstractRestconfStreamRegistry.EventStreamFilter {
    private final DatabindContext databindContext;
    private final AnydataNode<?> filter;

    public SubtreeEventStreamFilter(final DatabindContext databindContext, final AnydataNode<?> filter) {
        this.databindContext = databindContext;
        this.filter = filter;
    }

    @Override
    public boolean test(final YangInstanceIdentifier path, final ContainerNode body) {
        try {
            final var writer = new StringWriter();
            final var xmlStreamWriter = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(writer);

            final var xmlNormalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlStreamWriter,
                databindContext.modelContext());
            final var normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(xmlNormalizedNodeStreamWriter);
            normalizedNodeWriter.write(filter);
            normalizedNodeWriter.flush();

            final var databindFilter = SubtreeFilter.readFrom(databindContext, UntrustedXML.createXMLStreamReader(
                new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8))));
            final var matcher = new SubtreeMatcher(databindFilter, body);
            return matcher.matches();
        } catch (IOException | XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }
}
