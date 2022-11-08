/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.MustDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.LeafStatement;
import org.opendaylight.yangtools.yang.model.ri.type.BaseTypes;

@Deprecated(forRemoval = true, since = "4.0.0")
final class OperationsLeafSchemaNode extends AbstractOperationDataSchemaNode<LeafStatement>
        implements LeafSchemaNode, LeafEffectiveStatement {
    private final QName qname;

    OperationsLeafSchemaNode(final RpcDefinition rpc) {
        qname = rpc.getQName();
    }

    @Override
    public TypeDefinition<?> getType() {
        return BaseTypes.emptyType();
    }

    @Override
    public QName argument() {
        return qname;
    }

    @Override
    public boolean isMandatory() {
        // This leaf has to be present
        return true;
    }

    @Override
    public Collection<@NonNull MustDefinition> getMustConstraints() {
        return Set.of();
    }

    @Override
    public LeafEffectiveStatement asEffectiveStatement() {
        return this;
    }

    @Override
    public List<EffectiveStatement<?, ?>> effectiveSubstatements() {
        // FIXME: a 'type empty; mandatory true;' substatements, actually
        return List.of();
    }
}
