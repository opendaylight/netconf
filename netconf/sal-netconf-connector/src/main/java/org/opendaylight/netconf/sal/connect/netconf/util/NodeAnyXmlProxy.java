/*
 * Copyright (c) 2019 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.AnyXmlSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.RevisionAwareXPath;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;

public class NodeAnyXmlProxy implements AnyXmlSchemaNode {

    private final QName netconfDataQname;

    public NodeAnyXmlProxy(final QName netconfDataQname) {
        this.netconfDataQname = netconfDataQname;
    }

    @Override
    public boolean isConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull QName getQName() {
        return netconfDataQname;
    }

    @Override
    public @NonNull SchemaPath getPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull Status getStatus() {
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
    public boolean isAugmenting() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAddedByUses() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RevisionAwareXPath> getWhenCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMandatory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<MustDefinition> getMustConstraints() {
        // TODO Auto-generated method stub
        return null;
    }

}
