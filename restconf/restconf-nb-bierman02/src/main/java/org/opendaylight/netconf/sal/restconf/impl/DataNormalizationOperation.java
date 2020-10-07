/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.AnyxmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.AugmentationTarget;
import org.opendaylight.yangtools.yang.model.api.CaseSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerLike;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.util.EffectiveAugmentationSchema;

abstract class DataNormalizationOperation<T extends PathArgument> implements Identifiable<T> {
    private final T identifier;

    @Override
    public T getIdentifier() {
        return identifier;
    }

    DataNormalizationOperation(final T identifier) {
        this.identifier = identifier;
    }

    static DataNormalizationOperation<?> from(final EffectiveModelContext ctx) {
        return new ContainerNormalization(ctx);
    }

    boolean isMixin() {
        return false;
    }

    Set<QName> getQNameIdentifiers() {
        return Collections.singleton(identifier.getNodeType());
    }

    abstract DataNormalizationOperation<?> getChild(PathArgument child) throws DataNormalizationException;

    abstract DataNormalizationOperation<?> getChild(QName child) throws DataNormalizationException;

    private abstract static class SimpleTypeNormalization<T extends PathArgument>
            extends DataNormalizationOperation<T> {
        SimpleTypeNormalization(final T identifier) {
            super(identifier);
        }

        @Override
        final DataNormalizationOperation<?> getChild(final PathArgument child) {
            return null;
        }

        @Override
        final DataNormalizationOperation<?> getChild(final QName child) {
            return null;
        }
    }

    private static final class LeafNormalization extends SimpleTypeNormalization<NodeIdentifier> {
        LeafNormalization(final LeafSchemaNode potential) {
            super(new NodeIdentifier(potential.getQName()));
        }
    }

    private static final class LeafListEntryNormalization extends SimpleTypeNormalization<NodeWithValue> {
        LeafListEntryNormalization(final LeafListSchemaNode potential) {
            super(new NodeWithValue(potential.getQName(), null));
        }
    }

    private abstract static class DataContainerNormalizationOperation<T extends PathArgument>
            extends DataNormalizationOperation<T> {
        private final DataNodeContainer schema;
        private final Map<QName, DataNormalizationOperation<?>> byQName;
        private final Map<PathArgument, DataNormalizationOperation<?>> byArg;

        DataContainerNormalizationOperation(final T identifier, final DataNodeContainer schema) {
            super(identifier);
            this.schema = schema;
            this.byArg = new ConcurrentHashMap<>();
            this.byQName = new ConcurrentHashMap<>();
        }

        @Override
        DataNormalizationOperation<?> getChild(final PathArgument child) throws DataNormalizationException {
            DataNormalizationOperation<?> potential = byArg.get(child);
            if (potential != null) {
                return potential;
            }
            potential = fromLocalSchema(child);
            return register(potential);
        }

        @Override
        DataNormalizationOperation<?> getChild(final QName child) throws DataNormalizationException {
            DataNormalizationOperation<?> potential = byQName.get(child);
            if (potential != null) {
                return potential;
            }
            potential = fromLocalSchemaAndQName(schema, child);
            return register(potential);
        }

        private DataNormalizationOperation<?> fromLocalSchema(final PathArgument child)
                throws DataNormalizationException {
            if (child instanceof AugmentationIdentifier) {
                return fromSchemaAndQNameChecked(schema, ((AugmentationIdentifier) child).getPossibleChildNames()
                        .iterator().next());
            }
            return fromSchemaAndQNameChecked(schema, child.getNodeType());
        }

        DataNormalizationOperation<?> fromLocalSchemaAndQName(final DataNodeContainer schema2,
                final QName child) throws DataNormalizationException {
            return fromSchemaAndQNameChecked(schema2, child);
        }

        private DataNormalizationOperation<?> register(final DataNormalizationOperation<?> potential) {
            if (potential != null) {
                byArg.put(potential.getIdentifier(), potential);
                for (final QName qname : potential.getQNameIdentifiers()) {
                    byQName.put(qname, potential);
                }
            }
            return potential;
        }

        private static DataNormalizationOperation<?> fromSchemaAndQNameChecked(final DataNodeContainer schema,
                final QName child) throws DataNormalizationException {

            final DataSchemaNode result = findChildSchemaNode(schema, child);
            if (result == null) {
                throw new DataNormalizationException(String.format(
                        "Supplied QName %s is not valid according to schema %s, potential children nodes: %s", child,
                        schema,schema.getChildNodes()));
            }

            // We try to look up if this node was added by augmentation
            if (schema instanceof DataSchemaNode && result.isAugmenting()) {
                return fromAugmentation(schema, (AugmentationTarget) schema, result);
            }
            return fromDataSchemaNode(result);
        }
    }

