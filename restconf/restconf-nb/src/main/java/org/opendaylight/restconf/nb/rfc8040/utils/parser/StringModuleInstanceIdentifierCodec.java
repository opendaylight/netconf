/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

final class StringModuleInstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec {
    private final @NonNull EffectiveModelContext context;

    private volatile DataSchemaContextTree dataContextTree;

    StringModuleInstanceIdentifierCodec(final @NonNull EffectiveModelContext context) {
        this.context = requireNonNull(context);
    }

    @Override
    protected Module moduleForPrefix(final String prefix) {
        return context.findModules(prefix).stream().findFirst().orElse(null);
    }

    @Override
    protected DataSchemaContextTree getDataContextTree() {
        DataSchemaContextTree local = dataContextTree;
        if (local == null) {
            dataContextTree = local = DataSchemaContextTree.from(context);
        }
        return local;
    }

    @Override
    protected String prefixForNamespace(final XMLNamespace namespace) {
        return context.findModules(namespace).stream().findFirst().map(Module::getName).orElse(null);
    }
}
