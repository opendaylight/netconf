/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.impl;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.ConstraintDefinition;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

/**
 * Special case only use by GET restconf/operations (since moment of old Yang
 * parser and old yang model API removal) to build and use fake container for
 * module.
 */
class FakeContainerSchemaNode implements ContainerSchemaNode {
    static final SchemaPath PATH =
            SchemaPath.create(true, QName.create(FakeRestconfModule.QNAME, "operations").intern());

    private final Collection<DataSchemaNode> children;

    FakeContainerSchemaNode(final Collection<LeafSchemaNode> children) {
        this.children = ImmutableList.copyOf(children);
    }

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return this.children;
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public DataSchemaNode getDataChildByName(final QName name) {
        for (final DataSchemaNode node : this.children) {
            if (node.getQName().equals(name)) {
                return node;
            }
        }
        throw new RestconfDocumentedException(name + " is not in child of " + PATH.getLastComponent());
    }

    @Override
    public Set<UsesNode> getUses() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Set<AugmentationSchema> getAvailableAugmentations() {
        return new HashSet<>();
    }

    @Override
    public boolean isAugmenting() {
        return false;
    }

    @Override
    public boolean isAddedByUses() {
        return false;
    }

    @Override
    public boolean isConfiguration() {
        return false;
    }

    @Override
    public ConstraintDefinition getConstraints() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public QName getQName() {
        return PATH.getLastComponent();
    }

    @Override
    public SchemaPath getPath() {
        return PATH;
    }

    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public String getReference() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Status getStatus() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isPresenceContainer() {
        throw new UnsupportedOperationException("Not supported.");
    }
}
