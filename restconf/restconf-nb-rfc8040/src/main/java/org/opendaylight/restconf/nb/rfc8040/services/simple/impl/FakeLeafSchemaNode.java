/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.simple.impl;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.RevisionAwareXPath;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;

/**
 * Special case only use by GET restconf/operations (since moment of old Yang
 * parser and old yang model API removal) to build and use fake leaf like child
 * in container.
 */
final class FakeLeafSchemaNode implements LeafSchemaNode {

    private final SchemaPath path;

    /**
     * Base values for fake leaf schema node.
     *
     * @param qname
     *             qname
     */
    FakeLeafSchemaNode(final QName qname) {
        this.path = FakeContainerSchemaNode.PATH.createChild(qname);
    }

    @Override
    public boolean isAugmenting() {
        return true;
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
    public QName getQName() {
        return path.getLastComponent();
    }

    @Override
    public SchemaPath getPath() {
        return path;
    }

    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Optional<String> getDescription() {
        throw new UnsupportedOperationException("Not supported operations.");
    }

    @Override
    public Optional<String> getReference() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Status getStatus() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public TypeDefinition<?> getType() {
        return BaseTypes.emptyType();
    }

    @Override
    public Optional<RevisionAwareXPath> getWhenCondition() {
        return Optional.empty();
    }

    @Override
    public boolean isMandatory() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public Collection<MustDefinition> getMustConstraints() {
        return ImmutableList.of();
    }
}
