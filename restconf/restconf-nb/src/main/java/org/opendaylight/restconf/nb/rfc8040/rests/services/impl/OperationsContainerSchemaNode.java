/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.util.CollectionWrappers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.DataTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;

@Deprecated(forRemoval = true, since = "4.0.0")
final class OperationsContainerSchemaNode extends AbstractOperationDataSchemaNode<ContainerStatement>
        implements ContainerSchemaNode, ContainerEffectiveStatement {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    static final @NonNull QName QNAME = QName.create(OperationsRestconfModule.NAMESPACE, "operations");

    private final Map<QName, OperationsLeafSchemaNode> children;

    OperationsContainerSchemaNode(final Collection<OperationsLeafSchemaNode> children) {
        this.children = Maps.uniqueIndex(children, OperationsLeafSchemaNode::getQName);
    }

    @Override
    public QName argument() {
        return QNAME;
    }

    @Override
    public List<? extends EffectiveStatement<?, ?>> effectiveSubstatements() {
        return CollectionWrappers.wrapAsList(children.values());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<DataSchemaNode> getChildNodes() {
        return (Collection) children.values();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<DataTreeEffectiveStatement<?>> dataTreeNodes() {
        return (Collection) children.values();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<SchemaTreeEffectiveStatement<?>> schemaTreeNodes() {
        return (Collection) children.values();
    }

    @Override
    public Optional<DataTreeEffectiveStatement<?>> findDataTreeNode(final QName qname) {
        return Optional.ofNullable(children.get(requireNonNull(qname)));
    }

    @Override
    public @NonNull Optional<SchemaTreeEffectiveStatement<?>> findSchemaTreeNode(final QName qname) {
        return Optional.ofNullable(children.get(requireNonNull(qname)));
    }

    @Override
    public DataSchemaNode dataChildByName(final QName name) {
        return children.get(requireNonNull(name));
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return Set.of();
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return Set.of();
    }

    @Override
    public Set<UsesNode> getUses() {
        return Set.of();
    }

    @Override
    public Set<AugmentationSchemaNode> getAvailableAugmentations() {
        return Set.of();
    }

    @Override
    public Set<NotificationDefinition> getNotifications() {
        return Set.of();
    }

    @Override
    public Set<ActionDefinition> getActions() {
        return Set.of();
    }

    @Override
    public Collection<@NonNull MustDefinition> getMustConstraints() {
        return Set.of();
    }

    @Override
    public boolean isPresenceContainer() {
        return false;
    }

    @Override
    public ContainerEffectiveStatement asEffectiveStatement() {
        return this;
    }
}
