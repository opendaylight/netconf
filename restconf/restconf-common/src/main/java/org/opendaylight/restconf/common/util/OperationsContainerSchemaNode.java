/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;

final class OperationsContainerSchemaNode extends AbstractOperationDataSchemaNode implements ContainerSchemaNode {
    // There is no need to intern this nor add a revision, as we are providing the corresponding context anyway
    private static final @NonNull QName QNAME = QName.create(OperationsRestconfModule.NAMESPACE, "operations");
    static final @NonNull SchemaPath PATH = SchemaPath.create(true, QNAME);

    private final Map<QName, OperationsLeafSchemaNode> children;

    OperationsContainerSchemaNode(final Collection<OperationsLeafSchemaNode> children) {
        this.children = Maps.uniqueIndex(children, OperationsLeafSchemaNode::getQName);
    }

    @Override
    public QName getQName() {
        return QNAME;
    }

    @Override
    @Deprecated
    public SchemaPath getPath() {
        return PATH;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<DataSchemaNode> getChildNodes() {
        return (Collection) children.values();
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return Optional.ofNullable(children.get(requireNonNull(name)));
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public Set<UsesNode> getUses() {
        return Collections.emptySet();
    }

    @Override
    public Set<AugmentationSchemaNode> getAvailableAugmentations() {
        return Collections.emptySet();
    }

    @Override
    public Set<NotificationDefinition> getNotifications() {
        return Collections.emptySet();
    }

    @Override
    public Set<ActionDefinition> getActions() {
        return Collections.emptySet();
    }

    @Override
    public Collection<MustDefinition> getMustConstraints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPresenceContainer() {
        return false;
    }

    @Override
    public ContainerEffectiveStatement asEffectiveStatement() {
        throw new UnsupportedOperationException();
    }
}