    private static final class ListItemNormalization extends
            DataContainerNormalizationOperation<NodeIdentifierWithPredicates> {
        ListItemNormalization(final NodeIdentifierWithPredicates identifier, final ListSchemaNode schema) {
            super(identifier, schema);
        }
    }

    private static final class UnkeyedListItemNormalization
            extends DataContainerNormalizationOperation<NodeIdentifier> {
        UnkeyedListItemNormalization(final ListSchemaNode schema) {
            super(new NodeIdentifier(schema.getQName()), schema);
        }
    }

    private static final class ContainerNormalization extends DataContainerNormalizationOperation<NodeIdentifier> {
        ContainerNormalization(final ContainerLike schema) {
            super(new NodeIdentifier(schema.getQName()), schema);
        }
    }

    private abstract static class MixinNormalizationOp<T extends PathArgument> extends DataNormalizationOperation<T> {
        MixinNormalizationOp(final T identifier) {
            super(identifier);
        }

        @Override
        final boolean isMixin() {
            return true;
        }
    }

    private static final class LeafListMixinNormalization extends MixinNormalizationOp<NodeIdentifier> {
        private final DataNormalizationOperation<?> innerOp;

        LeafListMixinNormalization(final LeafListSchemaNode potential) {
            super(new NodeIdentifier(potential.getQName()));
            innerOp = new LeafListEntryNormalization(potential);
        }

        @Override
        DataNormalizationOperation<?> getChild(final PathArgument child) {
            if (child instanceof NodeWithValue) {
                return innerOp;
            }
            return null;
        }

        @Override
        DataNormalizationOperation<?> getChild(final QName child) {
            if (getIdentifier().getNodeType().equals(child)) {
                return innerOp;
            }
            return null;
        }
    }

    private static final class AugmentationNormalization
            extends DataContainerNormalizationOperation<AugmentationIdentifier> {

        AugmentationNormalization(final AugmentationSchemaNode augmentation, final DataNodeContainer schema) {
            super(augmentationIdentifierFrom(augmentation), augmentationProxy(augmentation,schema));
        }

        private static DataNodeContainer augmentationProxy(final AugmentationSchemaNode augmentation,
                final DataNodeContainer schema) {
            final Set<DataSchemaNode> children = new HashSet<>();
            for (final DataSchemaNode augNode : augmentation.getChildNodes()) {
                children.add(schema.getDataChildByName(augNode.getQName()));
            }
            return new EffectiveAugmentationSchema(augmentation, children);
        }

        @Override
        boolean isMixin() {
            return true;
        }

        @Override
        DataNormalizationOperation<?> fromLocalSchemaAndQName(final DataNodeContainer schema, final QName child) {
            final DataSchemaNode result = findChildSchemaNode(schema, child);
            if (result == null) {
                return null;
            }

            // We try to look up if this node was added by augmentation
            if (schema instanceof DataSchemaNode && result.isAugmenting()) {
                return fromAugmentation(schema, (AugmentationTarget) schema, result);
            }
            return fromDataSchemaNode(result);
        }

        @Override
        Set<QName> getQNameIdentifiers() {
            return getIdentifier().getPossibleChildNames();
        }

        private static AugmentationIdentifier augmentationIdentifierFrom(final AugmentationSchemaNode augmentation) {
            final ImmutableSet.Builder<QName> potentialChildren = ImmutableSet.builder();
            for (final DataSchemaNode child : augmentation.getChildNodes()) {
                potentialChildren.add(child.getQName());
            }
            return new AugmentationIdentifier(potentialChildren.build());
        }
    }

    private static final class MapMixinNormalization extends MixinNormalizationOp<NodeIdentifier> {
        private final ListItemNormalization innerNode;

        MapMixinNormalization(final ListSchemaNode list) {
            super(new NodeIdentifier(list.getQName()));
            this.innerNode = new ListItemNormalization(NodeIdentifierWithPredicates.of(list.getQName(),
                    Collections.<QName, Object>emptyMap()), list);
        }

        @Override
        DataNormalizationOperation<?> getChild(final PathArgument child) {
            if (child.getNodeType().equals(getIdentifier().getNodeType())) {
                return innerNode;
            }
            return null;
        }

        @Override
        DataNormalizationOperation<?> getChild(final QName child) {
            if (getIdentifier().getNodeType().equals(child)) {
                return innerNode;
            }
            return null;
        }
    }

    private static final class UnkeyedListMixinNormalization extends MixinNormalizationOp<NodeIdentifier> {
        private final UnkeyedListItemNormalization innerNode;

        UnkeyedListMixinNormalization(final ListSchemaNode list) {
            super(new NodeIdentifier(list.getQName()));
            this.innerNode = new UnkeyedListItemNormalization(list);
        }

        @Override
        DataNormalizationOperation<?> getChild(final PathArgument child) {
            if (child.getNodeType().equals(getIdentifier().getNodeType())) {
                return innerNode;
            }
            return null;
        }

