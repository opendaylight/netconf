/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;
import org.opendaylight.yangtools.yang.xpath.api.YangXPathExpression.QualifiedBound;

/**
 * Simple proxy for container like schema nodes, where user provides a collection of children schema nodes.
 */
public final class NodeContainerProxy implements ContainerSchemaNode {

    private final Map<QName, ? extends DataSchemaNode> childNodes;
    private final QName qualifiedName;
    private final Collection<? extends AugmentationSchemaNode> availableAugmentations;

    public NodeContainerProxy(final QName qualifiedName, final Map<QName, ? extends DataSchemaNode> childNodes,
                              final Collection<? extends AugmentationSchemaNode> availableAugmentations) {
        this.availableAugmentations = availableAugmentations;
        this.childNodes = requireNonNull(childNodes, "childNodes");
        this.qualifiedName = qualifiedName;
    }

    public NodeContainerProxy(final QName qualifiedName, final Collection<? extends DataSchemaNode> childNodes) {
        this(qualifiedName, asMap(childNodes), Collections.emptySet());
    }

    public NodeContainerProxy(final QName qualifiedName, final Collection<? extends DataSchemaNode> childNodes,
                              final Collection<? extends AugmentationSchemaNode> availableAugmentations) {
        this(qualifiedName, asMap(childNodes), availableAugmentations);
    }

    private static Map<QName, ? extends DataSchemaNode> asMap(final Collection<? extends DataSchemaNode> childNodes) {
        return Maps.uniqueIndex(childNodes, DataSchemaNode::getQName);
    }

    @Override
    public Collection<? extends TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public Collection<? extends DataSchemaNode> getChildNodes() {
        return childNodes.values();
    }

    @Override
    public Collection<? extends GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return Optional.ofNullable(childNodes.get(name));
    }

    @Override
    public Collection<? extends UsesNode> getUses() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPresenceContainer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AugmentationSchemaNode> getAvailableAugmentations() {
        return availableAugmentations;
    }

    @Override
    @Deprecated
    public boolean isAugmenting() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public boolean isAddedByUses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QName getQName() {
        return qualifiedName;
    }

    @Override
    @Deprecated
    public SchemaPath getPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> getDescription() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> getReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Status getStatus() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends NotificationDefinition> getNotifications() {
        return Collections.emptySet();
    }

    @Override
    public Collection<? extends ActionDefinition> getActions() {
        return Collections.emptySet();
    }

    @Override
    public Optional<? extends QualifiedBound> getWhenCondition() {
        return Optional.empty();
    }

    @Override
    public Collection<? extends MustDefinition> getMustConstraints() {
        return Collections.emptySet();
    }

    @Override
    public ContainerEffectiveStatement asEffectiveStatement() {
        throw new UnsupportedOperationException();
    }
}
