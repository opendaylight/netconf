/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.util.AbstractStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;

final class ApiPathInstanceIdentifierCodec extends AbstractStringInstanceIdentifierCodec {
    private final @NonNull DatabindContext databind;

    ApiPathInstanceIdentifierCodec(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    @Override
    protected DataSchemaContextTree getDataContextTree() {
        return databind.schemaTree();
    }

    @Override
    protected QNameModule moduleForPrefix(final String prefix) {
        return databind.modelContext().findModuleStatements(prefix).stream().findFirst()
            .map(ModuleEffectiveStatement::localQNameModule)
            .orElse(null);
    }

    @Override
    protected String prefixForNamespace(final XMLNamespace namespace) {
        return databind.modelContext().findModuleStatements(namespace).stream().findFirst()
            .map(module -> module.argument().getLocalName())
            .orElse(null);
    }
}
