/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

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
        return Collections.emptySet();
    }

    @Override
    public Optional<DataSchemaNode> findDataChildByName(final QName name) {
        return Optional.empty();
    }
}