        @Override
        DataNormalizationOperation<?> getChild(final QName child) {
            if (getIdentifier().getNodeType().equals(child)) {
                return innerNode;
            }
            return null;
        }
    }

    private static final class ChoiceNodeNormalization extends MixinNormalizationOp<NodeIdentifier> {
        private final ImmutableMap<QName, DataNormalizationOperation<?>> byQName;
        private final ImmutableMap<PathArgument, DataNormalizationOperation<?>> byArg;

        ChoiceNodeNormalization(final ChoiceSchemaNode schema) {
            super(new NodeIdentifier(schema.getQName()));
            final ImmutableMap.Builder<QName, DataNormalizationOperation<?>> byQNameBuilder = ImmutableMap.builder();
            final ImmutableMap.Builder<PathArgument, DataNormalizationOperation<?>> byArgBuilder =
                    ImmutableMap.builder();

            for (final CaseSchemaNode caze : schema.getCases()) {
                for (final DataSchemaNode cazeChild : caze.getChildNodes()) {
                    final DataNormalizationOperation<?> childOp = fromDataSchemaNode(cazeChild);
                    byArgBuilder.put(childOp.getIdentifier(), childOp);
                    for (final QName qname : childOp.getQNameIdentifiers()) {
                        byQNameBuilder.put(qname, childOp);
                    }
                }
            }
            byQName = byQNameBuilder.build();
            byArg = byArgBuilder.build();
        }

        @Override
        DataNormalizationOperation<?> getChild(final PathArgument child) {
            return byArg.get(child);
        }

        @Override
        DataNormalizationOperation<?> getChild(final QName child) {
            return byQName.get(child);
        }
    }

    private static final class AnyxmlNormalization extends SimpleTypeNormalization<NodeIdentifier> {
        AnyxmlNormalization(final AnyxmlSchemaNode schema) {
            super(new NodeIdentifier(schema.getQName()));
        }
    }

    private static @Nullable DataSchemaNode findChildSchemaNode(final DataNodeContainer parent, final QName child) {
        final DataSchemaNode potential = parent.getDataChildByName(child);
        return potential != null ? potential : findChoice(parent, child);
    }

    private static @Nullable ChoiceSchemaNode findChoice(final DataNodeContainer parent, final QName child) {
        for (final ChoiceSchemaNode choice : Iterables.filter(parent.getChildNodes(), ChoiceSchemaNode.class)) {
            for (final CaseSchemaNode caze : choice.getCases()) {
                if (findChildSchemaNode(caze, child) != null) {
                    return choice;
                }
            }
        }
        return null;
    }

    /**
     * Returns a DataNormalizationOperation for provided child node.
     *
     * <p>
     * If supplied child is added by Augmentation this operation returns
     * a DataNormalizationOperation for augmentation,
     * otherwise returns a DataNormalizationOperation for child as
     * call for {@link #fromDataSchemaNode(DataSchemaNode)}.
     */
    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static DataNormalizationOperation<?> fromAugmentation(final DataNodeContainer parent,
            final AugmentationTarget parentAug, final DataSchemaNode child) {
        AugmentationSchemaNode augmentation = null;
        for (final AugmentationSchemaNode aug : parentAug.getAvailableAugmentations()) {
            final DataSchemaNode potential = aug.getDataChildByName(child.getQName());
            if (potential != null) {
                augmentation = aug;
                break;
            }

        }
        if (augmentation != null) {
            return new AugmentationNormalization(augmentation, parent);
        } else {
            return fromDataSchemaNode(child);
        }
    }

    static DataNormalizationOperation<?> fromDataSchemaNode(final DataSchemaNode potential) {
        if (potential instanceof ContainerSchemaNode) {
            return new ContainerNormalization((ContainerSchemaNode) potential);
        } else if (potential instanceof ListSchemaNode) {
            return fromListSchemaNode((ListSchemaNode) potential);
        } else if (potential instanceof LeafSchemaNode) {
            return new LeafNormalization((LeafSchemaNode) potential);
        } else if (potential instanceof ChoiceSchemaNode) {
            return new ChoiceNodeNormalization((ChoiceSchemaNode) potential);
        } else if (potential instanceof LeafListSchemaNode) {
            return new LeafListMixinNormalization((LeafListSchemaNode) potential);
        } else if (potential instanceof AnyxmlSchemaNode) {
            return new AnyxmlNormalization((AnyxmlSchemaNode) potential);
        }
        return null;
    }

    private static DataNormalizationOperation<?> fromListSchemaNode(final ListSchemaNode potential) {
        if (potential.getKeyDefinition().isEmpty()) {
            return new UnkeyedListMixinNormalization(potential);
        }
        return new MapMixinNormalization(potential);
    }
}
