/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import java.util.List;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.Status;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;

public class DummyType implements TypeDefinition<DummyType> {
    QName dummyQName = TestUtils.buildQName("dummy type", "simple:uri", "2012-12-17");

    @Override
    public QName getQName() {
        return dummyQName;
    }

    @Override
    public SchemaPath getPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getReference() {
        return Optional.empty();
    }

    @Override
    public Status getStatus() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<UnknownSchemaNode> getUnknownSchemaNodes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DummyType getBaseType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Optional<String> getUnits() {
        return Optional.empty();
    }

    @Override
    public Optional<? extends Object> getDefaultValue() {
        return Optional.empty();
    }
}
