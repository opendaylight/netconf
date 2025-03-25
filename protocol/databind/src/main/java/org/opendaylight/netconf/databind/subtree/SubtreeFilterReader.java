/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;

@NonNullByDefault
final class SubtreeFilterReader {
    private SubtreeFilterReader() {
        // Hidden on purpose
    }

    static SubtreeFilter readSubtreeFilter(final XMLStreamReader reader, final DatabindContext databind)
            throws XMLStreamException, IOException {
        final var builder = SubtreeFilter.builder(databind);

        final var result = new NormalizationResultHolder();
        final var streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);
        final var xmlParser = XmlParserStream.create(streamWriter, databind.modelContext());
        xmlParser.parse(reader);
        final var transformed = ((ContainerNode) result.getResult().data());
        for (final var child : transformed.body()) {
            builder.add(fillBuilder(child));
        }

        return builder.build();
    }

    private static Sibling fillBuilder(final DataContainerChild node) {
        final var identifier = node.name();
        if (node instanceof ContainerNode containerNode) {
            // if container is empty - select whole container
            if (containerNode.isEmpty()) {
                return SelectionNode.builder(new Exact(identifier)).build();
            }
            // if container not empty - select container with only specified children
            final var containment = ContainmentNode.builder(new Exact(identifier));
            for (final var child : containerNode.body()) {
                containment.add(fillBuilder(child));
            }
            return containment.build();
        } else if (node instanceof LeafNode leaf) {
            // if leaf is empty - select whole leaf
            if (leaf.body().equals("")) {
                return SelectionNode.builder(new Exact(identifier)).build();
            }
            // if leaf not empty - match content of the leaf
            return new ContentMatchNode(new Exact(identifier), leaf.body());
        }
        // TODO choice
        return null;
    }
}
