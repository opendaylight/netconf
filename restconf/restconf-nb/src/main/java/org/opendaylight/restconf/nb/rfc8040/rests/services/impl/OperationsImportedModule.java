/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.List;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;

@Deprecated(forRemoval = true, since = "4.0.0")
final class OperationsImportedModule extends AbstractOperationsModule {
    private final Module original;

    OperationsImportedModule(final Module original) {
        this.original = requireNonNull(original);
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public QNameModule getQNameModule() {
        return original.getQNameModule();
    }

    @Override
    public String getPrefix() {
        return original.getPrefix();
    }

    @Override
    public Collection<DataSchemaNode> getChildNodes() {
        return List.of();
    }

    @Override
    public DataSchemaNode dataChildByName(final QName name) {
        return null;
    }

    @Override
    public List<EffectiveStatement<?, ?>> effectiveSubstatements() {
        return List.of();
    }
}
