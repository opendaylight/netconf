/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

@NonNullByDefault
public final class XmlInstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec {
    private final DataSchemaContextTree dataContextTree;
    private final EffectiveModelContext context;
    private final Module defaultModule;

    public XmlInstanceIdentifierCodec(final EffectiveModelContext context, final Module defaultModule) {
        this.context = requireNonNull(context);
        this.defaultModule = requireNonNull(defaultModule);
        this.dataContextTree = DataSchemaContextTree.from(context);
    }

    @Override
    protected @Nullable Module moduleForPrefix(final String prefix) {
        return prefix.isEmpty() ? defaultModule : context.findModules(prefix).stream().findFirst().orElse(null);
    }

    @Override
    public DataSchemaContextTree getDataContextTree() {
        return dataContextTree;
    }

    @Override
    protected @Nullable String prefixForNamespace(final XMLNamespace namespace) {
        return context.findModules(namespace).stream().findFirst().map(Module::getName).orElse(null);
    }
}
