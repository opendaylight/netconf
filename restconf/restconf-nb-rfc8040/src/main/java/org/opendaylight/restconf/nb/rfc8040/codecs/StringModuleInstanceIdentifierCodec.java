/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.codecs;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.util.AbstractModuleStringInstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public final class StringModuleInstanceIdentifierCodec extends AbstractModuleStringInstanceIdentifierCodec {
    private final @NonNull DataSchemaContextTree dataContextTree;
    private final @Nullable String defaultPrefix;
    private final EffectiveModelContext context;

    private StringModuleInstanceIdentifierCodec(final @Nullable String defaultPrefix,
            final EffectiveModelContext context) {
        // FIXME: what does the empty string mean, exactly?
        this.defaultPrefix = defaultPrefix;
        this.context = requireNonNull(context);
        this.dataContextTree = DataSchemaContextTree.from(context);
    }

    public StringModuleInstanceIdentifierCodec(final EffectiveModelContext context) {
        this(null, context);
    }

    public StringModuleInstanceIdentifierCodec(final EffectiveModelContext context,
            final @NonNull String defaultPrefix) {
        this(defaultPrefix.isEmpty() ? null : defaultPrefix, context);
    }

    @Override
    protected Module moduleForPrefix(final String prefix) {
        final String moduleName = prefix.isEmpty() && defaultPrefix != null ? defaultPrefix : prefix;
        return context.findModules(moduleName).stream().findFirst().orElse(null);
    }

    @Override
    public DataSchemaContextTree getDataContextTree() {
        return dataContextTree;
    }

    @Override
    protected String prefixForNamespace(final XMLNamespace namespace) {
        return this.context.findModules(namespace).stream().findFirst().map(Module::getName).orElse(null);
    }
}
