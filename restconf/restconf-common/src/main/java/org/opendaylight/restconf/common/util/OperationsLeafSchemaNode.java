/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import java.util.Collection;
import java.util.Collections;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;

final class OperationsLeafSchemaNode extends AbstractOperationDataSchemaNode implements LeafSchemaNode {
    private final QName qname;

    OperationsLeafSchemaNode(final RpcDefinition rpc) {
        this.qname = rpc.getQName();
    }

    @Override
    public TypeDefinition<?> getType() {
        return BaseTypes.emptyType();
    }

    @Override
    public QName getQName() {
        return qname;
    }

    @Override
    @Deprecated
    public SchemaPath getPath() {
        return OperationsContainerSchemaNode.PATH.createChild(getQName());
    }

    @Override
    public boolean isMandatory() {
        // This leaf has to be present
        return true;
    }

    @Override
    public Collection<MustDefinition> getMustConstraints() {
        return Collections.emptySet();
    }

    @Override
    public LeafEffectiveStatement asEffectiveStatement() {
        throw new UnsupportedOperationException();
    }
}
