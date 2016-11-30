/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.impl;

import java.util.ArrayList;
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
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.api.UsesNode;

/**
 * Special case only use by GET restconf/operations (since moment of old Yang
 * parser and old yang model API removal) to build and use fake container for
 * module
 *
 */
class ContainerSchemaNodeImpl implements ContainerSchemaNode {

    List<DataSchemaNode> child = new ArrayList<>();
    private final QName qname = QName.create(ModuleImpl.moduleQName, "operations");
    private final SchemaPath path = SchemaPath.create(true, this.qname);

    @Override
    public Set<TypeDefinition<?>> getTypeDefinitions() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return this.child;
    }

    @Override
    public Set<GroupingDefinition> getGroupings() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public DataSchemaNode getDataChildByName(final QName name) {
        for (final DataSchemaNode node : this.child) {
            if (node.getQName().equals(name)) {
                return node;
            }
        }
        throw new RestconfDocumentedException(name + " is not in child of " + this.qname);
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
        return this.qname;
    }

    @Override
    public SchemaPath getPath() {
        return this.path;
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

    /**
     * Adding new schema node to this container
     *
     * @param fakeLeaf
     *            - fake schema leaf node
     */
    public void addNodeChild(final DataSchemaNode fakeLeaf) {
        this.child.add(fakeLeaf);
    }
}
