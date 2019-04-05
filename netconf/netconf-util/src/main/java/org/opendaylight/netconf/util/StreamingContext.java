/*
 * Copyright (c) 2018 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter.UNKNOWN_SIZE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.util.EffectiveAugmentationSchema;

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

        final DataSchemaNode result = potential.get();
        // We try to look up if this node was added by augmentation
        if (schema instanceof DataSchemaNode && result.isAugmenting()) {
            for (final AugmentationSchemaNode aug : ((AugmentationTarget)schema).getAvailableAugmentations()) {
                final DataSchemaNode found = aug.getDataChildByName(result.getQName());
                if (found != null) {
                    return new Augmentation(aug, schema);
                }
            }
        }
        return fromDataSchemaNode(result);
    }

    static StreamingContext<?> fromDataSchemaNode(final DataSchemaNode potential) {
        if (potential instanceof ContainerSchemaNode) {
            return new Container((ContainerSchemaNode) potential);
        } else if (potential instanceof ListSchemaNode) {
            return fromListSchemaNode((ListSchemaNode) potential);
        } else if (potential instanceof LeafSchemaNode) {
            return new Leaf((LeafSchemaNode) potential);
        } else if (potential instanceof ChoiceSchemaNode) {
            return new Choice((ChoiceSchemaNode) potential);
        } else if (potential instanceof LeafListSchemaNode) {
            return fromLeafListSchemaNode((LeafListSchemaNode) potential);
        } else if (potential instanceof AnyXmlSchemaNode) {
            return new AnyXml((AnyXmlSchemaNode) potential);
        }
        return null;
    }

    @Override
    public final T getIdentifier() {
        return identifier;
    }

    abstract StreamingContext<?> getChild(PathArgument child);

    abstract void streamToWriter(NormalizedNodeStreamWriter writer, PathArgument first, Iterator<PathArgument> others)
            throws IOException;

    abstract boolean isMixin();

    private static Optional<DataSchemaNode> findChildSchemaNode(final DataNodeContainer parent, final QName child) {
        DataSchemaNode potential = parent.getDataChildByName(child);
        if (potential == null) {
            potential = findChoice(Iterables.filter(parent.getChildNodes(), ChoiceSchemaNode.class), child);
        }
        return Optional.ofNullable(potential);
    }

    private static ChoiceSchemaNode findChoice(final Iterable<ChoiceSchemaNode> choices, final QName child) {
        for (final ChoiceSchemaNode choice : choices) {
            for (final CaseSchemaNode caze : choice.getCases().values()) {
                if (findChildSchemaNode(caze, child).isPresent()) {
                    return choice;
                }
            }
        }
        return null;
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
            if (!isMixin()) {
                final QName type = getIdentifier().getNodeType();
                if (type != null) {
                    final QName firstType = first.getNodeType();
                    checkArgument(type.equals(firstType), "Node QName must be %s was %s", type, firstType);
                }
            }

            emitElementStart(writer, first);
            if (others.hasNext()) {
                final PathArgument childPath = others.next();
                final StreamingContext<?> childOp = getChildOperation(childPath);
                childOp.streamToWriter(writer, childPath, others);
            }
            writer.endNode();
        }

        abstract void emitElementStart(NormalizedNodeStreamWriter writer, PathArgument arg) throws IOException;

        @SuppressWarnings("checkstyle:illegalCatch")
        private StreamingContext<?> getChildOperation(final PathArgument childPath) {
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
            if (child instanceof AugmentationIdentifier) {
                return fromSchemaAndQNameChecked(schema, ((AugmentationIdentifier) child).getPossibleChildNames()
                        .iterator().next());
            }
            return fromSchemaAndQNameChecked(schema, child.getNodeType());
        }
    }

    private abstract static class AbstractMapMixin extends AbstractComposite<NodeIdentifier> {
        private final ListEntry innerNode;

        AbstractMapMixin(final ListSchemaNode list) {
            super(NodeIdentifier.create(list.getQName()));
            this.innerNode = new ListEntry(new NodeIdentifierWithPredicates(list.getQName(), ImmutableMap.of()), list);
        }

        @Override
        final StreamingContext<?> getChild(final PathArgument child) {
            return child.getNodeType().equals(getIdentifier().getNodeType()) ? innerNode : null;
        }

        @Override
        final boolean isMixin() {
            return true;
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
    }

    private static final class AnyXml extends AbstractSimple<NodeIdentifier> {
        AnyXml(final AnyXmlSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()));
        }

        @Override
        void streamToWriter(final NormalizedNodeStreamWriter writer, final PathArgument first,
                final Iterator<PathArgument> others) throws IOException {
            writer.startAnyxmlNode(getIdentifier());
            // FIXME: why are we not emitting a value?
            writer.endNode();
        }
    }

    private static final class Choice extends AbstractComposite<NodeIdentifier> {
        private final ImmutableMap<PathArgument, StreamingContext<?>> byArg;

        Choice(final ChoiceSchemaNode schema) {
            super(NodeIdentifier.create(schema.getQName()));
            final ImmutableMap.Builder<PathArgument, StreamingContext<?>> byArgBuilder = ImmutableMap.builder();

            for (final CaseSchemaNode caze : schema.getCases().values()) {
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startChoiceNode(getIdentifier(), UNKNOWN_SIZE);
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
            super(new NodeWithValue<>(potential.getQName(), null));
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            final NodeIdentifierWithPredicates identifier = (NodeIdentifierWithPredicates) arg;
            writer.startMapEntryNode(identifier, UNKNOWN_SIZE);

            for (Entry<QName, Object> entry : identifier.getKeyValues().entrySet()) {
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startUnkeyedListItem(getIdentifier(), UNKNOWN_SIZE);
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startContainerNode(getIdentifier(), UNKNOWN_SIZE);
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startOrderedLeafSet(getIdentifier(), UNKNOWN_SIZE);
        }
    }

    private static class UnorderedLeafListMixin extends LeafListMixin {
        UnorderedLeafListMixin(final LeafListSchemaNode potential) {
            super(potential);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startLeafSet(getIdentifier(), UNKNOWN_SIZE);
        }
    }

    private static final class Augmentation extends AbstractDataContainer<AugmentationIdentifier> {
        Augmentation(final AugmentationSchemaNode augmentation, final DataNodeContainer schema) {
            super(DataSchemaContextNode.augmentationIdentifierFrom(augmentation),
                    EffectiveAugmentationSchema.create(augmentation, schema));
        }

        @Override
        boolean isMixin() {
            return true;
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startAugmentationNode(getIdentifier());
        }
    }

    private static final class UnorderedMapMixin extends AbstractMapMixin {
        UnorderedMapMixin(final ListSchemaNode list) {
            super(list);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startMapNode(getIdentifier(), UNKNOWN_SIZE);
        }
    }

    private static final class OrderedMapMixin extends AbstractMapMixin {
        OrderedMapMixin(final ListSchemaNode list) {
            super(list);
        }

        @Override
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startOrderedMapNode(getIdentifier(), UNKNOWN_SIZE);
        }
    }

    private static final class UnkeyedListMixin extends AbstractComposite<NodeIdentifier> {
        private final UnkeyedListItem innerNode;

        UnkeyedListMixin(final ListSchemaNode list) {
            super(NodeIdentifier.create(list.getQName()));
            this.innerNode = new UnkeyedListItem(list);
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
        void emitElementStart(final NormalizedNodeStreamWriter writer, final PathArgument arg) throws IOException {
            writer.startUnkeyedList(getIdentifier(), UNKNOWN_SIZE);
        }
    }
}
