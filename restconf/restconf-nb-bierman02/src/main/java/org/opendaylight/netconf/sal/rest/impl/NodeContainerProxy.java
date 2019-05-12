/*
 * Copyright (c) 2019 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RevisionAwareXPath;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

final class NodeContainerProxy implements ContainerSchemaNode {

    private static final QName NETCONF_DATA_QNAME =
            QName.create("urn:ietf:params:xml:ns:netconf:base:1.0", "2011-06-01", "data").intern();

    private final Map<QName, DataSchemaNode> childNodes;
    private final Set<AugmentationSchemaNode> availableAugmentations;

    NodeContainerProxy(final Collection<DataSchemaNode> childNodes) {
        this.availableAugmentations = Collections.emptySet();
        this.childNodes = Preconditions.checkNotNull(asMap(childNodes), "childNodes");
    }

    private static Map<QName, DataSchemaNode> asMap(final Collection<DataSchemaNode> childNodes) {
        return Maps.uniqueIndex(childNodes, DataSchemaNode::getQName);
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        return Collections.emptySet();
    }

    @Override
    public Set<DataSchemaNode> getChildNodes() {
        return Sets.newHashSet(childNodes.values());
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        return Collections.emptySet();
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return Optional.ofNullable(childNodes.get(name));
    }

    @Override
    public Set<UsesNode> getUses() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPresenceContainer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<AugmentationSchemaNode> getAvailableAugmentations() {
        return availableAugmentations;
    }

    @Override
    public boolean isAugmenting() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAddedByUses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QName getQName() {
        return NETCONF_DATA_QNAME;
    }

    @Override
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
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        return Collections.emptyList();
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
    public Optional<RevisionAwareXPath> getWhenCondition() {
        return Optional.empty();
    }

    @Override
    public Collection<MustDefinition> getMustConstraints() {
        return Collections.emptySet();
    }
}
