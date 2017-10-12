/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter.UNKNOWN_SIZE;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.api.RestconfNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamAttributeWriter;
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

    private ParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final Integer maxDepth,
                                               final List<Set<QName>> fields) {
        this.writer = Preconditions.checkNotNull(writer);
        this.maxDepth = maxDepth;
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
            final NormalizedNodeStreamWriter writer, final Integer maxDepth, final List<Set<QName>> fields) {
        return forStreamWriter(writer, true,  maxDepth, fields);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}. Unlike the simple
     * {@link #forStreamWriter(NormalizedNodeStreamWriter, Integer, List)}
     * method, this allows the caller to switch off RFC6020 XML compliance, providing better
     * throughput. The reason is that the XML mapping rules in RFC6020 require the encoding
     * to emit leaf nodes which participate in a list's key first and in the order in which
     * they are defined in the key. For JSON, this requirement is completely relaxed and leaves
     * can be ordered in any way we see fit. The former requires a bit of work: first a lookup
     * for each key and then for each emitted node we need to check whether it was already
     * emitted.
     *
     * @param writer Back-end writer
     * @param orderKeyLeaves whether the returned instance should be RFC6020 XML compliant.
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static ParameterAwareNormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
                                                                     final boolean orderKeyLeaves,
                                                                     final Integer maxDepth,
                                                                     final List<Set<QName>> fields) {
        return orderKeyLeaves ? new OrderedParameterAwareNormalizedNodeWriter(writer, maxDepth, fields)
                : new ParameterAwareNormalizedNodeWriter(writer, maxDepth, fields);
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
    public final ParameterAwareNormalizedNodeWriter write(final NormalizedNode<?, ?> node) throws IOException {
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

    /**
     * Emit a best guess of a hint for a particular set of children. It evaluates the
     * iterable to see if the size can be easily gotten to. If it is, we hint at the
     * real number of child nodes. Otherwise we emit UNKNOWN_SIZE.
     *
     * @param children Child nodes
     * @return Best estimate of the collection size required to hold all the children.
     */
    static final int childSizeHint(final Iterable<?> children) {
        return children instanceof Collection ? ((Collection<?>) children).size() : UNKNOWN_SIZE;
    }

    private boolean wasProcessAsSimpleNode(final NormalizedNode<?, ?> node) throws IOException {
        if (node instanceof LeafSetEntryNode) {
            if (selectedByParameters(node, false)) {
                final LeafSetEntryNode<?> nodeAsLeafList = (LeafSetEntryNode<?>) node;
                if (writer instanceof NormalizedNodeStreamAttributeWriter) {
                    ((NormalizedNodeStreamAttributeWriter) writer).leafSetEntryNode(nodeAsLeafList.getNodeType(),
                            nodeAsLeafList.getValue(), nodeAsLeafList.getAttributes());
                } else {
                    writer.leafSetEntryNode(nodeAsLeafList.getNodeType(), nodeAsLeafList.getValue());
                }
            }
            return true;
        } else if (node instanceof LeafNode) {
            final LeafNode<?> nodeAsLeaf = (LeafNode<?>)node;
            if (writer instanceof NormalizedNodeStreamAttributeWriter) {
                ((NormalizedNodeStreamAttributeWriter) writer).leafNode(
                        nodeAsLeaf.getIdentifier(), nodeAsLeaf.getValue(), nodeAsLeaf.getAttributes());
            } else {
                writer.leafNode(nodeAsLeaf.getIdentifier(), nodeAsLeaf.getValue());
            }
            return true;
        } else if (node instanceof AnyXmlNode) {
            final AnyXmlNode anyXmlNode = (AnyXmlNode)node;
            writer.anyxmlNode(anyXmlNode.getIdentifier(), anyXmlNode.getValue());
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
    protected boolean selectedByParameters(final NormalizedNode<?, ?> node, final boolean mixinParent) {
        // nodes to be written are not limited by fields, only by depth
        if (fields == null) {
            return maxDepth == null || currentDepth < maxDepth;
        }

        // children of mixin nodes are never selected in fields but must be written if they are first in selected target
        if (mixinParent && currentDepth == 0) {
            return true;
        }

        // always write augmentation nodes
        if (node instanceof AugmentationNode) {
            return true;
        }

        // write only selected nodes
        if (currentDepth > 0 && currentDepth <= fields.size()) {
            return fields.get(currentDepth - 1).contains(node.getNodeType());
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
    protected final boolean writeChildren(final Iterable<? extends NormalizedNode<?, ?>> children,
                                          final boolean mixinParent) throws IOException {
        for (final NormalizedNode<?, ?> child : children) {
            if (selectedByParameters(child, mixinParent)) {
                write(child);
            }
        }
        writer.endNode();
        return true;
    }

    protected boolean writeMapEntryChildren(final MapEntryNode mapEntryNode) throws IOException {
        if (selectedByParameters(mapEntryNode, false)) {
            writeChildren(mapEntryNode.getValue(), false);
        } else if (fields == null && maxDepth != null && currentDepth == maxDepth) {
            writeOnlyKeys(mapEntryNode.getIdentifier().getKeyValues());
        }
        return true;
    }

    private void writeOnlyKeys(final Map<QName, Object> keyValues) throws IllegalArgumentException, IOException {
        for (final Map.Entry<QName, Object> entry : keyValues.entrySet()) {
            writer.leafNode(new NodeIdentifier(entry.getKey()), entry.getValue());
        }
        writer.endNode();
    }

    protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
        if (writer instanceof NormalizedNodeStreamAttributeWriter) {
            ((NormalizedNodeStreamAttributeWriter) writer)
                    .startMapEntryNode(node.getIdentifier(), childSizeHint(node.getValue()), node.getAttributes());
        } else {
            writer.startMapEntryNode(node.getIdentifier(), childSizeHint(node.getValue()));
        }
        currentDepth++;
        writeMapEntryChildren(node);
        currentDepth--;
        return true;
    }

    private boolean wasProcessedAsCompositeNode(final NormalizedNode<?, ?> node) throws IOException {
        boolean processedAsCompositeNode = false;
        if (node instanceof ContainerNode) {
            final ContainerNode n = (ContainerNode) node;
            if (!n.getNodeType().equals(ROOT_DATA_QNAME)) {
                if (writer instanceof NormalizedNodeStreamAttributeWriter) {
                    ((NormalizedNodeStreamAttributeWriter) writer).startContainerNode(
                            n.getIdentifier(), childSizeHint(n.getValue()), n.getAttributes());
                } else {
                    writer.startContainerNode(n.getIdentifier(), childSizeHint(n.getValue()));
                }
                currentDepth++;
                processedAsCompositeNode = writeChildren(n.getValue(), false);
                currentDepth--;
            } else {
                // write child nodes of data root container
                for (final NormalizedNode<?, ?> child : n.getValue()) {
                    currentDepth++;
                    if (selectedByParameters(child, false)) {
                        write(child);
                    }
                    currentDepth--;
                    processedAsCompositeNode = true;
                }
            }
        } else if (node instanceof MapEntryNode) {
            processedAsCompositeNode = writeMapEntryNode((MapEntryNode) node);
        } else if (node instanceof UnkeyedListEntryNode) {
            final UnkeyedListEntryNode n = (UnkeyedListEntryNode) node;
            writer.startUnkeyedListItem(n.getIdentifier(), childSizeHint(n.getValue()));
            currentDepth++;
            processedAsCompositeNode = writeChildren(n.getValue(), false);
            currentDepth--;
        } else if (node instanceof ChoiceNode) {
            final ChoiceNode n = (ChoiceNode) node;
            writer.startChoiceNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n.getValue(), true);
        } else if (node instanceof AugmentationNode) {
            final AugmentationNode n = (AugmentationNode) node;
            writer.startAugmentationNode(n.getIdentifier());
            processedAsCompositeNode = writeChildren(n.getValue(), true);
        } else if (node instanceof UnkeyedListNode) {
            final UnkeyedListNode n = (UnkeyedListNode) node;
            writer.startUnkeyedList(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n.getValue(), false);
        } else if (node instanceof OrderedMapNode) {
            final OrderedMapNode n = (OrderedMapNode) node;
            writer.startOrderedMapNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n.getValue(), true);
        } else if (node instanceof MapNode) {
            final MapNode n = (MapNode) node;
            writer.startMapNode(n.getIdentifier(), childSizeHint(n.getValue()));
            processedAsCompositeNode = writeChildren(n.getValue(), true);
        } else if (node instanceof LeafSetNode) {
            final LeafSetNode<?> n = (LeafSetNode<?>) node;
            if (node instanceof OrderedLeafSetNode) {
                writer.startOrderedLeafSet(n.getIdentifier(), childSizeHint(n.getValue()));
            } else {
                writer.startLeafSet(n.getIdentifier(), childSizeHint(n.getValue()));
            }
            currentDepth++;
            processedAsCompositeNode = writeChildren(n.getValue(), true);
            currentDepth--;
        }

        return processedAsCompositeNode;
    }

    private static final class OrderedParameterAwareNormalizedNodeWriter extends ParameterAwareNormalizedNodeWriter {
        private static final Logger LOG = LoggerFactory.getLogger(OrderedParameterAwareNormalizedNodeWriter.class);

        OrderedParameterAwareNormalizedNodeWriter(final NormalizedNodeStreamWriter writer, final Integer maxDepth,
                                                  final List<Set<QName>> fields) {
            super(writer, maxDepth, fields);
        }

        @Override
        protected boolean writeMapEntryNode(final MapEntryNode node) throws IOException {
            final NormalizedNodeStreamWriter writer = getWriter();
            if (writer instanceof NormalizedNodeStreamAttributeWriter) {
                ((NormalizedNodeStreamAttributeWriter) writer).startMapEntryNode(
                        node.getIdentifier(), childSizeHint(node.getValue()), node.getAttributes());
            } else {
                writer.startMapEntryNode(node.getIdentifier(), childSizeHint(node.getValue()));
            }

            final Set<QName> qnames = node.getIdentifier().getKeyValues().keySet();
            // Write out all the key children
            currentDepth++;
            for (final QName qname : qnames) {
                final Optional<? extends NormalizedNode<?, ?>> child = node.getChild(new NodeIdentifier(qname));
                if (child.isPresent()) {
                    if (selectedByParameters(child.get(), false)) {
                        write(child.get());
                    }
                } else {
                    LOG.info("No child for key element {} found", qname);
                }
            }
            currentDepth--;

            currentDepth++;
            // Write all the rest
            final boolean result =
                    writeChildren(Iterables.filter(node.getValue(), input -> {
                        if (input instanceof AugmentationNode) {
                            return true;
                        }
                        if (!qnames.contains(input.getNodeType())) {
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
