/*
 * Copyright (c) 2018 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

abstract class StreamingContext<T extends PathArgument> implements Identifiable<T> {
    private final T identifier;

    StreamingContext(final T identifier) {
        this.identifier = identifier;
    }

    static StreamingContext<?> fromSchemaAndQNameChecked(final DataNodeContainer schema, final QName child) {
        final Optional<DataSchemaNode> potential = findChildSchemaNode(schema, child);
        checkArgument(potential.isPresent(),
                "Supplied QName %s is not valid according to schema %s, potential children nodes: %s", child, schema,
                schema.getChildNodes());
        return fromDataSchemaNode(potential.orElseThrow());
    }

    static StreamingContext<?> fromDataSchemaNode(final DataSchemaNode potential) {
        if (potential instanceof ContainerSchemaNode container) {
            return new Container(container);
        } else if (potential instanceof ListSchemaNode list) {
            return fromListSchemaNode(list);
        } else if (potential instanceof LeafSchemaNode leaf) {
            return new Leaf(leaf);
        } else if (potential instanceof ChoiceSchemaNode choice) {
            return new Choice(choice);
        } else if (potential instanceof LeafListSchemaNode leafList) {
            return fromLeafListSchemaNode(leafList);
        } else if (potential instanceof AnyxmlSchemaNode anyxml) {
            return new AnyXml(anyxml);
        }
        // FIXME: unhandled anydata!
        return null;
    }

    @Override
    public final T getIdentifier() {
        return identifier;
    }

    abstract StreamingContext<?> getChild(PathArgument child);

    /**
     * Writing node structure that is described by series of {@link PathArgument}
     * into {@link NormalizedNodeStreamWriter}.
     *
     * @param writer output {@link NormalizedNode} writer
     * @param first  the first {@link PathArgument}
     * @param others iterator that points to next path arguments
     * @throws IOException failed to write a stream of path arguments into {@link NormalizedNodeStreamWriter}
     */
    abstract void streamToWriter(NormalizedNodeStreamWriter writer, PathArgument first, Iterator<PathArgument> others)
            throws IOException;

    /**
     * Writing node structure that is described by provided {@link PathNode} into {@link NormalizedNodeStreamWriter}.
     *
     * @param writer output {@link NormalizedNode} writer
     * @param first  the first {@link PathArgument}
     * @param tree   subtree of path arguments that starts with the first path argument
     * @throws IOException failed to write a stream of path arguments into {@link NormalizedNodeStreamWriter}
     */
    abstract void streamToWriter(NormalizedNodeStreamWriter writer, PathArgument first, PathNode tree)
            throws IOException;

    abstract boolean isMixin();

    private static Optional<DataSchemaNode> findChildSchemaNode(final DataNodeContainer parent, final QName child) {
        final Optional<DataSchemaNode> potential = parent.findDataChildByName(child);
        return potential.isPresent() ? potential
                : findChoice(Iterables.filter(parent.getChildNodes(), ChoiceSchemaNode.class), child);
    }

    private static Optional<DataSchemaNode> findChoice(final Iterable<ChoiceSchemaNode> choices, final QName child) {
        for (final ChoiceSchemaNode choice : choices) {
            for (final CaseSchemaNode caze : choice.getCases()) {
                if (findChildSchemaNode(caze, child).isPresent()) {
                    return Optional.of(choice);
                }
            }
        }
        return Optional.empty();
    }

    private static StreamingContext<?> fromListSchemaNode(final ListSchemaNode potential) {
        final List<QName> keyDefinition = potential.getKeyDefinition();
        if (keyDefinition == null || keyDefinition.isEmpty()) {
            return new UnkeyedListMixin(potential);
        }
        return potential.isUserOrdered() ? new OrderedMapMixin(potential)
                : new UnorderedMapMixin(potential);
    }

    private static StreamingContext<?> fromLeafListSchemaNode(final LeafListSchemaNode potential) {
        return potential.isUserOrdered() ? new OrderedLeafListMixin(potential)
                : new UnorderedLeafListMixin(potential);
    }

    private abstract static class AbstractComposite<T extends PathArgument> extends StreamingContext<T> {
        AbstractComposite(final T identifier) {
            super(identifier);
        }

        @Override
        final void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                final Iterator<PathArgument> others) throws IOException {
            verifyActualPathArgument(first);

            emitElementStart(writer, first, others.hasNext() ? 1 : 0);
            if (others.hasNext()) {
                final PathArgument childPath = others.next();
                final StreamingContext<?> childOp = getChildOperation(childPath);
                childOp.streamToWriter(writer, childPath, others);
            }
            writer.endNode();
        }

        @Override
        final void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                                  final PathNode subtree) throws IOException {
            verifyActualPathArgument(first);

            final Collection<PathNode> children = subtree.children();
            emitElementStart(writer, first, children.size());
            for (final PathNode node : subtree.children()) {
                emitChildTreeNode(writer, node);
            }
            writer.endNode();
        }

        void emitChildTreeNode(final NormalizedNodeStreamWriter writer, final PathNode node) throws IOException {
            final PathArgument childPath = node.element();
            getChildOperation(childPath).streamToWriter(writer, childPath, node);
        }

        private void verifyActualPathArgument(final PathArgument first) {
            if (!isMixin()) {
                final QName type = getIdentifier().getNodeType();
                final QName firstType = first.getNodeType();
                checkArgument(type.equals(firstType), "Node QName must be %s was %s", type, firstType);
            }
        }

        abstract void emitElementStart(NormalizedNodeStreamWriter writer, PathArgument arg,
                                       int childSizeHint) throws IOException;

        @SuppressWarnings("checkstyle:illegalCatch")
        StreamingContext<?> getChildOperation(final PathArgument childPath) {
            final StreamingContext<?> childOp;
            try {
                childOp = getChild(childPath);
            } catch (final RuntimeException e) {
                throw new IllegalArgumentException(String.format("Failed to process child node %s", childPath), e);
            }
            checkArgument(childOp != null, "Node %s is not allowed inside %s", childPath, getIdentifier());
            return childOp;
        }
    }

    private abstract static class AbstractDataContainer<T extends PathArgument> extends AbstractComposite<T> {
        private final Map<PathArgument, StreamingContext<?>> byArg = new HashMap<>();
        private final DataNodeContainer schema;

        AbstractDataContainer(final T identifier, final DataNodeContainer schema) {
            super(identifier);
            this.schema = schema;
        }

        @Override
        final StreamingContext<?> getChild(final PathArgument child) {
            StreamingContext<?> potential = byArg.get(child);
            if (potential != null) {
                return potential;
            }
            potential = fromLocalSchema(child);
            if (potential != null) {
                byArg.put(potential.getIdentifier(), potential);
            }
            return potential;
        }

        private StreamingContext<?> fromLocalSchema(final PathArgument child) {
            return fromSchemaAndQNameChecked(schema, child.getNodeType());
        }
    }

    private abstract static class AbstractMapMixin extends AbstractComposite<NodeIdentifier> {
        private final ListEntry innerNode;
        private final List<QName> keyLeaves;

        AbstractMapMixin(final ListSchemaNode list) {
            super(NodeIdentifier.create(list.getQName()));
            innerNode = new ListEntry(NodeIdentifierWithPredicates.of(list.getQName()), list);
            keyLeaves = list.getKeyDefinition();
        }

        @Override
        final StreamingContext<?> getChild(final PathArgument child) {
            return child.getNodeType().equals(getIdentifier().getNodeType()) ? innerNode : null;
        }

        @Override
        final boolean isMixin() {
            return true;
        }

        @Override
        final void emitChildTreeNode(final NormalizedNodeStreamWriter writer, final PathNode node) throws IOException {
            final PathArgument element = node.element();
            if (!(element instanceof NodeIdentifierWithPredicates childPath)) {
                throw new IOException("Child identifier " + element + " is invalid in parent " + getIdentifier());
            }

            final StreamingContext<?> childOp = getChildOperation(childPath);
            if (childPath.size() == 0 && node.isEmpty() || childPath.keySet().containsAll(keyLeaves)) {
                // This is a query for the entire list, or the query specifies everything we need
                childOp.streamToWriter(writer, childPath, node);
                return;
            }

            // Inexact query, we need to also request the leaf nodes we need to for reconstructing a valid instance
            // NodeIdentifierWithPredicates.
            childOp.streamToWriter(writer, childPath, node.copyWith(keyLeaves.stream()
                .filter(qname -> !childPath.containsKey(qname))
                .map(NodeIdentifier::new)
                .collect(Collectors.toUnmodifiableList())));
        }
    }

    private abstract static class AbstractSimple<T extends PathArgument> extends StreamingContext<T> {
        AbstractSimple(final T identifier) {
            super(identifier);
        }

        @Override
        final StreamingContext<?> getChild(final PathArgument child) {
            return null;
        }

        @Override
        final boolean isMixin() {
            return false;
        }

        @Override
        final void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                            final PathNode tree) throws IOException {
            streamToWriter(writer, first, Collections.emptyIterator());
        }
    }

    private static final class AnyXml extends AbstractSimple<NodeIdentifier> {
        AnyXml(final AnyxmlSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()));
        }

        @Override
        void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                final Iterator<PathArgument> others) throws IOException {
            writer.startAnyxmlNode(getIdentifier(), DOMSource.class);
            // FIXME: why are we not emitting a value?
            writer.endNode();
        }
    }

    private static final class Choice extends AbstractComposite<NodeIdentifier> {
        private final ImmutableMap<PathArgument, StreamingContext<?>> byArg;

        Choice(final ChoiceSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()));
            final ImmutableMap.Builder<PathArgument, StreamingContext<?>> byArgBuilder = ImmutableMap.builder();

            for (final CaseSchemaNode caze : schema.getCases()) {
                for (final DataSchemaNode cazeChild : caze.getChildNodes()) {
                    final StreamingContext<?> childOp = fromDataSchemaNode(cazeChild);
                    byArgBuilder.put(childOp.getIdentifier(), childOp);
                }
            }
            byArg = byArgBuilder.build();
        }

        @Override
        StreamingContext<?> getChild(final PathArgument child) {
            return byArg.get(child);
        }

        @Override
        boolean isMixin() {
            return true;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startChoiceNode(getIdentifier(), childSizeHint);
        }
    }

    private static final class Leaf extends AbstractSimple<NodeIdentifier> {
        Leaf(final LeafSchemaNode potential) {
            super(new NodeIdentifier(potential.getQName()));
        }

        @Override
        void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                final Iterator<PathArgument> others) throws IOException {
            writer.startLeafNode(getIdentifier());
            // FIXME: why are we not emitting a value?
            writer.endNode();
        }
    }

    private static final class LeafListEntry extends AbstractSimple<NodeWithValue<?>> {
        LeafListEntry(final LeafListSchemaNode potential) {
            super(new NodeWithValue<>(potential.getQName(), Empty.value()));
        }

        @Override
        void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                final Iterator<PathArgument> others) throws IOException {
            checkArgument(first instanceof NodeWithValue);
            final NodeWithValue<?> identifier = (NodeWithValue<?>) first;
            writer.startLeafSetEntryNode(identifier);
            writer.scalarValue(identifier.getValue());
            writer.endNode();
        }
    }

    private static final class ListEntry extends AbstractDataContainer<NodeIdentifierWithPredicates> {
        ListEntry(final NodeIdentifierWithPredicates identifier, final ListSchemaNode schema) {
            super(identifier, schema);
        }

        @Override
        boolean isMixin() {
            return false;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            final NodeIdentifierWithPredicates identifier = (NodeIdentifierWithPredicates) arg;
            writer.startMapEntryNode(identifier, childSizeHint);

            for (Entry<QName, Object> entry : identifier.entrySet()) {
                writer.startLeafNode(new NodeIdentifier(entry.getKey()));
                writer.scalarValue(entry.getValue());
                writer.endNode();
            }
        }
    }

    private static final class UnkeyedListItem extends AbstractDataContainer<NodeIdentifier> {
        UnkeyedListItem(final ListSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()), schema);
        }

        @Override
        boolean isMixin() {
            return false;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startUnkeyedListItem(getIdentifier(), childSizeHint);
        }
    }

    private static final class Container extends AbstractDataContainer<NodeIdentifier> {
        Container(final ContainerSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()), schema);
        }

        @Override
        boolean isMixin() {
            return false;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startContainerNode(getIdentifier(), childSizeHint);
        }
    }

    private abstract static class LeafListMixin extends AbstractComposite<NodeIdentifier> {
        private final StreamingContext<?> innerOp;

        LeafListMixin(final LeafListSchemaNode potential) {
            super(NodeIdentifier.create(potential.getQName()));
            innerOp = new LeafListEntry(potential);
        }

        @Override
        final StreamingContext<?> getChild(final PathArgument child) {
            return child instanceof NodeWithValue ? innerOp : null;
        }

        @Override
        final boolean isMixin() {
            return true;
        }
    }

    private static final class OrderedLeafListMixin extends LeafListMixin {
        OrderedLeafListMixin(final LeafListSchemaNode potential) {
            super(potential);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startOrderedLeafSet(getIdentifier(), childSizeHint);
        }
    }

    private static class UnorderedLeafListMixin extends LeafListMixin {
        UnorderedLeafListMixin(final LeafListSchemaNode potential) {
            super(potential);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startLeafSet(getIdentifier(), childSizeHint);
        }
    }

    private static final class UnorderedMapMixin extends AbstractMapMixin {
        UnorderedMapMixin(final ListSchemaNode list) {
            super(list);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startMapNode(getIdentifier(), childSizeHint);
        }
    }

    private static final class OrderedMapMixin extends AbstractMapMixin {
        OrderedMapMixin(final ListSchemaNode list) {
            super(list);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startOrderedMapNode(getIdentifier(), childSizeHint);
        }
    }

    private static final class UnkeyedListMixin extends AbstractComposite<NodeIdentifier> {
        private final UnkeyedListItem innerNode;

        UnkeyedListMixin(final ListSchemaNode list) {
            super(NodeIdentifier.create(list.getQName()));
            innerNode = new UnkeyedListItem(list);
        }

        @Override
        StreamingContext<?> getChild(final PathArgument child) {
            return child.getNodeType().equals(getIdentifier().getNodeType()) ? innerNode : null;
        }

        @Override
        boolean isMixin() {
            return true;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg,
                              final int childSizeHint) throws IOException {
            writer.startUnkeyedList(getIdentifier(), childSizeHint);
        }
    }
}
