/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.common.Ordering;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an experimental iterator over a {@link NormalizedNode}. This is essentially
 * the opposite of a {@link javax.xml.stream.XMLStreamReader} -- unlike instantiating an iterator over
 * the backing data, this encapsulates a {@link NormalizedNodeStreamWriter} and allows
 * us to write multiple nodes.
 */
@Beta
public class ParameterAwareNormalizedNodeWriter implements RestconfNormalizedNodeWriter {
    private static final QName ROOT_DATA_QNAME = QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "data");

    private final NormalizedNodeStreamWriter writer;
    private final Integer maxDepth;
    protected final List<Set<QName>> fields;
    protected int currentDepth = 0;

    private ParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final DepthParam depth,
                                               final List<Set<QName>> fields) {
        this.writer = requireNonNull(writer);
        maxDepth = depth == null ? null : depth.value();
        this.fields = fields;
    }

    protected final NormalizedNodeStreamWriter getWriter() {
        return writer;
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static ParameterAwareNormalizedNodeWriter forStreamWriter(
            final NormalizedNodeStreamWriter writer, final DepthParam maxDepth, final List<Set<QName>> fields) {
        return forStreamWriter(writer, true,  maxDepth, fields);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}. Unlike the simple
     * {@link #forStreamWriter(NormalizedNodeStreamWriter, DepthParam, List)} method, this allows the caller to
     * switch off RFC6020 XML compliance, providing better throughput. The reason is that the XML mapping rules in
     * RFC6020 require the encoding to emit leaf nodes which participate in a list's key first and in the order in which
     * they are defined in the key. For JSON, this requirement is completely relaxed and leaves can be ordered in any
     * way we see fit. The former requires a bit of work: first a lookup for each key and then for each emitted node we
     * need to check whether it was already emitted.
     *
     * @param writer Back-end writer
     * @param orderKeyLeaves whether the returned instance should be RFC6020 XML compliant.
     * @param depth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static ParameterAwareNormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
                                                                     final boolean orderKeyLeaves,
                                                                     final DepthParam depth,
                                                                     final List<Set<QName>> fields) {
        return orderKeyLeaves ? new OrderedParameterAwareNormalizedNodeWriter(writer, depth, fields)
                : new ParameterAwareNormalizedNodeWriter(writer, depth, fields);
    }

    /**
     * Iterate over the provided {@link NormalizedNode} and emit write
     * events to the encapsulated {@link NormalizedNodeStreamWriter}.
     *
     * @param node Node
     * @return {@code ParameterAwareNormalizedNodeWriter}
     * @throws IOException when thrown from the backing writer.
     */
    @Override
    public final ParameterAwareNormalizedNodeWriter write(final NormalizedNode node) throws IOException {
        if (wasProcessedAsCompositeNode(node)) {
            return this;
        }

        if (wasProcessAsSimpleNode(node)) {
            return this;
        }

        throw new IllegalStateException("It wasn't possible to serialize node " + node);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }

    private boolean wasProcessAsSimpleNode(final NormalizedNode node) throws IOException {
        if (node instanceof LeafSetEntryNode<?> nodeAsLeafList) {
            if (selectedByParameters(node, false)) {
                writer.startLeafSetEntryNode(nodeAsLeafList.name());
                writer.scalarValue(nodeAsLeafList.body());
                writer.endNode();
            }
            return true;
        } else if (node instanceof LeafNode<?> nodeAsLeaf) {
            writer.startLeafNode(nodeAsLeaf.name());
            writer.scalarValue(nodeAsLeaf.body());
            writer.endNode();
            return true;
        } else if (node instanceof AnyxmlNode<?> anyxmlNode) {
            final Class<?> objectModel = anyxmlNode.bodyObjectModel();
            if (writer.startAnyxmlNode(anyxmlNode.name(), objectModel)) {
                if (DOMSource.class.isAssignableFrom(objectModel)) {
                    writer.domSourceValue((DOMSource) anyxmlNode.body());
                } else {
                    writer.scalarValue(anyxmlNode.body());
                }
                writer.endNode();
            }
            return true;
        } else if (node instanceof AnydataNode<?> anydataNode) {
            final Class<?> objectModel = anydataNode.bodyObjectModel();
            if (writer.startAnydataNode(anydataNode.name(), objectModel)) {
                writer.scalarValue(anydataNode.body());
                writer.endNode();
            }
            return true;
        }

        return false;
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
     * @return True
     * @throws IOException when the writer reports it
     */
    protected final boolean writeChildren(final Iterable<? extends NormalizedNode> children,
                                          final boolean mixinParent) throws IOException {
        for (final NormalizedNode child : children) {
            if (selectedByParameters(child, mixinParent)) {
                write(child);
            }
        }
        writer.endNode();
        return true;
    }

    protected boolean writeMapEntryChildren(final MapEntryNode mapEntryNode) throws IOException {
        if (selectedByParameters(mapEntryNode, false)) {
            writeChildren(mapEntryNode.body(), false);
        } else if (fields == null && maxDepth != null && currentDepth == maxDepth) {
            writeOnlyKeys(mapEntryNode.name().entrySet());
        }
        return true;
    }

    private void writeOnlyKeys(final Set<Entry<QName, Object>> entries) throws IOException {
        for (final Entry<QName, Object> entry : entries) {
            writer.startLeafNode(new NodeIdentifier(entry.getKey()));
            writer.scalarValue(entry.getValue());
            writer.endNode();
        }
        writer.endNode();
    }

    protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
        writer.startMapEntryNode(node.name(), node.size());
        currentDepth++;
        writeMapEntryChildren(node);
        currentDepth--;
        return true;
    }

    private boolean wasProcessedAsCompositeNode(final NormalizedNode node) throws IOException {
        boolean processedAsCompositeNode = false;
        if (node instanceof ContainerNode n) {
            if (!n.name().getNodeType().withoutRevision().equals(ROOT_DATA_QNAME)) {
                writer.startContainerNode(n.name(), n.size());
                currentDepth++;
                processedAsCompositeNode = writeChildren(n.body(), false);
                currentDepth--;
            } else {
                // write child nodes of data root container
                for (final NormalizedNode child : n.body()) {
                    currentDepth++;
                    if (selectedByParameters(child, false)) {
                        write(child);
                    }
                    currentDepth--;
                }
                processedAsCompositeNode = true;
            }
        } else if (node instanceof MapEntryNode n) {
            processedAsCompositeNode = writeMapEntryNode(n);
        } else if (node instanceof UnkeyedListEntryNode n) {
            writer.startUnkeyedListItem(n.name(), n.size());
            currentDepth++;
            processedAsCompositeNode = writeChildren(n.body(), false);
            currentDepth--;
        } else if (node instanceof ChoiceNode n) {
            writer.startChoiceNode(n.name(), n.size());
            processedAsCompositeNode = writeChildren(n.body(), true);
        } else if (node instanceof UnkeyedListNode n) {
            writer.startUnkeyedList(n.name(), n.size());
            processedAsCompositeNode = writeChildren(n.body(), false);
        } else if (node instanceof UserMapNode n) {
            writer.startOrderedMapNode(n.name(), n.size());
            processedAsCompositeNode = writeChildren(n.body(), true);
        } else if (node instanceof SystemMapNode n) {
            writer.startMapNode(n.name(), n.size());
            processedAsCompositeNode = writeChildren(n.body(), true);
        } else if (node instanceof LeafSetNode<?> n) {
            if (n.ordering() == Ordering.USER) {
                writer.startOrderedLeafSet(n.name(), n.size());
            } else {
                writer.startLeafSet(n.name(), n.size());
            }
            currentDepth++;
            processedAsCompositeNode = writeChildren(n.body(), true);
            currentDepth--;
        }

        return processedAsCompositeNode;
    }

    private static final class OrderedParameterAwareNormalizedNodeWriter extends ParameterAwareNormalizedNodeWriter {
        private static final Logger LOG = LoggerFactory.getLogger(OrderedParameterAwareNormalizedNodeWriter.class);

        OrderedParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final DepthParam depth,
                                                  final List<Set<QName>> fields) {
            super(writer, depth, fields);
        }

        @Override
        protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
            final NormalizedNodeStreamWriter writer = getWriter();
            writer.startMapEntryNode(node.name(), node.size());

            final Set<QName> qnames = node.name().keySet();
            // Write out all the key children
            currentDepth++;
            for (final QName qname : qnames) {
                final DataContainerChild child = node.childByArg(new NodeIdentifier(qname));
                if (child != null) {
                    if (selectedByParameters(child, false)) {
                        write(child);
                    }
                } else {
                    LOG.info("No child for key element {} found", qname);
                }
            }
            currentDepth--;

            currentDepth++;
            // Write all the rest
            final boolean result =
                    writeChildren(Iterables.filter(node.body(), input -> {
                        if (!qnames.contains(input.name().getNodeType())) {
                            return true;
                        }

                        LOG.debug("Skipping key child {}", input);
                        return false;
                    }), false);
            currentDepth--;
            return result;
        }
    }
}
