/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an experimental iterator over a {@link NormalizedNode}. This is essentially
 * the opposite of a {@link javax.xml.stream.XMLStreamReader} -- unlike instantiating an iterator over
 * the backing data, this encapsulates a {@link NormalizedNodeStreamWriter} and allows
 * us to write multiple nodes.
 */
// FIXME: this is a copy&paste from yangtools' NormalizedNodeWriter then adapted for filtering
sealed class DefaultNormalizedNodeWriter extends NormalizedNodeWriter {
    private static final QName ROOT_DATA_QNAME = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data");

    private final Integer maxDepth;
    private final List<Set<QName>> fields;

    int currentDepth = 0;

    DefaultNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final DepthParam depth,
            final List<Set<QName>> fields) {
        super(writer);
        maxDepth = depth == null ? null : depth.value();
        this.fields = fields;
    }

    @Override
    protected void writeAnydata(final AnydataNode<?> node) throws IOException {
        final var objectModel = node.bodyObjectModel();
        if (writer.startAnydataNode(node.name(), objectModel)) {
            writer.scalarValue(node.body());
            writer.endNode();
        }
    }

    @Override
    protected void writeAnyxml(final AnyxmlNode<?> node) throws IOException {
        final var objectModel = node.bodyObjectModel();
        if (writer.startAnyxmlNode(node.name(), objectModel)) {
            if (DOMSource.class.isAssignableFrom(objectModel)) {
                writer.domSourceValue((DOMSource) node.body());
            } else {
                writer.scalarValue(node.body());
            }
            writer.endNode();
        }
    }

    @Override
    protected void writeChoice(final ChoiceNode node) throws IOException {
        writer.startChoiceNode(node.name(), node.size());
        writeChildren(node.body(), true);
    }

    @Override
    protected void writeContainer(final ContainerNode node) throws IOException {
        if (!node.name().getNodeType().withoutRevision().equals(ROOT_DATA_QNAME)) {
            writer.startContainerNode(node.name(), node.size());
            currentDepth++;
            writeChildren(node.body(), false);
            currentDepth--;
        } else {
            // write child nodes of data root container
            for (var child : node.body()) {
                currentDepth++;
                if (selectedByParameters(child, false)) {
                    write(child);
                }
                currentDepth--;
            }
        }
    }

    @Override
    protected void writeLeaf(final LeafNode<?> node) throws IOException {
        writer.startLeafNode(node.name());
        writer.scalarValue(node.body());
        writer.endNode();
    }

    @Override
    protected void writeLeafSet(final LeafSetNode<?> node) throws IOException {
        final var ordering = node.ordering();
        switch (ordering) {
            case SYSTEM -> writer.startLeafSet(node.name(), node.size());
            case USER -> writer.startOrderedLeafSet(node.name(), node.size());
            default -> throw new IOException("Unsupported ordering " + ordering.argument());
        }

        currentDepth++;
        writeChildren(node.body(), true);
        currentDepth--;
    }

    @Override
    protected void writeLeafSetEntry(final LeafSetEntryNode<?> node) throws IOException {
        if (selectedByParameters(node, false)) {
            writer.startLeafSetEntryNode(node.name());
            writer.scalarValue(node.body());
            writer.endNode();
        }
    }

    @Override
    protected void writeMap(final MapNode node) throws IOException {
        final var ordering = node.ordering();
        switch (ordering) {
            case SYSTEM -> writer.startMapNode(node.name(), node.size());
            case USER -> writer.startOrderedMapNode(node.name(), node.size());
            default -> throw new IOException("Unsupported ordering " + ordering.argument());
        }
        writeChildren(node.body(), true);
    }

    @Override
    protected void writeMapEntry(final MapEntryNode node) throws IOException {
        writer.startMapEntryNode(node.name(), node.size());
        currentDepth++;
        writeMapEntryChildren(node);
        currentDepth--;
    }

    @Override
    protected void writeUnkeyedList(final UnkeyedListNode node) throws IOException {
        writer.startUnkeyedList(node.name(), node.size());
        writeChildren(node.body(), false);
    }

    @Override
    protected void writeUnkeyedListEntry(final UnkeyedListEntryNode node) throws IOException {
        writer.startUnkeyedListItem(node.name(), node.size());
        currentDepth++;
        writeChildren(node.body(), false);
        currentDepth--;
    }

    /**
     * Check if node should be written according to parameters fields and depth.
     * See <a href="https://tools.ietf.org/html/draft-ietf-netconf-restconf-18#page-49">Restconf draft</a>.
     * @param node Node to be written
     * @param mixinParent {@code true} if parent is mixin, {@code false} otherwise
     * @return {@code true} if node will be written, {@code false} otherwise
     */
    protected boolean selectedByParameters(final NormalizedNode node, final boolean mixinParent) {
        // nodes to be written are not limited by fields, only by depth
        if (fields == null) {
            return maxDepth == null || currentDepth < maxDepth;
        }

        // children of mixin nodes are never selected in fields but must be written if they are first in selected target
        if (mixinParent && currentDepth == 0) {
            return true;
        }

        // write only selected nodes
        if (currentDepth > 0 && currentDepth <= fields.size()) {
            return fields.get(currentDepth - 1).contains(node.name().getNodeType());
        }

        // after this depth only depth parameter is used to determine when to write node
        return maxDepth == null || currentDepth < maxDepth;
    }

    /**
     * Emit events for all children and then emit an endNode() event.
     *
     * @param children Child iterable
     * @param mixinParent {@code true} if parent is mixin, {@code false} otherwise
     * @throws IOException when the writer reports it
     */
    protected final void writeChildren(final Iterable<? extends NormalizedNode> children, final boolean mixinParent)
            throws IOException {
        for (var child : children) {
            if (selectedByParameters(child, mixinParent)) {
                write(child);
            }
        }
        writer.endNode();
    }

    private void writeMapEntryChildren(final MapEntryNode mapEntryNode) throws IOException {
        if (selectedByParameters(mapEntryNode, false)) {
            writeChildren(mapEntryNode.body(), false);
        } else if (fields == null && maxDepth != null && currentDepth == maxDepth) {
            writeOnlyKeys(mapEntryNode.name().entrySet());
        }
    }

    private void writeOnlyKeys(final Set<Entry<QName, Object>> entries) throws IOException {
        for (var entry : entries) {
            writer.startLeafNode(new NodeIdentifier(entry.getKey()));
            writer.scalarValue(entry.getValue());
            writer.endNode();
        }
        writer.endNode();
    }

    static final class OrderedRestconfNormalizedNodeWriter extends DefaultNormalizedNodeWriter {
        private static final Logger LOG = LoggerFactory.getLogger(OrderedRestconfNormalizedNodeWriter.class);

        OrderedRestconfNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final DepthParam depth,
                final List<Set<QName>> fields) {
            super(writer, depth, fields);
        }

        @Override
        protected void writeMapEntry(final MapEntryNode node) throws IOException {
            writer.startMapEntryNode(node.name(), node.size());

            final var qnames = node.name().keySet();
            // Write out all the key children
            currentDepth++;
            for (var qname : qnames) {
                final var child = node.childByArg(new NodeIdentifier(qname));
                if (child != null) {
                    if (selectedByParameters(child, false)) {
                        write(child);
                    }
                } else {
                    LOG.info("No child for key element {} found", qname);
                }
            }

            // Write all the rest
            writeChildren(Iterables.filter(node.body(), input -> {
                if (!qnames.contains(input.name().getNodeType())) {
                    return true;
                }

                LOG.debug("Skipping key child {}", input);
                return false;
            }), false);
            currentDepth--;
        }
    }
}
